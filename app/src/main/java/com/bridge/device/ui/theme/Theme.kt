package com.bridge.device.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val BridgeDarkColorScheme = darkColorScheme(
    primary = Color.White,
    onPrimary = BridgeColors.background,
    surface = BridgeColors.background,
    onSurface = Color.White,
    background = BridgeColors.background,
    onBackground = Color.White,
)

@Composable
fun BridgeTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = BridgeDarkColorScheme,
        typography = Typography,
        content = content
    )
}
