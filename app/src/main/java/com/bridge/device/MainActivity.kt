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
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.core.content.edit
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bridge.device.ui.theme.BridgeTheme

class MainActivity : ComponentActivity() {

    private lateinit var dpm: DevicePolicyManager
    private lateinit var admin: ComponentName

    // Prevent repeated/early LockTask calls
    private var kioskStarted = false

    private var policiesApplied = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        dpm = getSystemService(DevicePolicyManager::class.java)
        admin = ComponentName(this, MyDeviceAdminReceiver::class.java)

        // Hard black immediately (only affects once our window exists)
        window.decorView.setBackgroundColor(Color.BLACK)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        @Suppress("DEPRECATION")
        window.statusBarColor = Color.BLACK
        @Suppress("DEPRECATION")
        window.navigationBarColor = Color.BLACK

        // ✅ Policies only (NO startLockTask here)
        applyDeviceOwnerPolicies()

        // Switch from Splash theme to real theme ASAP
        setTheme(R.style.Theme_Bridge)

        enableEdgeToEdge()

        setContent {
            BridgeTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = BRIDGE_BG
                ) {
                    BridgeApp(
                        onOpenMaps = { openMapsNavigation(activity = this@MainActivity) },
                        onOpenConnectivity = { startActivity(Intent(android.provider.Settings.ACTION_WIRELESS_SETTINGS)) },
                        onOpenBluetooth = { startActivity(Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS)) },
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
        if (!policiesApplied) {
            applyDeviceOwnerPolicies()
            policiesApplied = true
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) enterKioskWhenReady()
    }

    private fun applyDeviceOwnerPolicies() {
        if (!dpm.isDeviceOwnerApp(packageName)) return
        if (BuildConfig.DEBUG) {
            try {
                dpm.clearPackagePersistentPreferredActivities(admin, packageName)
            } catch (_: Throwable) {}
        }
    
        // Allow Bridge to be used for LockTask
        try { dpm.setLockTaskPackages(admin, arrayOf(packageName)) } catch (_: Throwable) { return }
    
        // ✅ DO NOT set persistent HOME while debugging
        if (!BuildConfig.DEBUG) {
            try {
                val filter = IntentFilter(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_HOME)
                    addCategory(Intent.CATEGORY_DEFAULT)
                }
                dpm.addPersistentPreferredActivity(
                    admin,
                    filter,
                    ComponentName(this, MainActivity::class.java)
                )
            } catch (_: Throwable) {}
        }
    
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try { dpm.setStatusBarDisabled(admin, true) } catch (_: Throwable) {}
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try { dpm.setKeyguardDisabled(admin, true) } catch (_: Throwable) {}
        }
    }

    private fun clearPersistentHomeBinding() {
        if (!dpm.isDeviceOwnerApp(packageName)) return
        try {
            dpm.clearPackagePersistentPreferredActivities(admin, packageName)
        } catch (_: Throwable) {}
    }

    private fun enterKioskWhenReady() {
        if (!dpm.isDeviceOwnerApp(packageName)) return
        if (BuildConfig.DEBUG) return  
        if (kioskStarted) return
    
        window.decorView.post { tryStartLockTaskWithRetry() }
    }

    private fun tryStartLockTaskWithRetry() {
        if (kioskStarted) return

        // Attempt now
        try {
            startLockTask()
            kioskStarted = true
            return
        } catch (_: Throwable) {}

        // Retry once shortly (boot/home transitions can be racy)
        window.decorView.postDelayed({
            if (kioskStarted) return@postDelayed
            try {
                startLockTask()
                kioskStarted = true
            } catch (_: Throwable) {}
        }, 250)
    }
}

/**
 * Home menu base items (always shown).
 * Enabled utility tool rows will be injected under "Utility".
 */
private val baseMenuItems = listOf(
    "Library",
    "Phone",
    "Messages",
    "Travel",
    "QR",
    "Auth",
    "Utility",
    "Connectivity",
    "Bluetooth",
    "Exit Bridge"
)

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
    Exit
}

private fun deviceAdminComponent(context: Context): ComponentName =
    ComponentName(context, MyDeviceAdminReceiver::class.java)


private fun tryLaunchPackage(activity: ComponentActivity, pkg: String): Boolean {
    val intent = activity.packageManager.getLaunchIntentForPackage(pkg) ?: return false
    activity.startActivity(intent)
    return true
}

private fun openDialer(activity: ComponentActivity) {
    activity.startActivity(Intent(Intent.ACTION_DIAL))
}

private fun openSms(activity: ComponentActivity) {
    val intent = Intent(Intent.ACTION_VIEW).apply {
        data = Uri.parse("sms:")
    }
    activity.startActivity(intent)
}

private fun openMapsNavigation(activity: ComponentActivity) {
    val uri = Uri.parse("google.navigation:q=Central+Station")
    val intent = Intent(Intent.ACTION_VIEW, uri).apply {
        setPackage("com.google.android.apps.maps")
    }

    if (intent.resolveActivity(activity.packageManager) != null) {
        activity.startActivity(intent)
    } else {
        activity.startActivity(Intent(Intent.ACTION_VIEW, uri))
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
        BridgeScreen.Home -> BridgeHomeStack(
            enabledUtility = enabledUtility(),
            statusText = statusText,
            onClearStatus = { statusText = null },
            onSelect = { screen ->
                statusText = null
                currentScreen = screen
            },
            onOpenConnectivity = onOpenConnectivity,
            onOpenBluetooth = onOpenBluetooth,
            onOpenPhone = onOpenPhone,
            onOpenMessages = onOpenMessages,
            onOpenEnabledUtility = { tool ->
                val ok = onTryLaunchPackage(tool.pkg)
                statusText = if (ok) null else "${tool.title} not installed"
            }
        )

        
        BridgeScreen.AuthFirstRun -> FirstRunScreen(
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

        BridgeScreen.Auth -> {
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

        BridgeScreen.AuthConfig -> ToolConfigScreen(
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

        
        BridgeScreen.UtilityFirstRun -> FirstRunScreen(
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

        BridgeScreen.Utility -> {
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

        BridgeScreen.UtilityConfig -> ToolConfigScreen(
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

        
        BridgeScreen.Travel -> TravelMenuScreen(
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

        BridgeScreen.TravelConfig -> ToolConfigScreen(
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

        else -> PlaceholderScreen(
            title = currentScreen.name,
            onBack = { currentScreen = BridgeScreen.Home }
        )
    }
}

@Composable
fun BridgeHome(
    enabledUtility: List<Tool>,
    statusText: String?,
    onClearStatus: () -> Unit,
    onSelect: (BridgeScreen) -> Unit,
    onOpenConnectivity: () -> Unit,
    onOpenBluetooth: () -> Unit,
    onOpenPhone: () -> Unit,
    onOpenMessages: () -> Unit,
    onOpenEnabledUtility: (Tool) -> Unit
) {
    val rows = buildList {
        baseMenuItems.forEach { item ->
            add(item)
            if (item == "Utility") {
                enabledUtility.forEach { add(it.title) }
            }
        }
    }
    BridgeScaffold(title = "bridge", statusText = statusText) {
        Spacer(modifier = Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(BRIDGE_TILE_SPACING)) {
            rows.forEach { label ->
                BridgeRowTile(
                    label = label,
                    onClick = {
                        onClearStatus()
                        val enabled = enabledUtility.firstOrNull { it.title == label }
                        if (enabled != null) {
                            onOpenEnabledUtility(enabled)
                            return@BridgeRowTile
                        }
                        when (label) {
                            "Library" -> onSelect(BridgeScreen.Library)
                            "Phone" -> onOpenPhone()
                            "Messages" -> onOpenMessages()
                            "Travel" -> onSelect(BridgeScreen.Travel)
                            "QR" -> onSelect(BridgeScreen.QR)
                            "Auth" -> onSelect(BridgeScreen.Auth)
                            "Utility" -> onSelect(BridgeScreen.Utility)
                            "Connectivity" -> onOpenConnectivity()
                            "Bluetooth" -> onOpenBluetooth()
                            "Exit Bridge" -> onSelect(BridgeScreen.Exit)
                        }
                    }
                )
            }
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
    var pressed by remember { mutableStateOf(false) }
    val bg = if (pressed) BRIDGE_TILE_PRESSED else BRIDGE_TILE
    val shape = RoundedCornerShape(BRIDGE_TILE_CORNER)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clip(shape)
            .background(bg)
            .clickable(onClick = onClick, onClickLabel = label)
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
fun BridgeHomeStack(
    enabledUtility: List<Tool>,
    statusText: String?,
    onClearStatus: () -> Unit,
    onSelect: (BridgeScreen) -> Unit,
    onOpenConnectivity: () -> Unit,
    onOpenBluetooth: () -> Unit,
    onOpenPhone: () -> Unit,
    onOpenMessages: () -> Unit,
    onOpenEnabledUtility: (Tool) -> Unit
) {
    val tiles: List<Pair<String, () -> Unit>> = listOf(
        "phone" to { onClearStatus(); onOpenPhone() },
        "messages" to { onClearStatus(); onOpenMessages() },
        "travel" to { onClearStatus(); onSelect(BridgeScreen.Travel) },
        "auth" to { onClearStatus(); onSelect(BridgeScreen.Auth) },
        "utility" to { onClearStatus(); onSelect(BridgeScreen.Utility) },
        "connectivity" to { onClearStatus(); onOpenConnectivity() },
        "bluetooth" to { onClearStatus(); onOpenBluetooth() },
        "library" to { onClearStatus(); onSelect(BridgeScreen.Library) },
    )

    // Subtle time. Updates every 30s.
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
                text = "bridge",
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

        val shape = RoundedCornerShape(BRIDGE_TILE_CORNER)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(BRIDGE_TILE_SPACING)
        ) {
            tiles.forEach { (label, action) ->
                BridgeHomeTile(
                    label = label.bridgeLower(),
                    shape = shape,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .heightIn(min = 56.dp),
                    onClick = action
                )
            }
        }

        // Status line, calm + small
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

        // Optional hidden "exit" tile later (do NOT show for now)
        // We'll implement a long-press gesture or admin-only action later.
    }
}

@Composable
private fun BridgeHomeTile(
    label: String,
    shape: RoundedCornerShape,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    var pressed by remember { mutableStateOf(false) }
    val bg = if (pressed) BRIDGE_TILE_PRESSED else BRIDGE_TILE

    Box(
        modifier = modifier
            .clip(shape)
            .background(bg)
            .clickable(
                onClick = onClick,
                onClickLabel = label
            )
            .padding(horizontal = 16.dp, vertical = 16.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            text = label,
            color = BRIDGE_TEXT,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
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