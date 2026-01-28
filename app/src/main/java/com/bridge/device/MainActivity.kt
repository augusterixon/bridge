package com.bridge.device

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            BridgeTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    BridgeApp()
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
    "Hotspot",
    "Auth",
    "Settings",
    "Exit Bridge"
)

enum class BridgeScreen {
    Home,
    Library,
    Phone,
    Messages,
    Maps,
    QR,
    Hotspot,
    Auth,
    Settings,
    Exit
}

@Composable
fun BridgeApp() {
    var currentScreen by remember { mutableStateOf(BridgeScreen.Home) }

    when (currentScreen) {
        BridgeScreen.Home -> BridgeHome(
            onSelect = { screen -> currentScreen = screen }
        )

        else -> PlaceholderScreen(
            title = currentScreen.name,
            onBack = { currentScreen = BridgeScreen.Home }
        )
    }
}

@Composable
fun BridgeHome(onSelect: (BridgeScreen) -> Unit) {
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
                    val screen = when (label) {
                        "Library" -> BridgeScreen.Library
                        "Phone" -> BridgeScreen.Phone
                        "Messages" -> BridgeScreen.Messages
                        "Maps" -> BridgeScreen.Maps
                        "QR" -> BridgeScreen.QR
                        "Hotspot" -> BridgeScreen.Hotspot
                        "Auth" -> BridgeScreen.Auth
                        "Settings" -> BridgeScreen.Settings
                        "Exit Bridge" -> BridgeScreen.Exit
                        else -> BridgeScreen.Home
                    }
                    onSelect(screen)
                }
            )
            Spacer(modifier = Modifier.height(14.dp))
        }
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