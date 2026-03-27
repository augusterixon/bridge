package com.bridge.device.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.unit.sp
import com.bridge.device.R

private val provider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs
)

val DmSansFontFamily = FontFamily(
    Font(GoogleFont("DM Sans"), provider, weight = FontWeight.W300),
    Font(GoogleFont("DM Sans"), provider, weight = FontWeight.W400),
    Font(GoogleFont("DM Sans"), provider, weight = FontWeight.W500),
    Font(GoogleFont("DM Sans"), provider, weight = FontWeight.W600),
)

val GeistFontFamily = FontFamily(
    Font(GoogleFont("Geist"), provider, weight = FontWeight.W400),
    Font(GoogleFont("Geist"), provider, weight = FontWeight.W500),
)

@Suppress("unused")
val OutfitFontFamily = DmSansFontFamily

val Typography = Typography(
    bodyLarge = TextStyle(
        fontFamily = DmSansFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    ),
    titleLarge = TextStyle(
        fontFamily = DmSansFontFamily,
        fontWeight = FontWeight.W300,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    ),
    labelSmall = TextStyle(
        fontFamily = DmSansFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    )
)
