package com.bridge.device

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bridge.device.ui.theme.BridgeTheme
import android.app.ActivityManager

class MainActivity : ComponentActivity() {

    private val dpm by lazy { getSystemService(DevicePolicyManager::class.java) }
    private val admin by lazy { ComponentName(this, MyDeviceAdminReceiver::class.java) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        
        if (dpm.isDeviceOwnerApp(packageName)) {
            try {
                dpm.setLockTaskPackages(admin, arrayOf(packageName))
                
                
            } catch (_: SecurityException) {
                
            }
        }

        setContent {
            BridgeTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    BridgeApp(
                        onOpenMaps = { openMapsNavigation(activity = this) },
                        onOpenConnectivity = { startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS)) },
                        onOpenBluetooth = { startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) },
                        onOpenPhone = { openDialer(this) },
                        onOpenMessages = { openSms(this) },
                        onTryLaunchPackage = { pkg -> tryLaunchPackage(this, pkg) },
                        prefsGetEnabled = { key ->
                            getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getBoolean(key, false)
                        },
                        prefsSetEnabled = { key, value ->
                            getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                                .edit()
                                .putBoolean(key, value)
                                .apply()
                        }
                    )
                }
            }
        }

        
        enableKioskIfDeviceOwner()
    }

    override fun onResume() {
        super.onResume()
        
        enableKioskIfDeviceOwner()
    }

    private fun enableKioskIfDeviceOwner() {
        val dpm = getSystemService(DevicePolicyManager::class.java)
        if (!dpm.isDeviceOwnerApp(packageName)) return
    
        val admin = ComponentName(this, MyDeviceAdminReceiver::class.java)
    
        
        dpm.setLockTaskPackages(admin, arrayOf(packageName))
    
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            dpm.setStatusBarDisabled(admin, true)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            dpm.setKeyguardDisabled(admin, true)
        }
    
        val am = getSystemService(ActivityManager::class.java)
    
        
        if (am.lockTaskModeState == ActivityManager.LOCK_TASK_MODE_NONE) {
            try {
                startLockTask()
            } catch (_: Throwable) {
                
            }
        }
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
    val intent = Intent(Intent.ACTION_MAIN).apply {
        addCategory(Intent.CATEGORY_APP_MESSAGING)
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
        BridgeScreen.Home -> BridgeHome(
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
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.Start
    ) {
        Text(
            text = "Bridge",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(modifier = Modifier.height(24.dp))

        
        val rows = buildList {
            baseMenuItems.forEach { item ->
                add(item)
                if (item == "Utility") {
                    enabledUtility.forEach { add(it.title) }
                }
            }
        }

        rows.forEachIndexed { index, label ->
            MenuRow(
                index = index + 1,
                label = label,
                onClick = {
                    onClearStatus()

                    
                    val enabled = enabledUtility.firstOrNull { it.title == label }
                    if (enabled != null) {
                        onOpenEnabledUtility(enabled)
                        return@MenuRow
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
            Spacer(modifier = Modifier.height(14.dp))
        }

        if (!statusText.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(10.dp))
            Text(text = statusText, style = MaterialTheme.typography.bodyMedium)
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
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.Start
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(modifier = Modifier.height(24.dp))

        MenuRow(index = 1, label = enableLabel, onClick = onEnable)
        Spacer(modifier = Modifier.height(14.dp))
        MenuRow(index = 2, label = "Back", onClick = onBack)
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
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.Start
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(modifier = Modifier.height(24.dp))

        var rowIndex = 1
        enabledTools.forEach { tool ->
            MenuRow(index = rowIndex++, label = tool.title, onClick = { onOpenTool(tool) })
            Spacer(modifier = Modifier.height(14.dp))
        }

        MenuRow(index = rowIndex++, label = manageLabel, onClick = onManage)
        Spacer(modifier = Modifier.height(14.dp))
        MenuRow(index = rowIndex, label = "Back", onClick = onBack)

        if (!statusText.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(24.dp))
            Text(text = statusText, style = MaterialTheme.typography.bodyMedium)
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
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.Start
    ) {
        Text(
            text = "Travel",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(modifier = Modifier.height(24.dp))

        var rowIndex = 1

        
        MenuRow(index = rowIndex++, label = "Maps", onClick = onOpenMaps)
        Spacer(modifier = Modifier.height(14.dp))

        
        enabledTravel.forEach { tool ->
            MenuRow(index = rowIndex++, label = tool.title, onClick = { onOpenTool(tool) })
            Spacer(modifier = Modifier.height(14.dp))
        }

        
        MenuRow(index = rowIndex++, label = "Manage travel apps", onClick = onManage)
        Spacer(modifier = Modifier.height(14.dp))

        MenuRow(index = rowIndex, label = "Back", onClick = onBack)

        if (!statusText.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(24.dp))
            Text(text = statusText, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun ToggleRow(
    index: Int,
    label: String,
    checked: Boolean,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = index.toString().padStart(2, '0'),
            style = MaterialTheme.typography.bodyMedium
        )

        Spacer(modifier = Modifier.width(14.dp))

        Text(
            text = label,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.weight(1f)
        )

        Checkbox(
            checked = checked,
            onCheckedChange = { onToggle() }
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
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.Start
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(modifier = Modifier.height(12.dp))
        Text(text = note, style = MaterialTheme.typography.bodyMedium)

        Spacer(modifier = Modifier.height(24.dp))

        tools.forEachIndexed { index, tool ->
            var enabled by remember(tool.key) { mutableStateOf(prefsGetEnabled(tool.key)) }

            ToggleRow(
                index = index + 1,
                label = tool.title,
                checked = enabled,
                onToggle = {
                    enabled = !enabled
                    prefsSetEnabled(tool.key, enabled)
                }
            )

            Spacer(modifier = Modifier.height(14.dp))
        }

        Spacer(modifier = Modifier.height(10.dp))
        MenuRow(index = tools.size + 1, label = "Back", onClick = onBack)
    }
}

@Composable
private fun MenuRow(index: Int, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = index.toString().padStart(2, '0'),
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(modifier = Modifier.width(14.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.titleLarge
        )
    }
}

@Composable
fun PlaceholderScreen(title: String, onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "Coming soon",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.clickable { onBack() }
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Tap to return",
            style = MaterialTheme.typography.bodyMedium
        )
    }
}