package com.mucephei.app.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.sp

/**
 * System typography using system default font family across the app.
 * Configured with TextDirection.Content for robust Bi-directional (Bidi) LTR and RTL language rendering.
 */
val SystemFontFamily = FontFamily.Default

val HandwritingTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = SystemFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        lineBreak = LineBreak.Heading,
        textDirection = TextDirection.Content
    ),
    displayMedium = TextStyle(
        fontFamily = SystemFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 30.sp,
        lineBreak = LineBreak.Heading,
        textDirection = TextDirection.Content
    ),
    headlineLarge = TextStyle(
        fontFamily = SystemFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        lineBreak = LineBreak.Heading,
        textDirection = TextDirection.Content
    ),
    headlineMedium = TextStyle(
        fontFamily = SystemFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 19.sp,
        lineHeight = 25.sp,
        textDirection = TextDirection.Content
    ),
    titleLarge = TextStyle(
        fontFamily = SystemFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        textDirection = TextDirection.Content
    ),
    titleMedium = TextStyle(
        fontFamily = SystemFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        textDirection = TextDirection.Content
    ),
    bodyLarge = TextStyle(
        fontFamily = SystemFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        textDirection = TextDirection.Content
    ),
    bodyMedium = TextStyle(
        fontFamily = SystemFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        textDirection = TextDirection.Content
    ),
    labelLarge = TextStyle(
        fontFamily = SystemFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 18.sp,
        textDirection = TextDirection.Content
    ),
    labelMedium = TextStyle(
        fontFamily = SystemFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        textDirection = TextDirection.Content
    )
)
