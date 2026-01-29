package com.bridge.device

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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

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
                            getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                                .putBoolean(key, value)
                                .apply()
                        }
                    )
                }
            }
        }
    }
}

private val menuItems = listOf(
    "Library",
    "Phone",
    "Messages",
    "Maps",
    "QR",
    "Auth",
    "Connectivity",
    "Bluetooth",
    "Exit Bridge"
)

/**
 * Auth tools we support (curated, utilitarian).
 * Package names are best-effort defaults; adjust if needed after checking installed packages.
 */
private const val PKG_BANKID = "com.bankid.bus"
private const val PKG_MS_AUTH = "com.azure.authenticator"
private const val PKG_GOOGLE_AUTH = "com.google.android.apps.authenticator2"
private const val PKG_OKTA_VERIFY = "com.okta.android.auth"
private const val PKG_DUO = "com.duosecurity.duomobile"
private const val PKG_AUTHY = "com.authy.authy"

// prefs
private const val PREFS_NAME = "bridge_prefs"
private const val KEY_ENABLE_BANKID = "enable_bankid"
private const val KEY_ENABLE_MS_AUTH = "enable_ms_auth"
private const val KEY_ENABLE_GOOGLE_AUTH = "enable_google_auth"
private const val KEY_ENABLE_OKTA = "enable_okta"
private const val KEY_ENABLE_DUO = "enable_duo"
private const val KEY_ENABLE_AUTHY = "enable_authy"

data class AuthTool(
    val title: String,
    val pkg: String,
    val key: String
)

private val authTools = listOf(
    AuthTool("BankID", PKG_BANKID, KEY_ENABLE_BANKID),
    AuthTool("Microsoft Authenticator", PKG_MS_AUTH, KEY_ENABLE_MS_AUTH),
    AuthTool("Google Authenticator", PKG_GOOGLE_AUTH, KEY_ENABLE_GOOGLE_AUTH),
    AuthTool("Okta Verify", PKG_OKTA_VERIFY, KEY_ENABLE_OKTA),
    AuthTool("Duo Mobile", PKG_DUO, KEY_ENABLE_DUO),
    AuthTool("Authy", PKG_AUTHY, KEY_ENABLE_AUTHY)
)

enum class BridgeScreen {
    Home,
    Library,
    Phone,
    Messages,
    Maps,
    QR,
    Auth,
    AuthFirstRun,
    AuthConfig,
    Bluetooth,
    Connectivity,
    Exit
}

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
    var authStatus by remember { mutableStateOf<String?>(null) }

    fun anyAuthEnabled(): Boolean =
        authTools.any { tool -> prefsGetEnabled(tool.key) }

    when (currentScreen) {
        BridgeScreen.Home -> BridgeHome(
            onSelect = { screen -> currentScreen = screen },
            onOpenMaps = onOpenMaps,
            onOpenConnectivity = onOpenConnectivity,
            onOpenBluetooth = onOpenBluetooth,
            onOpenPhone = onOpenPhone,
            onOpenMessages = onOpenMessages
        )

        BridgeScreen.AuthFirstRun -> AuthFirstRunScreen(
            onEnable = { currentScreen = BridgeScreen.AuthConfig },
            onBack = { currentScreen = BridgeScreen.Home }
        )

        BridgeScreen.Auth -> {
            if (!anyAuthEnabled()) {
                currentScreen = BridgeScreen.AuthFirstRun
            } else {
                AuthMenuScreen(
                    statusText = authStatus,
                    enabledTools = authTools.filter { tool -> prefsGetEnabled(tool.key) },
                    onBack = {
                        authStatus = null
                        currentScreen = BridgeScreen.Home
                    },
                    onOpenTool = { tool ->
                        val ok = onTryLaunchPackage(tool.pkg)
                        authStatus = if (ok) null else "${tool.title} not installed"
                    },
                    onManage = {
                        authStatus = null
                        currentScreen = BridgeScreen.AuthConfig
                    }
                )
            }
        }


        BridgeScreen.AuthConfig -> AuthConfigScreen(
            onBack = { currentScreen = BridgeScreen.Auth },
            prefsGetEnabled = prefsGetEnabled,
            prefsSetEnabled = prefsSetEnabled,
            note = "Toggle which auth tools appear inside Bridge."
        )

        else -> PlaceholderScreen(
            title = currentScreen.name,
            onBack = { currentScreen = BridgeScreen.Home }
        )
    }
}

@Composable
fun BridgeHome(
    onSelect: (BridgeScreen) -> Unit,
    onOpenMaps: () -> Unit,
    onOpenConnectivity: () -> Unit,
    onOpenBluetooth: () -> Unit,
    onOpenPhone: () -> Unit,
    onOpenMessages: () -> Unit
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

        menuItems.forEachIndexed { index, label ->
            MenuRow(
                index = index + 1,
                label = label,
                onClick = {
                    when (label) {
                        "Maps" -> onOpenMaps()
                        "Connectivity" -> onOpenConnectivity()
                        "Bluetooth" -> onOpenBluetooth()
                        "Phone" -> onOpenPhone()
                        "Messages" -> onOpenMessages()
                        "Auth" -> onSelect(BridgeScreen.Auth)
                        else -> {
                            val screen = when (label) {
                                "Library" -> BridgeScreen.Library
                                "QR" -> BridgeScreen.QR
                                "Exit Bridge" -> BridgeScreen.Exit
                                else -> BridgeScreen.Home
                            }
                            onSelect(screen)
                        }
                    }
                }
            )
            Spacer(modifier = Modifier.height(14.dp))
        }
    }
}

@Composable
fun AuthFirstRunScreen(
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
            text = "Auth",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(modifier = Modifier.height(24.dp))
        MenuRow(index = 1, label = "Enable auth apps", onClick = onEnable)
        Spacer(modifier = Modifier.height(14.dp))
        MenuRow(index = 2, label = "Back", onClick = onBack)
    }
}

@Composable
fun AuthMenuScreen(
    statusText: String?,
    enabledTools: List<AuthTool>,
    onBack: () -> Unit,
    onOpenTool: (AuthTool) -> Unit,
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
            text = "Auth",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(modifier = Modifier.height(24.dp))

        var rowIndex = 1
        enabledTools.forEach { tool ->
            MenuRow(index = rowIndex++, label = tool.title, onClick = { onOpenTool(tool) })
            Spacer(modifier = Modifier.height(14.dp))
        }

        MenuRow(index = rowIndex++, label = "Manage auth apps", onClick = onManage)
        Spacer(modifier = Modifier.height(14.dp))
        MenuRow(index = rowIndex, label = "Back", onClick = onBack)

        if (!statusText.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(24.dp))
            Text(text = statusText, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun AuthMoreScreen(
    statusText: String?,
    enabledMore: List<AuthTool>,
    onBack: () -> Unit,
    onOpenTool: (AuthTool) -> Unit,
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
            text = "More Auth",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(modifier = Modifier.height(24.dp))

        var rowIndex = 1
        if (enabledMore.isEmpty()) {
            Text(
                text = "No additional auth tools enabled.",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(18.dp))
        } else {
            enabledMore.forEach { tool ->
                MenuRow(index = rowIndex++, label = tool.title, onClick = { onOpenTool(tool) })
                Spacer(modifier = Modifier.height(14.dp))
            }
        }

        MenuRow(index = rowIndex++, label = "Manage auth apps", onClick = onManage)
        Spacer(modifier = Modifier.height(14.dp))

        MenuRow(index = rowIndex, label = "Back", onClick = onBack)

        if (!statusText.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(24.dp))
            Text(text = statusText, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun AuthToggleRow(
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
fun AuthConfigScreen(
    onBack: () -> Unit,
    prefsGetEnabled: (String) -> Boolean,
    prefsSetEnabled: (String, Boolean) -> Unit,
    note: String
) {
    val allTools = authTools

    // ✅ Key fix: drive UI from a single state map, not per-row remember() state.
    val state = remember {
        mutableStateMapOf<String, Boolean>().apply {
            allTools.forEach { put(it.key, prefsGetEnabled(it.key)) }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.Start
    ) {
        Text(
            text = "Enable Auth Apps",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(modifier = Modifier.height(12.dp))
        Text(text = note, style = MaterialTheme.typography.bodyMedium)

        Spacer(modifier = Modifier.height(24.dp))

        allTools.forEachIndexed { index, tool ->
            val enabled = state[tool.key] == true

            AuthToggleRow(
                index = index + 1,
                label = tool.title,
                checked = enabled,
                onToggle = {
                    val newValue = !enabled
                    state[tool.key] = newValue
                    prefsSetEnabled(tool.key, newValue)
                }
            )

            Spacer(modifier = Modifier.height(14.dp))
        }

        Spacer(modifier = Modifier.height(10.dp))
        MenuRow(index = allTools.size + 1, label = "Back", onClick = onBack)
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