package com.smartprint.direct.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Authentic HP Brand Colors
val HpBlue = Color(0xFF0096D6)
val HpDarkBlue = Color(0xFF0073A8)
val HpDeepNavy = Color(0xFF0A2240)
val HpLightBlueBg = Color(0xFFF0F8FC)
val HpCardBg = Color(0xFFFFFFFF)
val HpSurfaceBg = Color(0xFFF7F9FA)

val InkBlack = Color(0xFF1E1E1E)
val InkCyan = Color(0xFF00AEEF)
val InkMagenta = Color(0xFFEC008C)
val InkYellow = Color(0xFFFFD400)

val StatusGreen = Color(0xFF10B981)
val StatusAmber = Color(0xFFF59E0B)
val StatusRed = Color(0xFFEF4444)

val Typography = Typography(
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.15.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.25.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    )
)
