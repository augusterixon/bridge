package com.bridge.device

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import android.app.ActivityManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.core.content.edit
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview as CameraPreview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.bridge.device.ui.theme.BridgeColors
import com.bridge.device.ui.theme.BridgeTheme
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage

class MainActivity : ComponentActivity() {

    private lateinit var dpm: DevicePolicyManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        dpm = getSystemService(DevicePolicyManager::class.java)

        // Hard black immediately (only affects once our window exists)
        window.decorView.setBackgroundColor(Color.BLACK)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        @Suppress("DEPRECATION")
        window.statusBarColor = Color.BLACK
        @Suppress("DEPRECATION")
        window.navigationBarColor = Color.BLACK

        MyDeviceAdminReceiver.enforceDeviceOwnerPolicies(this)

        // Switch from Splash theme to real theme ASAP
        setTheme(R.style.Theme_Bridge)

        enableEdgeToEdge()
        applyImmersiveMode()

        setContent {
            BridgeTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = BridgeColors.background
                ) {
                    BridgeApp(
                        onOpenMaps = { openMapsNavigation(activity = this@MainActivity) },
                        onOpenConnectivity = {
                            try {
                                startActivity(Intent(android.provider.Settings.ACTION_WIRELESS_SETTINGS))
                            } catch (e: Exception) {
                                Log.w("BridgeApp", "Failed to launch: $e")
                            }
                        },
                        onOpenBluetooth = {
                            try {
                                startActivity(Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS))
                            } catch (e: Exception) {
                                Log.w("BridgeApp", "Failed to launch: $e")
                            }
                        },
                        onOpenPhone = { openDialer(this@MainActivity) },
                        onOpenMessages = { openSms(this@MainActivity) },
                        onTryLaunchPackage = { pkg -> tryLaunchPackage(this@MainActivity, pkg) },
                        prefsGetEnabled = { key ->
                            getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getBoolean(key, false)
                        },
                        prefsSetEnabled = { key, value ->
                            getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit {
                                putBoolean(key, value)
                            }
                        }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        MyDeviceAdminReceiver.enforceDeviceOwnerPolicies(this)
        applyImmersiveMode()
        enterKioskWhenReady()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            // Re-apply immersive mode every time the window regains focus.
            // This is critical after returning from external apps (WhatsApp,
            // Maps, etc.) which may have caused the system UI to reappear.
            applyImmersiveMode()
            enterKioskWhenReady()
        }
    }

    /**
     * Hides status bar + navigation bar and sets transient-on-swipe behavior.
     * Called on every resume and focus change to ensure the system UI never
     * persists after returning from external apps.
     *
     * Uses WindowInsetsControllerCompat for backwards compatibility (minSdk 29).
     * LockTask mode handles the actual gesture blocking at the system level;
     * this just ensures the bars stay hidden visually.
     */
    private fun applyImmersiveMode() {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.hide(
            WindowInsetsCompat.Type.statusBars() or
                WindowInsetsCompat.Type.navigationBars()
        )
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    private fun enterKioskWhenReady() {
        if (!dpm.isDeviceOwnerApp(packageName)) return
        if (BuildConfig.DEBUG) return
        if (isInLockTaskMode()) return
    
        window.decorView.post { tryStartLockTaskWithRetry() }
    }

    private fun tryStartLockTaskWithRetry() {
        if (isInLockTaskMode()) return

        // Attempt now
        try {
            startLockTask()
            return
        } catch (_: Throwable) {}

        // Retry once shortly (boot/home transitions can be racy)
        window.decorView.postDelayed({
            if (isInLockTaskMode()) return@postDelayed
            try {
                startLockTask()
            } catch (_: Throwable) {}
        }, 250)
    }

    private fun isInLockTaskMode(): Boolean {
        val am = getSystemService(ActivityManager::class.java) ?: return false
        return am.lockTaskModeState != ActivityManager.LOCK_TASK_MODE_NONE
    }
}

/**
 * Auth tools we support (curated, utilitarian).
 */
private const val PKG_BANKID = "com.bankid.bus"
private const val PKG_MS_AUTH = "com.azure.authenticator"
private const val PKG_GOOGLE_AUTH = "com.google.android.apps.authenticator2"
private const val PKG_OKTA_VERIFY = "com.okta.android.auth"
private const val PKG_DUO = "com.duosecurity.duomobile"
private const val PKG_AUTHY = "com.authy.authy"

/**
 * Utility apps (opt-in).
 */
private const val PKG_WHATSAPP = "com.whatsapp"
private const val PKG_SPOTIFY = "com.spotify.music"
private const val PKG_UBER = "com.ubercab"

/**
 * Travel apps (opt-in). (Maps is NOT an app toggle; it’s a built-in action in Travel.)
 * Keep these if you want; otherwise you can remove the whole travelTools list.
 */
private const val PKG_GRAB = "com.grabtaxi.passenger"
private const val PKG_BOLT = "ee.mtakso.client"

/** prefs */
private const val PREFS_NAME = "bridge_prefs"


private const val KEY_ENABLE_BANKID = "enable_bankid"
private const val KEY_ENABLE_MS_AUTH = "enable_ms_auth"
private const val KEY_ENABLE_GOOGLE_AUTH = "enable_google_auth"
private const val KEY_ENABLE_OKTA = "enable_okta"
private const val KEY_ENABLE_DUO = "enable_duo"
private const val KEY_ENABLE_AUTHY = "enable_authy"


private const val KEY_ENABLE_WHATSAPP = "enable_whatsapp"
private const val KEY_ENABLE_SPOTIFY = "enable_spotify"
private const val KEY_ENABLE_UBER = "enable_uber"


private const val KEY_ENABLE_GRAB = "enable_grab"
private const val KEY_ENABLE_BOLT = "enable_bolt"

enum class ToolCategory { Auth, Utility, Travel }

data class Tool(
    val title: String,
    val pkg: String,
    val key: String,
    val category: ToolCategory
)

private val authTools = listOf(
    Tool("BankID", PKG_BANKID, KEY_ENABLE_BANKID, ToolCategory.Auth),
    Tool("Microsoft Authenticator", PKG_MS_AUTH, KEY_ENABLE_MS_AUTH, ToolCategory.Auth),
    Tool("Google Authenticator", PKG_GOOGLE_AUTH, KEY_ENABLE_GOOGLE_AUTH, ToolCategory.Auth),
    Tool("Okta Verify", PKG_OKTA_VERIFY, KEY_ENABLE_OKTA, ToolCategory.Auth),
    Tool("Duo Mobile", PKG_DUO, KEY_ENABLE_DUO, ToolCategory.Auth),
    Tool("Authy", PKG_AUTHY, KEY_ENABLE_AUTHY, ToolCategory.Auth)
)

private val utilityTools = listOf(
    Tool("WhatsApp", PKG_WHATSAPP, KEY_ENABLE_WHATSAPP, ToolCategory.Utility),
    Tool("Spotify", PKG_SPOTIFY, KEY_ENABLE_SPOTIFY, ToolCategory.Utility),
    Tool("Uber", PKG_UBER, KEY_ENABLE_UBER, ToolCategory.Utility)
)

private val travelTools = listOf(
    Tool("Grab", PKG_GRAB, KEY_ENABLE_GRAB, ToolCategory.Travel),
    Tool("Bolt", PKG_BOLT, KEY_ENABLE_BOLT, ToolCategory.Travel)
)

enum class BridgeScreen {
    Home,
    Library,
    QR,

    
    Auth,
    AuthFirstRun,
    AuthConfig,

    
    Utility,
    UtilityFirstRun,
    UtilityConfig,

    
    Travel,
    TravelConfig,

    Connectivity,
    Bluetooth,
    Settings
}

private fun deviceAdminComponent(context: Context): ComponentName =
    ComponentName(context, MyDeviceAdminReceiver::class.java)


// TODO: When Bridge launches external apps (WhatsApp, Maps, Spotify, etc.),
// the status bar and navigation bar may reappear inside those apps. This is
// a known limitation of LockTask mode — the immersive mode flags are per-window
// and external apps control their own system UI visibility. Bridge re-applies
// immersive mode in onWindowFocusChanged when the user returns, so the bars
// disappear again immediately. A future fix could use an AccessibilityService
// to force immersive mode system-wide, but that requires additional provisioning.
private fun tryLaunchPackage(activity: ComponentActivity, pkg: String): Boolean {
    val intent = activity.packageManager.getLaunchIntentForPackage(pkg) ?: return false
    activity.startActivity(intent)
    return true
}

private fun openDialer(activity: ComponentActivity) {
    try {
        activity.startActivity(Intent(Intent.ACTION_DIAL))
    } catch (e: Exception) {
        Log.w("BridgeApp", "Failed to launch: $e")
    }
}

private fun openSms(activity: ComponentActivity) {
    try {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("sms:")
        }
        activity.startActivity(intent)
    } catch (e: Exception) {
        Log.w("BridgeApp", "Failed to launch: $e")
    }
}

private fun openMapsNavigation(activity: ComponentActivity) {
    val uri = Uri.parse("google.navigation:q=Central+Station")
    val intent = Intent(Intent.ACTION_VIEW, uri).apply {
        setPackage("com.google.android.apps.maps")
    }

    try {
        if (intent.resolveActivity(activity.packageManager) != null) {
            activity.startActivity(intent)
        } else {
            activity.startActivity(Intent(Intent.ACTION_VIEW, uri))
        }
    } catch (e: Exception) {
        Log.w("BridgeApp", "Failed to launch: $e")
    }
}

@Composable
fun BridgeApp(
    onOpenMaps: () -> Unit,
    onOpenConnectivity: () -> Unit,
    onOpenBluetooth: () -> Unit,
    onOpenPhone: () -> Unit,
    onOpenMessages: () -> Unit,
    onTryLaunchPackage: (String) -> Boolean,
    prefsGetEnabled: (String) -> Boolean,
    prefsSetEnabled: (String, Boolean) -> Unit
) {
    var currentScreen by remember { mutableStateOf(BridgeScreen.Home) }
    var statusText by remember { mutableStateOf<String?>(null) }

    fun enabledAuth(): List<Tool> = authTools.filter { prefsGetEnabled(it.key) }
    fun enabledUtility(): List<Tool> = utilityTools.filter { prefsGetEnabled(it.key) }
    fun enabledTravel(): List<Tool> = travelTools.filter { prefsGetEnabled(it.key) }

    fun anyAuthEnabled(): Boolean = enabledAuth().isNotEmpty()
    fun anyUtilityEnabled(): Boolean = enabledUtility().isNotEmpty()

    when (currentScreen) {
        BridgeScreen.Home -> {
            BackHandler(enabled = true) { }
            BridgeHomeScreen(
                onNavigate = { screen ->
                    statusText = null
                    currentScreen = screen
                }
            )
        }

        
        BridgeScreen.AuthFirstRun -> {
            BackHandler {
                statusText = null
                currentScreen = BridgeScreen.Home
            }
            FirstRunScreen(
                title = "Auth",
                enableLabel = "Enable auth apps",
                onEnable = {
                    statusText = null
                    currentScreen = BridgeScreen.AuthConfig
                },
                onBack = {
                    statusText = null
                    currentScreen = BridgeScreen.Home
                }
            )
        }

        BridgeScreen.Auth -> {
            BackHandler {
                statusText = null
                currentScreen = BridgeScreen.Home
            }
            if (!anyAuthEnabled()) {
                currentScreen = BridgeScreen.AuthFirstRun
            } else {
                ToolMenuScreen(
                    title = "Auth",
                    enabledTools = enabledAuth(),
                    statusText = statusText,
                    manageLabel = "Manage auth apps",
                    onBack = {
                        statusText = null
                        currentScreen = BridgeScreen.Home
                    },
                    onManage = {
                        statusText = null
                        currentScreen = BridgeScreen.AuthConfig
                    },
                    onOpenTool = { tool ->
                        val ok = onTryLaunchPackage(tool.pkg)
                        statusText = if (ok) null else "${tool.title} not installed"
                    }
                )
            }
        }

        BridgeScreen.AuthConfig -> {
            BackHandler {
                statusText = null
                currentScreen = BridgeScreen.Auth
            }
            ToolConfigScreen(
                title = "Enable Auth Apps",
                note = "Toggle which auth tools appear inside Bridge.",
                tools = authTools,
                onBack = {
                    statusText = null
                    currentScreen = BridgeScreen.Auth
                },
                prefsGetEnabled = prefsGetEnabled,
                prefsSetEnabled = prefsSetEnabled
            )
        }

        
        BridgeScreen.UtilityFirstRun -> {
            BackHandler {
                statusText = null
                currentScreen = BridgeScreen.Home
            }
            FirstRunScreen(
                title = "Utility",
                enableLabel = "Enable utility apps",
                onEnable = {
                    statusText = null
                    currentScreen = BridgeScreen.UtilityConfig
                },
                onBack = {
                    statusText = null
                    currentScreen = BridgeScreen.Home
                }
            )
        }

        BridgeScreen.Utility -> {
            BackHandler {
                statusText = null
                currentScreen = BridgeScreen.Home
            }
            if (!anyUtilityEnabled()) {
                currentScreen = BridgeScreen.UtilityFirstRun
            } else {
                ToolMenuScreen(
                    title = "Utility",
                    enabledTools = enabledUtility(),
                    statusText = statusText,
                    manageLabel = "Manage utility apps",
                    onBack = {
                        statusText = null
                        currentScreen = BridgeScreen.Home
                    },
                    onManage = {
                        statusText = null
                        currentScreen = BridgeScreen.UtilityConfig
                    },
                    onOpenTool = { tool ->
                        val ok = onTryLaunchPackage(tool.pkg)
                        statusText = if (ok) null else "${tool.title} not installed"
                    }
                )
            }
        }

        BridgeScreen.UtilityConfig -> {
            BackHandler {
                statusText = null
                currentScreen = BridgeScreen.Utility
            }
            ToolConfigScreen(
                title = "Enable Utility Apps",
                note = "Toggle which utility apps appear inside Bridge.",
                tools = utilityTools,
                onBack = {
                    statusText = null
                    currentScreen = BridgeScreen.Utility
                },
                prefsGetEnabled = prefsGetEnabled,
                prefsSetEnabled = prefsSetEnabled
            )
        }

        
        BridgeScreen.Travel -> {
            BackHandler {
                statusText = null
                currentScreen = BridgeScreen.Home
            }
            TravelMenuScreen(
                enabledTravel = enabledTravel(),
                statusText = statusText,
                onBack = {
                    statusText = null
                    currentScreen = BridgeScreen.Home
                },
                onOpenMaps = {
                    statusText = null
                    onOpenMaps()
                },
                onOpenTool = { tool ->
                    val ok = onTryLaunchPackage(tool.pkg)
                    statusText = if (ok) null else "${tool.title} not installed"
                },
                onManage = {
                    statusText = null
                    currentScreen = BridgeScreen.TravelConfig
                }
            )
        }

        BridgeScreen.TravelConfig -> {
            BackHandler {
                statusText = null
                currentScreen = BridgeScreen.Travel
            }
            ToolConfigScreen(
                title = "Enable Travel Apps",
                note = "Toggle which travel apps appear inside Bridge.",
                tools = travelTools,
                onBack = {
                    statusText = null
                    currentScreen = BridgeScreen.Travel
                },
                prefsGetEnabled = prefsGetEnabled,
                prefsSetEnabled = prefsSetEnabled
            )
        }

        BridgeScreen.QR -> {
            BackHandler {
                statusText = null
                currentScreen = BridgeScreen.Home
            }
            QRScannerScreen(
                onBack = {
                    statusText = null
                    currentScreen = BridgeScreen.Home
                }
            )
        }

        BridgeScreen.Settings -> {
            BackHandler {
                statusText = null
                currentScreen = BridgeScreen.Home
            }
            BridgeSettingsScreen(
                onBack = {
                    statusText = null
                    currentScreen = BridgeScreen.Home
                }
            )
        }

        else -> {
            BackHandler { currentScreen = BridgeScreen.Home }
            PlaceholderScreen(
                title = currentScreen.name,
                onBack = { currentScreen = BridgeScreen.Home }
            )
        }
    }
}

private val BRIDGE_BG = ComposeColor(0xFF121314)      // dark gray
private val BRIDGE_TILE = ComposeColor(0xFF1A1C1E)    // slightly lighter tile
private val BRIDGE_TILE_PRESSED = ComposeColor(0xFF202327)
private val BRIDGE_TEXT = ComposeColor(0xFFE6E7E8)
private val BRIDGE_MUTED = ComposeColor(0xFF9EA3A8)

// Stack layout: set to 0.dp for square tiles
private val BRIDGE_TILE_CORNER = 10.dp
private val BRIDGE_TILE_SPACING = 10.dp

private val timeFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

private fun String.bridgeLower(): String = this.lowercase()

@Composable
fun BridgeScaffold(
    title: String,
    statusText: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    var nowText by remember { mutableStateOf(LocalTime.now().format(timeFmt)) }
    LaunchedEffect(Unit) {
        while (true) {
            nowText = LocalTime.now().format(timeFmt)
            kotlinx.coroutines.delay(30_000)
        }
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BRIDGE_BG)
            .padding(horizontal = 18.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.Start
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title.bridgeLower(),
                color = BRIDGE_TEXT,
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = nowText,
                color = BRIDGE_MUTED,
                fontSize = 14.sp,
                fontWeight = FontWeight.Normal
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        content()
        if (!statusText.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = statusText,
                color = BRIDGE_MUTED,
                fontSize = 13.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** Reusable full-width row tile for list-style screens (min 64dp, rounded, Bridge colors). */
@Composable
fun BridgeRowTile(
    label: String,
    hint: String? = null,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val bg = if (pressed) BRIDGE_TILE_PRESSED else BRIDGE_TILE
    val shape = RoundedCornerShape(BRIDGE_TILE_CORNER)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clip(shape)
            .background(bg)
            .clickable(interactionSource = interactionSource, indication = null, onClickLabel = label, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = label.bridgeLower(),
                color = BRIDGE_TEXT,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!hint.isNullOrBlank()) {
                Text(
                    text = hint,
                    color = BRIDGE_MUTED,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Normal
                )
            }
        }
    }
}

@Composable
fun FirstRunScreen(
    title: String,
    enableLabel: String,
    onEnable: () -> Unit,
    onBack: () -> Unit
) {
    BridgeScaffold(title = title) {
        Spacer(modifier = Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(BRIDGE_TILE_SPACING)) {
            BridgeRowTile(label = enableLabel, onClick = onEnable)
            BridgeRowTile(label = "back", onClick = onBack)
        }
    }
}

@Composable
fun ToolMenuScreen(
    title: String,
    enabledTools: List<Tool>,
    statusText: String?,
    manageLabel: String,
    onBack: () -> Unit,
    onManage: () -> Unit,
    onOpenTool: (Tool) -> Unit
) {
    BridgeScaffold(title = title, statusText = statusText) {
        Spacer(modifier = Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(BRIDGE_TILE_SPACING)) {
            enabledTools.forEach { tool ->
                BridgeRowTile(label = tool.title, onClick = { onOpenTool(tool) })
            }
            BridgeRowTile(label = manageLabel, onClick = onManage)
            BridgeRowTile(label = "back", onClick = onBack)
        }
    }
}

@Composable
fun TravelMenuScreen(
    enabledTravel: List<Tool>,
    statusText: String?,
    onBack: () -> Unit,
    onOpenMaps: () -> Unit,
    onOpenTool: (Tool) -> Unit,
    onManage: () -> Unit
) {
    BridgeScaffold(title = "Travel", statusText = statusText) {
        Spacer(modifier = Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(BRIDGE_TILE_SPACING)) {
            BridgeRowTile(label = "maps", onClick = onOpenMaps)
            enabledTravel.forEach { tool ->
                BridgeRowTile(label = tool.title, onClick = { onOpenTool(tool) })
            }
            BridgeRowTile(label = "manage travel apps", onClick = onManage)
            BridgeRowTile(label = "back", onClick = onBack)
        }
    }
}

@Composable
private fun BridgeToggleRow(
    index: Int,
    label: String,
    checked: Boolean,
    onToggle: () -> Unit
) {
    val shape = RoundedCornerShape(BRIDGE_TILE_CORNER)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clip(shape)
            .background(BRIDGE_TILE)
            .clickable { onToggle() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = index.toString().padStart(2, '0'),
            color = BRIDGE_MUTED,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.width(14.dp))
        Text(
            text = label.bridgeLower(),
            color = BRIDGE_TEXT,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Checkbox(
            checked = checked,
            onCheckedChange = { onToggle() },
            colors = CheckboxDefaults.colors(
                checkedColor = BRIDGE_TEXT,
                uncheckedColor = BRIDGE_MUTED,
                checkmarkColor = BRIDGE_BG
            )
        )
    }
}

@Composable
fun ToolConfigScreen(
    title: String,
    note: String,
    tools: List<Tool>,
    onBack: () -> Unit,
    prefsGetEnabled: (String) -> Boolean,
    prefsSetEnabled: (String, Boolean) -> Unit
) {
    BridgeScaffold(title = title) {
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = note,
            color = BRIDGE_MUTED,
            fontSize = 13.sp,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        Column(verticalArrangement = Arrangement.spacedBy(BRIDGE_TILE_SPACING)) {
            tools.forEachIndexed { index, tool ->
                var enabled by remember(tool.key) { mutableStateOf(prefsGetEnabled(tool.key)) }
                BridgeToggleRow(
                    index = index + 1,
                    label = tool.title,
                    checked = enabled,
                    onToggle = {
                        enabled = !enabled
                        prefsSetEnabled(tool.key, enabled)
                    }
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            BridgeRowTile(label = "back", onClick = onBack)
        }
    }
}

/**
 * Bridge settings screen — provides device controls that are otherwise
 * inaccessible because swipe-down (notifications/quick settings) is blocked.
 * Currently contains a brightness slider. Wired to BridgeScreen.Settings.
 */
@Composable
fun BridgeSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val canWriteSettings = remember { android.provider.Settings.System.canWrite(context) }

    // Read the current system brightness (0-255). Falls back to 128 if unreadable.
    var brightness by remember {
        mutableIntStateOf(
            try {
                android.provider.Settings.System.getInt(
                    context.contentResolver,
                    android.provider.Settings.System.SCREEN_BRIGHTNESS
                )
            } catch (_: Exception) {
                128
            }
        )
    }

    BridgeScaffold(title = "settings") {
        Spacer(modifier = Modifier.height(8.dp))

        // -- Brightness section -----------------------------------------------

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "brightness",
                color = BRIDGE_TEXT,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "${brightness * 100 / 255}%",
                color = BRIDGE_MUTED,
                fontSize = 14.sp,
                fontWeight = FontWeight.Normal
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Slider(
            value = brightness.toFloat(),
            onValueChange = { newValue ->
                brightness = newValue.toInt()
                applyBrightness(context, newValue.toInt(), canWriteSettings)
            },
            valueRange = 1f..255f,
            colors = SliderDefaults.colors(
                thumbColor = BRIDGE_TEXT,
                activeTrackColor = BRIDGE_MUTED,
                inactiveTrackColor = BRIDGE_TILE
            )
        )

        if (!canWriteSettings) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "system brightness unavailable — using window-level",
                color = BRIDGE_MUTED,
                fontSize = 11.sp
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        BridgeRowTile(label = "back", onClick = onBack)
    }
}

/**
 * Applies brightness at both the system level (if WRITE_SETTINGS is granted)
 * and the window level (always works, immediate visual feedback).
 * Clamps to [1, 255] to prevent a completely black screen.
 */
private fun applyBrightness(context: Context, value: Int, canWriteSystem: Boolean) {
    val clamped = value.coerceIn(1, 255)

    if (canWriteSystem) {
        try {
            android.provider.Settings.System.putInt(
                context.contentResolver,
                android.provider.Settings.System.SCREEN_BRIGHTNESS_MODE,
                android.provider.Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
            )
            android.provider.Settings.System.putInt(
                context.contentResolver,
                android.provider.Settings.System.SCREEN_BRIGHTNESS,
                clamped
            )
        } catch (_: Exception) { }
    }

    // Window-level brightness — always applied for immediate feedback.
    // Only affects Bridge's own window; external apps use system brightness.
    val activity = context as? ComponentActivity
    activity?.window?.let { win ->
        val params = win.attributes
        params.screenBrightness = clamped / 255f
        win.attributes = params
    }
}

@Composable
fun PlaceholderScreen(title: String, onBack: () -> Unit) {
    BridgeScaffold(title = title) {
        Spacer(modifier = Modifier.weight(1f))
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = title.bridgeLower(),
                color = BRIDGE_TEXT,
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "coming soon",
                color = BRIDGE_MUTED,
                fontSize = 15.sp,
                modifier = Modifier.clickable { onBack() }
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "tap to return",
                color = BRIDGE_MUTED,
                fontSize = 13.sp
            )
        }
        Spacer(modifier = Modifier.weight(1f))
    }
}

@Composable
fun QRScannerScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var scannedValue by remember { mutableStateOf<String?>(null) }
    val hasCameraPermission = androidx.core.content.ContextCompat.checkSelfPermission(
        context, android.Manifest.permission.CAMERA
    ) == android.content.pm.PackageManager.PERMISSION_GRANTED

    if (!hasCameraPermission) {
        BridgeScaffold(title = "QR") {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "camera permission not available",
                color = BRIDGE_MUTED,
                fontSize = 13.sp
            )
            Spacer(modifier = Modifier.height(BRIDGE_TILE_SPACING))
            BridgeRowTile(label = "back", onClick = onBack)
        }
        return
    }

    if (scannedValue != null) {
        QRResultScreen(
            value = scannedValue!!,
            onScanAgain = { scannedValue = null },
            onBack = onBack
        )
    } else {
        BridgeScaffold(title = "QR") {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "point camera at a qr code",
                color = BRIDGE_MUTED,
                fontSize = 13.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(BRIDGE_TILE_CORNER))
            ) {
                QRCameraPreview(onBarcodeDetected = { value -> scannedValue = value })
            }
            Spacer(modifier = Modifier.height(BRIDGE_TILE_SPACING))
            BridgeRowTile(label = "back", onClick = onBack)
        }
    }
}

@OptIn(ExperimentalGetImage::class)
@Composable
private fun QRCameraPreview(onBarcodeDetected: (String) -> Unit) {
    val context = LocalContext.current
    @Suppress("DEPRECATION")
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context) }
    var boundProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }

    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            boundProvider = cameraProvider

            val preview = CameraPreview.Builder().build().apply {
                surfaceProvider = previewView.surfaceProvider
            }

            val scanner = BarcodeScanning.getClient(
                BarcodeScannerOptions.Builder()
                    .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                    .build()
            )

            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .apply {
                    setAnalyzer(
                        androidx.core.content.ContextCompat.getMainExecutor(context)
                    ) { imageProxy ->
                        val mediaImage = imageProxy.image
                        if (mediaImage != null) {
                            val inputImage = InputImage.fromMediaImage(
                                mediaImage, imageProxy.imageInfo.rotationDegrees
                            )
                            scanner.process(inputImage)
                                .addOnSuccessListener { barcodes ->
                                    barcodes.firstOrNull()?.rawValue?.let(onBarcodeDetected)
                                }
                                .addOnCompleteListener { imageProxy.close() }
                        } else {
                            imageProxy.close()
                        }
                    }
                }

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    analysis
                )
            } catch (_: Exception) { }
        }, androidx.core.content.ContextCompat.getMainExecutor(context))

        onDispose {
            boundProvider?.unbindAll()
        }
    }

    AndroidView(
        factory = { previewView },
        modifier = Modifier.fillMaxSize()
    )
}

@Composable
private fun QRResultScreen(
    value: String,
    onScanAgain: () -> Unit,
    onBack: () -> Unit
) {
    val isUrl = value.startsWith("http://") || value.startsWith("https://")

    BridgeScaffold(title = "QR Result") {
        Spacer(modifier = Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(BRIDGE_TILE_CORNER))
                .background(BRIDGE_TILE)
                .padding(16.dp)
        ) {
            Column {
                Text(
                    text = value,
                    color = BRIDGE_TEXT,
                    fontSize = 14.sp,
                    maxLines = 10,
                    overflow = TextOverflow.Ellipsis
                )
                if (isUrl) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "url detected",
                        color = BRIDGE_MUTED,
                        fontSize = 12.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(BRIDGE_TILE_SPACING))

        BridgeRowTile(label = "scan again", onClick = onScanAgain)
        BridgeRowTile(label = "back", onClick = onBack)
    }
}