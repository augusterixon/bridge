package com.bridge.device

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.LocalTaxi
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bridge.device.ui.theme.BridgeColors
import com.bridge.device.ui.theme.OutfitFontFamily
import java.time.LocalTime
import java.time.format.DateTimeFormatter

// ===========================================================================
// DATA MODEL — Types that represent the resolved home screen state
// ===========================================================================

/**
 * A single installed app discovered on the device. Used inside folders
 * to list which apps are available for the user to launch.
 */
data class InstalledApp(
    val label: String,
    val packageName: String,
    val sublabel: String,
)

/**
 * Sealed class representing a tile on the home screen.
 * Either a single app tile or a folder containing multiple apps.
 */
sealed class HomeTileState {

    /**
     * A tile that launches a single app, intent action, or internal route.
     * Priority order: internalRoute > intentAction > installedPackage.
     */
    data class AppTileState(
        val label: String,
        val sublabel: String,
        val sublabelColor: Color,
        val icon: ImageVector,
        val iconColor: Color,
        val tileBackground: Color,
        val tileBorder: Color,
        val installedPackage: String? = null,
        val intentAction: String? = null,
        val internalRoute: BridgeScreen? = null,
    ) : HomeTileState()

    /**
     * A folder tile that groups multiple installed apps under one category.
     * Tapping opens a bottom sheet listing the apps inside.
     */
    data class FolderTileState(
        val folderName: String,
        val installedApps: List<InstalledApp>,
        val folderIcon: ImageVector,
        val iconColor: Color,
        val tileBackground: Color,
        val tileBorder: Color,
    ) : HomeTileState()
}

/**
 * The fully resolved home screen layout, computed once from the device's
 * installed packages and remembered for the composable lifetime.
 */
data class HomeScreenState(
    val primaryTiles: List<HomeTileState>,
    val secondaryTiles: List<HomeTileState>,
    val mediumRows: List<HomeTileState>,
    val utilityTiles: List<HomeTileState>,
)

// ===========================================================================
// CATALOG DEFINITIONS — Every app and folder category Bridge supports
// ===========================================================================

/**
 * Definition for a standalone app that always appears as an individual tile
 * and is never grouped into a folder.
 */
private data class StandaloneAppDef(
    val label: String,
    val sublabel: String,
    val packages: List<String>,
    val icon: ImageVector,
    val iconColor: Color,
    val sublabelColor: Color,
    val tileBackground: Color = BridgeColors.tilePrimary,
    val tileBorder: Color = BridgeColors.borderDefault,
    val alwaysVisible: Boolean = false,
    val fallbackIntentAction: String? = null,
    val internalRoute: BridgeScreen? = null,
)

/**
 * Definition for a single app that can live inside a folder category.
 * Carries its own visual properties so it can render as an individual tile
 * when the folder collapses (only 1 installed app in the category).
 */
private data class FolderAppDef(
    val label: String,
    val sublabel: String,
    val packages: List<String>,
    val icon: ImageVector,
    val iconColor: Color,
    val sublabelColor: Color,
    val tileBackground: Color = BridgeColors.tilePrimary,
    val tileBorder: Color = BridgeColors.borderDefault,
)

/**
 * Definition for a folder category. Contains the folder-level visual
 * properties and the list of apps it can hold.
 */
private data class FolderDef(
    val folderName: String,
    val apps: List<FolderAppDef>,
    val folderIcon: ImageVector,
    val folderIconColor: Color,
    val folderTileBackground: Color = BridgeColors.tilePrimary,
    val folderTileBorder: Color = BridgeColors.borderDefault,
)

// ---------------------------------------------------------------------------
// Standalone app definitions
// ---------------------------------------------------------------------------

private val phoneDef = StandaloneAppDef(
    label = "Phone",
    sublabel = "Call",
    packages = listOf("com.google.android.dialer", "com.android.dialer"),
    icon = Icons.Outlined.Call,
    iconColor = Color.White,
    sublabelColor = BridgeColors.textMuted,
    alwaysVisible = true,
    fallbackIntentAction = Intent.ACTION_DIAL,
)

private val mapsDef = StandaloneAppDef(
    label = "Maps",
    sublabel = "Navigate",
    packages = listOf("com.google.android.apps.maps"),
    icon = Icons.Outlined.Public,
    iconColor = BridgeColors.mapsBlue,
    sublabelColor = BridgeColors.textMuted,
)

private val messagesDef = StandaloneAppDef(
    label = "Messages",
    sublabel = "",
    packages = listOf(
        "com.google.android.apps.messaging",
        "com.android.mms",
        "org.fossify.messages",
    ),
    icon = Icons.AutoMirrored.Outlined.Chat,
    iconColor = BridgeColors.textSecondary,
    sublabelColor = BridgeColors.textMuted,
)

private val libraryDef = StandaloneAppDef(
    label = "Library",
    sublabel = "",
    packages = emptyList(),
    icon = Icons.Outlined.FolderOpen,
    iconColor = BridgeColors.textSecondary,
    sublabelColor = BridgeColors.textMuted,
    alwaysVisible = true,
    internalRoute = BridgeScreen.Library,
)

private val qrDef = StandaloneAppDef(
    label = "QR",
    sublabel = "",
    packages = emptyList(),
    icon = Icons.Outlined.QrCode2,
    iconColor = BridgeColors.utilityIcon,
    sublabelColor = BridgeColors.utilityLabel,
    tileBackground = BridgeColors.tileUtility,
    tileBorder = BridgeColors.borderUtility,
    alwaysVisible = true,
    internalRoute = BridgeScreen.QR,
)

private val hotspotDef = StandaloneAppDef(
    label = "Hotspot",
    sublabel = "",
    packages = emptyList(),
    icon = Icons.Outlined.Wifi,
    iconColor = BridgeColors.utilityIcon,
    sublabelColor = BridgeColors.utilityLabel,
    tileBackground = BridgeColors.tileUtility,
    tileBorder = BridgeColors.borderUtility,
    alwaysVisible = true,
    fallbackIntentAction = "android.settings.TETHER_SETTINGS",
)

private val settingsDef = StandaloneAppDef(
    label = "Settings",
    sublabel = "",
    packages = emptyList(),
    icon = Icons.Outlined.Settings,
    iconColor = BridgeColors.utilityIcon,
    sublabelColor = BridgeColors.utilityLabel,
    tileBackground = BridgeColors.tileUtility,
    tileBorder = BridgeColors.borderUtility,
    alwaysVisible = true,
    internalRoute = BridgeScreen.Settings,
)

// ---------------------------------------------------------------------------
// Folder definitions — each groups related apps under a single category
// ---------------------------------------------------------------------------

private val messagingFolderDef = FolderDef(
    folderName = "Messages",
    folderIcon = Icons.AutoMirrored.Outlined.Chat,
    folderIconColor = Color(0xFF25D366),
    apps = listOf(
        FolderAppDef(
            label = "WhatsApp",
            sublabel = "Messages",
            packages = listOf("com.whatsapp", "com.whatsapp.w4b"),
            icon = Icons.AutoMirrored.Outlined.Chat,
            iconColor = BridgeColors.whatsApp,
            sublabelColor = BridgeColors.whatsApp,
            tileBackground = BridgeColors.tileWhatsApp,
            tileBorder = BridgeColors.borderWhatsApp,
        ),
        FolderAppDef(
            label = "Signal",
            sublabel = "Encrypted",
            packages = listOf("org.thoughtcrime.securesms"),
            icon = Icons.AutoMirrored.Outlined.Chat,
            iconColor = Color.White,
            sublabelColor = BridgeColors.textMuted,
        ),
        FolderAppDef(
            label = "Telegram",
            sublabel = "Messages",
            packages = listOf("org.telegram.messenger", "org.telegram.messenger.web"),
            icon = Icons.AutoMirrored.Outlined.Chat,
            iconColor = Color(0xFF0088CC),
            sublabelColor = BridgeColors.textMuted,
        ),
    ),
)

private val musicFolderDef = FolderDef(
    folderName = "Music",
    folderIcon = Icons.Outlined.PlayArrow,
    folderIconColor = Color(0xFF1DB954),
    apps = listOf(
        FolderAppDef(
            label = "Spotify",
            sublabel = "Music",
            packages = listOf("com.spotify.music"),
            icon = Icons.Outlined.PlayArrow,
            iconColor = BridgeColors.spotifyGreen,
            sublabelColor = BridgeColors.spotifyGreen,
        ),
        FolderAppDef(
            label = "SoundCloud",
            sublabel = "Music",
            packages = listOf("com.soundcloud.android"),
            icon = Icons.Outlined.PlayArrow,
            iconColor = Color(0xFFFF5500),
            sublabelColor = BridgeColors.textMuted,
        ),
    ),
)

private val travelFolderDef = FolderDef(
    folderName = "Travel",
    folderIcon = Icons.Outlined.LocalTaxi,
    folderIconColor = Color(0xFF4285F4),
    apps = listOf(
        FolderAppDef(
            label = "Uber",
            sublabel = "Ride",
            packages = listOf("com.ubercab"),
            icon = Icons.Outlined.LocalTaxi,
            iconColor = Color.White,
            sublabelColor = BridgeColors.textMuted,
        ),
        FolderAppDef(
            label = "Grab",
            sublabel = "Ride",
            packages = listOf("com.grabtaxi.passenger"),
            icon = Icons.Outlined.LocalTaxi,
            iconColor = Color.White,
            sublabelColor = BridgeColors.textMuted,
        ),
        FolderAppDef(
            label = "Bolt",
            sublabel = "Ride",
            packages = listOf("ee.mtakso.client"),
            icon = Icons.Outlined.LocalTaxi,
            iconColor = Color.White,
            sublabelColor = BridgeColors.textMuted,
        ),
    ),
)

private val authFolderDef = FolderDef(
    folderName = "Auth",
    folderIcon = Icons.Outlined.VerifiedUser,
    folderIconColor = Color(0xFFAAAAAA),
    folderTileBackground = BridgeColors.tileUtility,
    folderTileBorder = BridgeColors.borderUtility,
    apps = listOf(
        FolderAppDef(
            label = "BankID",
            sublabel = "Verify",
            packages = listOf("com.bankid.bus"),
            icon = Icons.Outlined.VerifiedUser,
            iconColor = BridgeColors.utilityIcon,
            sublabelColor = BridgeColors.utilityLabel,
            tileBackground = BridgeColors.tileUtility,
            tileBorder = BridgeColors.borderUtility,
        ),
        FolderAppDef(
            label = "Microsoft Authenticator",
            sublabel = "2FA",
            packages = listOf("com.microsoft.authenticator"),
            icon = Icons.Outlined.Smartphone,
            iconColor = BridgeColors.utilityIcon,
            sublabelColor = BridgeColors.utilityLabel,
            tileBackground = BridgeColors.tileUtility,
            tileBorder = BridgeColors.borderUtility,
        ),
        FolderAppDef(
            label = "Google Authenticator",
            sublabel = "2FA",
            packages = listOf("com.google.android.apps.authenticator2"),
            icon = Icons.Outlined.Smartphone,
            iconColor = BridgeColors.utilityIcon,
            sublabelColor = BridgeColors.utilityLabel,
            tileBackground = BridgeColors.tileUtility,
            tileBorder = BridgeColors.borderUtility,
        ),
    ),
)

// ===========================================================================
// RESOLUTION LOGIC — Scans installed packages and builds HomeScreenState
// ===========================================================================

/**
 * Checks whether a given package is installed on the device.
 * Uses the modern PackageInfoFlags API on Android 13+ and falls back
 * to the deprecated int-flags overload on older versions.
 * Requires QUERY_ALL_PACKAGES permission in the manifest (Android 11+
 * package visibility filtering would otherwise hide third-party packages).
 */
private fun isAppInstalled(context: Context, packageName: String): Boolean {
    return try {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageInfo(
                packageName,
                PackageManager.PackageInfoFlags.of(0)
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(packageName, 0)
        }
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }
}

/**
 * Resolves a standalone app definition against installed packages.
 * Returns an [HomeTileState.AppTileState] if the app is found or always-visible.
 * Returns null when the app is not installed and not always-visible.
 */
private fun resolveStandalone(
    context: Context,
    def: StandaloneAppDef,
): HomeTileState.AppTileState? {
    val installed = def.packages.firstOrNull { isAppInstalled(context, it) }
    if (installed == null && !def.alwaysVisible) return null

    return HomeTileState.AppTileState(
        label = def.label,
        sublabel = def.sublabel,
        sublabelColor = def.sublabelColor,
        icon = def.icon,
        iconColor = def.iconColor,
        tileBackground = def.tileBackground,
        tileBorder = def.tileBorder,
        installedPackage = installed,
        // Only fall back to the raw intent when no concrete package was found
        intentAction = if (installed == null) def.fallbackIntentAction else null,
        internalRoute = def.internalRoute,
    )
}

/**
 * Resolves a folder category against installed packages.
 *
 * - 0 installed apps → null (hide the folder entirely)
 * - 1 installed app  → [HomeTileState.AppTileState] (collapse to a single tile)
 * - 2+ installed apps → [HomeTileState.FolderTileState] (full folder with sheet)
 */
private fun resolveFolder(context: Context, def: FolderDef): HomeTileState? {
    val installed = def.apps.mapNotNull { appDef ->
        val pkg = appDef.packages.firstOrNull { isAppInstalled(context, it) }
        if (pkg != null) appDef to pkg else null
    }

    return when {
        installed.isEmpty() -> null

        installed.size == 1 -> {
            val (appDef, pkg) = installed.first()
            HomeTileState.AppTileState(
                label = appDef.label,
                sublabel = appDef.sublabel,
                sublabelColor = appDef.sublabelColor,
                icon = appDef.icon,
                iconColor = appDef.iconColor,
                tileBackground = appDef.tileBackground,
                tileBorder = appDef.tileBorder,
                installedPackage = pkg,
            )
        }

        else -> HomeTileState.FolderTileState(
            folderName = def.folderName,
            installedApps = installed.map { (appDef, pkg) ->
                InstalledApp(
                    label = appDef.label,
                    packageName = pkg,
                    sublabel = appDef.sublabel,
                )
            },
            folderIcon = def.folderIcon,
            iconColor = def.folderIconColor,
            tileBackground = def.folderTileBackground,
            tileBorder = def.folderTileBorder,
        )
    }
}

/**
 * Resolves a "role" tile (Messages, Music, Maps) based on the user's
 * default app preference. Always returns a tile — never null:
 *
 * - Configured + installed → branded tile with generic role label/sublabel
 * - Unconfigured or missing → "Set up in settings" prompt routing to Settings
 *
 * [folderDef] is used to look up brand colors for the configured package.
 * Pass null for roles that have no folder (e.g. Maps).
 */
private fun resolveRoleTile(
    context: Context,
    defaultPkg: String?,
    folderDef: FolderDef?,
    genericLabel: String,
    genericSublabel: String,
    genericIcon: ImageVector,
    configuredIconColor: Color = BridgeColors.textMuted,
): HomeTileState.AppTileState {
    if (!defaultPkg.isNullOrEmpty() && isAppInstalled(context, defaultPkg)) {
        val matchingDef = folderDef?.apps?.firstOrNull { appDef ->
            appDef.packages.any { it == defaultPkg }
        }
        return HomeTileState.AppTileState(
            label = genericLabel,
            sublabel = genericSublabel,
            sublabelColor = matchingDef?.sublabelColor ?: BridgeColors.textMuted,
            icon = genericIcon,
            iconColor = matchingDef?.iconColor ?: configuredIconColor,
            tileBackground = matchingDef?.tileBackground ?: BridgeColors.tilePrimary,
            tileBorder = matchingDef?.tileBorder ?: BridgeColors.borderDefault,
            installedPackage = defaultPkg,
        )
    }

    return HomeTileState.AppTileState(
        label = genericLabel,
        sublabel = "Set up in settings",
        sublabelColor = BridgeColors.textMuted,
        icon = Icons.Outlined.TouchApp,
        iconColor = BridgeColors.textMuted,
        tileBackground = BridgeColors.tilePrimary,
        tileBorder = BridgeColors.borderDefault,
        internalRoute = BridgeScreen.Settings,
    )
}

/**
 * Master resolution function. Scans the device for all supported apps,
 * resolves standalone tiles and folder categories, and assembles the
 * complete home screen layout organized by tier. Role tiles (Messages,
 * Music, Maps) are always present — either showing the configured
 * default app or an "unconfigured" setup prompt.
 */
private fun resolveHomeScreen(context: Context, defaultApps: DefaultApps): HomeScreenState {
    val phone = resolveStandalone(context, phoneDef)
    val messages = resolveStandalone(context, messagesDef)
    val library = resolveStandalone(context, libraryDef)
    val qr = resolveStandalone(context, qrDef)
    val hotspot = resolveStandalone(context, hotspotDef)
    val settings = resolveStandalone(context, settingsDef)

    val messaging = resolveRoleTile(
        context, defaultApps.messagesPackage, messagingFolderDef,
        "Messages", "Chat", Icons.AutoMirrored.Outlined.Chat
    )

    val music = resolveRoleTile(
        context, defaultApps.musicPackage, musicFolderDef,
        "Music", "Listen", Icons.Outlined.PlayArrow
    )

    val maps = resolveRoleTile(
        context, defaultApps.mapsPackage, null,
        "Maps", "Navigate", Icons.Outlined.Public,
        configuredIconColor = BridgeColors.mapsBlue
    )

    val travel = resolveFolder(context, travelFolderDef)
    val auth = resolveFolder(context, authFolderDef)

    return HomeScreenState(
        primaryTiles = listOfNotNull(phone, messaging),
        secondaryTiles = listOfNotNull(maps, music, travel),
        mediumRows = listOfNotNull(messages, library),
        utilityTiles = listOfNotNull(auth, qr, hotspot, settings),
    )
}

// ===========================================================================
// LAUNCH LOGIC — Central dispatcher for all tile actions
// ===========================================================================

/**
 * Launches the action for any tile type.
 * Routes to internal navigation, raw intent actions, package launches,
 * or the folder sheet opener depending on the tile variant.
 */
private fun launchTile(
    context: Context,
    tile: HomeTileState,
    onNavigate: (BridgeScreen) -> Unit,
    onOpenFolder: (HomeTileState.FolderTileState) -> Unit,
) {
    when (tile) {
        is HomeTileState.AppTileState -> {
            tile.internalRoute?.let { onNavigate(it); return }

            tile.intentAction?.let { action ->
                try { context.startActivity(Intent(action)) } catch (_: Exception) {}
                return
            }

            tile.installedPackage?.let { pkg ->
                val intent = context.packageManager.getLaunchIntentForPackage(pkg)
                if (intent != null) {
                    try { context.startActivity(intent) } catch (_: Exception) {}
                }
            }
        }

        is HomeTileState.FolderTileState -> onOpenFolder(tile)
    }
}

// ===========================================================================
// BridgeHomeScreen — the single public composable
// ===========================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BridgeHomeScreen(
    onNavigate: (BridgeScreen) -> Unit,
    defaultApps: DefaultApps = DefaultApps(null, null, null)
) {
    val context = LocalContext.current

    // Clock — ticks every 30 seconds for a calm, non-distracting cadence
    val timeFmt = remember { DateTimeFormatter.ofPattern("HH:mm") }
    var clockText by remember { mutableStateOf(LocalTime.now().format(timeFmt)) }
    LaunchedEffect(Unit) {
        while (true) {
            clockText = LocalTime.now().format(timeFmt)
            kotlinx.coroutines.delay(30_000)
        }
    }

    // Time-based greeting
    val greeting = remember {
        when (LocalTime.now().hour) {
            in 5..11 -> "Good morning"
            in 12..16 -> "Good afternoon"
            in 17..20 -> "Good evening"
            else -> "Good night"
        }
    }

    // Resolve which tiles belong on screen — recomputed when defaults change
    val homeState = remember(defaultApps) { resolveHomeScreen(context, defaultApps) }

    // Tracks the currently open folder sheet (null = sheet dismissed)
    var openFolder by remember { mutableStateOf<HomeTileState.FolderTileState?>(null) }

    // Shared handler that dispatches any tile tap to the correct action
    val onTileTap: (HomeTileState) -> Unit = { tile ->
        launchTile(context, tile, onNavigate) { folder -> openFolder = folder }
    }

    // ---- Main vertical layout -----------------------------------------------

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BridgeColors.background)
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 24.dp)
            .padding(top = 12.dp, bottom = 8.dp)
    ) {

        // -- Header: "BRIDGE" wordmark left, clock right ----------------------

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "BRIDGE",
                fontFamily = OutfitFontFamily,
                fontWeight = FontWeight.W500,
                fontSize = 11.sp,
                letterSpacing = 3.sp,
                color = BridgeColors.wordmark
            )
            Text(
                text = clockText,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.W400,
                fontSize = 11.sp,
                color = BridgeColors.clock
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // -- Greeting ---------------------------------------------------------

        Text(
            text = "$greeting,\nAugust.",
            fontFamily = OutfitFontFamily,
            fontWeight = FontWeight.W300,
            fontSize = 22.sp,
            lineHeight = (22 * 1.3).sp,
            letterSpacing = (-0.44).sp,
            color = BridgeColors.textPrimary
        )

        Spacer(modifier = Modifier.height(28.dp))

        // -- Primary tier (Phone + messaging folder/tile) ---------------------

        if (homeState.primaryTiles.isNotEmpty()) {
            TileGrid(homeState.primaryTiles, onTileTap)
        }

        // -- Secondary tier (Maps, Music, Travel) -----------------------------

        if (homeState.secondaryTiles.isNotEmpty()) {
            if (homeState.primaryTiles.isNotEmpty()) Spacer(modifier = Modifier.height(12.dp))
            TileGrid(homeState.secondaryTiles, onTileTap)
        }

        // -- Medium rows (Messages/SMS, Library) ------------------------------

        if (homeState.mediumRows.isNotEmpty()) {
            val hasGridAbove =
                homeState.primaryTiles.isNotEmpty() || homeState.secondaryTiles.isNotEmpty()
            if (hasGridAbove) Spacer(modifier = Modifier.height(16.dp))

            homeState.mediumRows.forEachIndexed { index, tile ->
                if (index > 0) Spacer(modifier = Modifier.height(8.dp))
                if (tile is HomeTileState.AppTileState) {
                    MediumRow(
                        label = tile.label,
                        icon = tile.icon,
                        onClick = { onTileTap(tile) }
                    )
                }
            }
        }

        // Push utility tier + home indicator to the bottom
        Spacer(modifier = Modifier.weight(1f))

        // -- Utility tier (Auth, QR, Hotspot) ---------------------------------

        if (homeState.utilityTiles.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                homeState.utilityTiles.forEach { tile ->
                    when (tile) {
                        is HomeTileState.AppTileState -> UtilityTile(
                            modifier = Modifier.weight(1f),
                            label = tile.label,
                            icon = tile.icon,
                            onClick = { onTileTap(tile) }
                        )

                        is HomeTileState.FolderTileState -> UtilityTile(
                            modifier = Modifier.weight(1f),
                            label = tile.folderName,
                            icon = tile.folderIcon,
                            onClick = { openFolder = tile }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(36.dp))

        // -- Home indicator bar -----------------------------------------------

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .width(32.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(BridgeColors.homeIndicator)
            )
        }
    }

    // -- Folder bottom sheet (appears when a folder tile is tapped) -----------

    openFolder?.let { folder ->
        AppFolderSheet(
            folder = folder,
            onDismiss = { openFolder = null },
            onLaunchApp = { packageName ->
                val intent = context.packageManager.getLaunchIntentForPackage(packageName)
                if (intent != null) {
                    try { context.startActivity(intent) } catch (_: Exception) {}
                }
                openFolder = null
            }
        )
    }
}

// ===========================================================================
// TileGrid — renders a list of HomeTileState in a 2-column grid
// ===========================================================================

/**
 * Lays out tiles in rows of 2. Dispatches to [PrimaryTile] for single-app
 * tiles and [FolderTile] for folder tiles. Balances odd rows with a spacer.
 */
@Composable
private fun TileGrid(
    tiles: List<HomeTileState>,
    onTileTap: (HomeTileState) -> Unit,
) {
    tiles.chunked(2).forEachIndexed { chunkIndex, row ->
        if (chunkIndex > 0) Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            row.forEach { tile ->
                when (tile) {
                    is HomeTileState.AppTileState -> PrimaryTile(
                        modifier = Modifier.weight(1f),
                        label = tile.label,
                        sublabel = tile.sublabel,
                        sublabelColor = tile.sublabelColor,
                        icon = tile.icon,
                        iconColor = tile.iconColor,
                        tileBackground = tile.tileBackground,
                        tileBorder = tile.tileBorder,
                        onClick = { onTileTap(tile) }
                    )

                    is HomeTileState.FolderTileState -> FolderTile(
                        modifier = Modifier.weight(1f),
                        folder = tile,
                        onClick = { onTileTap(tile) }
                    )
                }
            }
            if (row.size == 1) Spacer(modifier = Modifier.weight(1f))
        }
    }
}

// ===========================================================================
// PrimaryTile — large card used by PRIMARY and SECONDARY tiers
// ===========================================================================

/**
 * 120dp tall card with icon top-left and label+sublabel bottom-left.
 * Identical animation: spring scale to 0.96 on press, dampingRatio 0.7,
 * stiffness 400.
 */
@Composable
private fun PrimaryTile(
    modifier: Modifier = Modifier,
    label: String,
    sublabel: String,
    sublabelColor: Color,
    icon: ImageVector,
    iconColor: Color,
    tileBackground: Color,
    tileBorder: Color,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 400f),
        label = "tileScale"
    )

    Box(
        modifier = modifier
            .height(120.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(20.dp))
            .background(tileBackground)
            .border(1.dp, tileBorder, RoundedCornerShape(20.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(20.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = iconColor,
                modifier = Modifier.size(24.dp)
            )
            Column {
                Text(
                    text = label,
                    fontFamily = OutfitFontFamily,
                    fontWeight = FontWeight.W500,
                    fontSize = 15.sp,
                    color = BridgeColors.textPrimary
                )
                Text(
                    text = sublabel,
                    fontFamily = OutfitFontFamily,
                    fontWeight = FontWeight.W400,
                    fontSize = 11.sp,
                    color = sublabelColor
                )
            }
        }
    }
}

// ===========================================================================
// FolderTile — large card with 2×2 mini app icon grid (iOS-style preview)
// ===========================================================================

/**
 * Same dimensions and animations as [PrimaryTile] (120dp, 20dp corners,
 * spring scale). Shows a 2×2 grid of real app icons loaded from the
 * PackageManager at the top, and folder name + app count at the bottom.
 */
@Composable
private fun FolderTile(
    modifier: Modifier = Modifier,
    folder: HomeTileState.FolderTileState,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 400f),
        label = "folderScale"
    )

    Box(
        modifier = modifier
            .height(120.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(20.dp))
            .background(folder.tileBackground)
            .border(1.dp, folder.tileBorder, RoundedCornerShape(20.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(20.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // 2×2 grid of small app icons from PackageManager
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                folder.installedApps.take(4).chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        row.forEach { app ->
                            AppIconImage(
                                packageName = app.packageName,
                                size = 18.dp,
                                modifier = Modifier.clip(RoundedCornerShape(5.dp))
                            )
                        }
                    }
                }
            }

            // Folder label + count badge
            Column {
                Text(
                    text = folder.folderName,
                    fontFamily = OutfitFontFamily,
                    fontWeight = FontWeight.W500,
                    fontSize = 15.sp,
                    color = BridgeColors.textPrimary
                )
                Text(
                    text = "${folder.installedApps.size} apps",
                    fontFamily = OutfitFontFamily,
                    fontWeight = FontWeight.W400,
                    fontSize = 11.sp,
                    color = BridgeColors.textMuted
                )
            }
        }
    }
}

// ===========================================================================
// MediumRow — full-width compact row (Messages, Library)
// ===========================================================================

/**
 * Full-width row with icon + label left and chevron right.
 * Same spring animation as all other tiles.
 */
@Composable
private fun MediumRow(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 400f),
        label = "rowScale"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(16.dp))
            .background(BridgeColors.tilePrimary)
            .border(1.dp, BridgeColors.borderDefault, RoundedCornerShape(16.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = BridgeColors.textSecondary,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = label,
                    fontFamily = OutfitFontFamily,
                    fontWeight = FontWeight.W400,
                    fontSize = 14.sp,
                    color = BridgeColors.textSecondary
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = "Open",
                tint = BridgeColors.rowChevron,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

// ===========================================================================
// UtilityTile — small icon + label tile (Auth, QR, Hotspot)
// ===========================================================================

/**
 * Compact tile with centered icon above label. Used for the utility tier
 * at the bottom of the screen.
 */
@Composable
private fun UtilityTile(
    modifier: Modifier = Modifier,
    label: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 400f),
        label = "utilityScale"
    )

    Column(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(16.dp))
            .background(BridgeColors.tileUtility)
            .border(1.dp, BridgeColors.borderUtility, RoundedCornerShape(16.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = BridgeColors.utilityIcon,
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = label,
            fontFamily = OutfitFontFamily,
            fontWeight = FontWeight.W400,
            fontSize = 10.sp,
            color = BridgeColors.utilityLabel,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// ===========================================================================
// AppIconImage — loads a real app icon from PackageManager
// ===========================================================================

/**
 * Renders the launcher icon for [packageName] by converting the PM drawable
 * to an ImageBitmap. Falls back to a neutral placeholder box when the icon
 * cannot be loaded (e.g. app uninstalled mid-session or missing icon).
 */
@Composable
private fun AppIconImage(
    packageName: String,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    val imageBitmap = remember(packageName) {
        try {
            val drawable = context.packageManager.getApplicationIcon(packageName)
            val w = drawable.intrinsicWidth.coerceAtLeast(1)
            val h = drawable.intrinsicHeight.coerceAtLeast(1)
            val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, w, h)
            drawable.draw(canvas)
            bitmap.asImageBitmap()
        } catch (_: Exception) {
            null
        }
    }

    if (imageBitmap != null) {
        Image(
            bitmap = imageBitmap,
            contentDescription = null,
            modifier = modifier.size(size),
            contentScale = ContentScale.Crop
        )
    } else {
        Box(
            modifier = modifier
                .size(size)
                .background(BridgeColors.borderDefault, RoundedCornerShape(size / 4)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.Smartphone,
                contentDescription = null,
                tint = BridgeColors.textMuted,
                modifier = Modifier.size(size * 0.6f)
            )
        }
    }
}

// ===========================================================================
// AppFolderSheet — ModalBottomSheet listing apps inside a folder
// ===========================================================================

/**
 * Dark bottom sheet that appears when a folder tile is tapped. Lists all
 * installed apps in the folder as tappable rows with real app icons.
 * Dismisses on back gesture, scrim tap, or drag-down.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppFolderSheet(
    folder: HomeTileState.FolderTileState,
    onDismiss: () -> Unit,
    onLaunchApp: (String) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = BridgeColors.tilePrimary,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        scrimColor = Color.Black.copy(alpha = 0.5f),
        dragHandle = {
            // Custom drag handle matching Bridge's visual language
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp, bottom = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(32.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(BridgeColors.borderDefault)
                )
            }
        },
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 24.dp)
        ) {
            // Folder name as the sheet title
            Text(
                text = folder.folderName,
                fontFamily = OutfitFontFamily,
                fontWeight = FontWeight.W500,
                fontSize = 16.sp,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Each app is a tappable row: icon + name + sublabel
            folder.installedApps.forEach { app ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onLaunchApp(app.packageName) }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    AppIconImage(
                        packageName = app.packageName,
                        size = 24.dp,
                        modifier = Modifier.clip(RoundedCornerShape(6.dp))
                    )
                    Column {
                        Text(
                            text = app.label,
                            fontFamily = OutfitFontFamily,
                            fontWeight = FontWeight.W400,
                            fontSize = 15.sp,
                            color = Color.White
                        )
                        if (app.sublabel.isNotEmpty()) {
                            Text(
                                text = app.sublabel,
                                fontFamily = OutfitFontFamily,
                                fontWeight = FontWeight.W400,
                                fontSize = 11.sp,
                                color = BridgeColors.textMuted
                            )
                        }
                    }
                }
            }

            // Bottom padding so the last row doesn't sit against the edge
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
