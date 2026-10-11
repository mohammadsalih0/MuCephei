package com.mucephei.app.theme

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.palette.graphics.Palette
import com.mucephei.app.data.model.NoteTheme
import com.mucephei.app.data.model.ThemePreset

/**
 * PaletteEngine:
 * Generates serene, calm, nostalgic diary color schemes.
 * Supports preset themes, custom image-extracted palettes, and Light/Dark Glass themes.
 * Respects global `glassifyEnabled` setting: when true, applies frosted glass transparency across all themes.
 */
object PaletteEngine {

    /**
     * Extracts muted, serene swatches from Bitmaps for photo-attached notes.
     */
    fun extractNoteThemeFromBitmap(bitmap: Bitmap, isDarkMode: Boolean = false): NoteTheme {
        val palette = Palette.from(bitmap).generate()

        val dominantSwatch = palette.mutedSwatch ?: palette.dominantSwatch ?: palette.lightMutedSwatch
        val secondarySwatch = palette.lightMutedSwatch ?: palette.vibrantSwatch
        val highlightSwatch = palette.darkMutedSwatch ?: dominantSwatch

        val primaryHex = dominantSwatch?.rgb?.toHex() ?: "#7C9885"
        val secondaryHex = secondarySwatch?.rgb?.toHex() ?: "#C98A7C"
        val highlightHex = highlightSwatch?.rgb?.toHex() ?: "#D8B26E"

        val dominantColorInt = dominantSwatch?.rgb ?: AndroidColor.parseColor("#7C9885")

        val darkBgInt = blendColors(dominantColorInt, AndroidColor.parseColor("#1A1C1E"), 0.85f)
        val lightBgInt = blendColors(dominantColorInt, AndroidColor.parseColor("#FBF8F3"), 0.92f)

        val bgInt = if (isDarkMode) darkBgInt else lightBgInt
        val surfaceInt = if (isDarkMode) blendColors(dominantColorInt, AndroidColor.parseColor("#24272B"), 0.80f) else blendColors(dominantColorInt, AndroidColor.parseColor("#FFFDF9"), 0.88f)

        val textInt = calculateWcagTextOnBackground(bgInt)

        return NoteTheme(
            dominantColorsHex = listOf(primaryHex, secondaryHex, highlightHex),
            backgroundColorHex = bgInt.toHex(),
            surfaceColorHex = surfaceInt.toHex(),
            textColorHex = textInt.toHex(),
            accentColorHex = secondaryHex,
            selectedPreset = ThemePreset.CUSTOM_IMAGE
        )
    }

    /**
     * Builds a Compose ColorScheme based on the selected ThemePreset and/or custom NoteTheme.
     */
    fun buildColorScheme(
        theme: NoteTheme? = null,
        preset: ThemePreset? = null,
        isSystemDark: Boolean,
        hasWallpaper: Boolean = false,
        glassifyEnabled: Boolean = true
    ): ColorScheme {
        // If a preset is explicitly given, use it
        val effectivePreset = preset
            ?: theme?.selectedPreset
            ?: ThemePreset.SUNSET_WARMTH

        val baseScheme = when (effectivePreset) {
            ThemePreset.DEFAULT_LIGHT -> defaultLightColorScheme(glassifyEnabled)
            ThemePreset.DEFAULT_DARK -> defaultDarkColorScheme(glassifyEnabled)
            ThemePreset.AMOLED_BLACK -> amoledDarkColorScheme(glassifyEnabled)
            ThemePreset.SUNSET_WARMTH -> sunsetWarmColorScheme(glassifyEnabled)
            ThemePreset.LAVENDER_DREAM -> lavenderDreamColorScheme(glassifyEnabled)
            ThemePreset.FOREST_MIST -> forestMistColorScheme(glassifyEnabled)
            ThemePreset.OCEAN_BREEZE -> oceanBreezeColorScheme(glassifyEnabled)
            ThemePreset.LIGHT_GLASS -> lightGlassColorScheme()
            ThemePreset.DARK_GLASS, ThemePreset.SHINING_STARS_GLASS -> darkGlassColorScheme()
            ThemePreset.CUSTOM_IMAGE -> {
                if (theme != null) buildCustomColorScheme(theme, isSystemDark, glassifyEnabled)
                else if (isSystemDark) defaultDarkColorScheme(glassifyEnabled) else defaultLightColorScheme(glassifyEnabled)
            }
        }

        if (hasWallpaper && theme != null) {
            val extractedPrimary = hexToColor(theme.dominantColorsHex.firstOrNull() ?: "#7C9885")
            val extractedSecondary = hexToColor(theme.accentColorHex)
            val extractedSurface = hexToColor(theme.surfaceColorHex)

            return baseScheme.copy(
                primary = extractedPrimary,
                secondary = extractedSecondary,
                surface = if (glassifyEnabled) extractedSurface.copy(alpha = 0.85f) else extractedSurface,
                surfaceVariant = if (glassifyEnabled) extractedSurface.copy(alpha = 0.75f) else extractedSurface
            )
        }

        return baseScheme
    }

    /**
     * Builds a custom color scheme from an extracted NoteTheme.
     */
    private fun buildCustomColorScheme(theme: NoteTheme, isSystemDark: Boolean, glassify: Boolean = true): ColorScheme {
        val primary = hexToColor(theme.dominantColorsHex.firstOrNull() ?: "#7C9885")
        val secondary = hexToColor(theme.accentColorHex)
        val tertiary = hexToColor(theme.dominantColorsHex.getOrNull(2) ?: "#D8B26E")
        val bg = hexToColor(theme.backgroundColorHex)
        val surface = hexToColor(theme.surfaceColorHex)
        val onText = hexToColor(theme.textColorHex)

        val effectiveSurface = if (glassify) surface.copy(alpha = 0.88f) else surface

        return if (isSystemDark) {
            darkColorScheme(
                primary = primary,
                secondary = secondary,
                tertiary = tertiary,
                background = bg,
                surface = effectiveSurface,
                surfaceVariant = if (glassify) Color(0xCC2F343A) else Color(0xFF2F343A),
                onPrimary = Color.White,
                onSecondary = Color.White,
                onBackground = onText,
                onSurface = onText
            )
        } else {
            lightColorScheme(
                primary = primary,
                secondary = secondary,
                tertiary = tertiary,
                background = bg,
                surface = effectiveSurface,
                surfaceVariant = if (glassify) Color(0xCCF4EFE6) else Color(0xFFF4EFE6),
                onPrimary = Color.White,
                onSecondary = Color.White,
                onBackground = onText,
                onSurface = onText
            )
        }
    }

    // ── Preset Color Schemes ──────────────────────────────────

    fun defaultLightColorScheme(glassify: Boolean = true): ColorScheme = lightColorScheme(
        primary = Color(0xFF7C9885),        // Serene Muted Sage Green
        secondary = Color(0xFFC98A7C),      // Faded Dusty Terracotta/Rose
        tertiary = Color(0xFF6B8EA4),       // Soft Muted Slate Blue
        background = Color(0xFFFBF8F3),     // Warm Linen Diary Paper
        surface = if (glassify) Color(0xD9FFFDF9) else Color(0xFFFFFDF9),        // Gentle Cream Card Surface
        surfaceVariant = if (glassify) Color(0xCCE8ECEA) else Color(0xFFF4EFE6), // Soft Paper Tint
        onBackground = Color(0xFF36322F),   // Warm Sepia Charcoal Ink
        onSurface = Color(0xFF36322F),
        outline = Color(0xFFE6DFD5),
        onPrimary = Color.White,
        onSecondary = Color.White
    )

    fun defaultDarkColorScheme(glassify: Boolean = true): ColorScheme = darkColorScheme(
        primary = Color(0xFF94B4A0),        // Soft Nocturnal Sage
        secondary = Color(0xFFE2A89B),      // Soft Dusk Rose
        tertiary = Color(0xFF88A9BC),       // Soft Moonlit Slate
        background = Color(0xFF1A1C1E),     // Serene Midnight
        surface = if (glassify) Color(0xCC24272B) else Color(0xFF24272B),        // Soft Periwinkle Slate Surface
        surfaceVariant = if (glassify) Color(0xCC2F343A) else Color(0xFF2F343A),
        onBackground = Color(0xFFEAE6DF),   // Soft Warm Parchment Text
        onSurface = Color(0xFFEAE6DF),
        outline = Color(0xFF3E434A),
        onPrimary = Color(0xFF1A1C1E),
        onSecondary = Color(0xFF1A1C1E)
    )

    /** Pure AMOLED black — #000000 background for OLED power saving */
    fun amoledDarkColorScheme(glassify: Boolean = true): ColorScheme = darkColorScheme(
        primary = Color(0xFFA8C5B4),        // Soft Sage, gentle on pure black
        secondary = Color(0xFFE8B4A6),      // Dusty warm rose
        tertiary = Color(0xFF8FB5CA),       // Muted sky
        background = Color(0xFF000000),     // Pure AMOLED Black
        surface = if (glassify) Color(0xCC0D0D0D) else Color(0xFF0D0D0D),        // Near-black surface
        surfaceVariant = if (glassify) Color(0xCC1A1A1A) else Color(0xFF1A1A1A), // Subtle card lift
        onBackground = Color(0xFFE8E4DD),   // Warm parchment on black
        onSurface = Color(0xFFE8E4DD),
        outline = Color(0xFF2A2A2A),
        onPrimary = Color(0xFF000000),
        onSecondary = Color(0xFF000000)
    )

    /** Warm sunset golden amber — cozy afternoon diary feel */
    fun sunsetWarmColorScheme(glassify: Boolean = true): ColorScheme = lightColorScheme(
        primary = Color(0xFFCC8844),        // Rich amber gold
        secondary = Color(0xFFD4725C),      // Sunset coral
        tertiary = Color(0xFFA67C52),       // Warm sienna
        background = Color(0xFFFFF8EC),     // Soft warm cream
        surface = if (glassify) Color(0xD9FFFAF2) else Color(0xFFFFFAF2),        // Vanilla parchment
        surfaceVariant = if (glassify) Color(0xCCF5EBD8) else Color(0xFFF5EBD8), // Toasted linen
        onBackground = Color(0xFF3D2E1A),   // Deep warm brown ink
        onSurface = Color(0xFF3D2E1A),
        outline = Color(0xFFE8D8C0),
        onPrimary = Color.White,
        onSecondary = Color.White
    )

    /** Soft lavender lilac — dreamy twilight calm */
    fun lavenderDreamColorScheme(glassify: Boolean = true): ColorScheme = lightColorScheme(
        primary = Color(0xFF9B89B3),        // Muted lavender
        secondary = Color(0xFFBFA0B8),      // Dusty mauve
        tertiary = Color(0xFF7E94B3),       // Periwinkle mist
        background = Color(0xFFF8F4FB),     // Soft lavender white
        surface = if (glassify) Color(0xD9FCF9FE) else Color(0xFFFCF9FE),        // Lilac cream
        surfaceVariant = if (glassify) Color(0xCCF0E8F5) else Color(0xFFF0E8F5), // Light wisteria
        onBackground = Color(0xFF2E2637),   // Deep plum ink
        onSurface = Color(0xFF2E2637),
        outline = Color(0xFFE0D6E8),
        onPrimary = Color.White,
        onSecondary = Color.White
    )

    /** Deep green and moss tones — woodland journal */
    fun forestMistColorScheme(glassify: Boolean = true): ColorScheme = darkColorScheme(
        primary = Color(0xFF6E9C7A),        // Forest sage
        secondary = Color(0xFFA4C3A2),      // Light moss
        tertiary = Color(0xFF5A8C70),       // Deep fern
        background = Color(0xFF101A14),     // Deep woodland night
        surface = if (glassify) Color(0xCC172119) else Color(0xFF172119),        // Mossy surface
        surfaceVariant = if (glassify) Color(0xCC1E2B22) else Color(0xFF1E2B22), // Fern shadow
        onBackground = Color(0xFFD8E8DC),   // Soft lichen text
        onSurface = Color(0xFFD8E8DC),
        outline = Color(0xFF2E3E33),
        onPrimary = Color(0xFF0A130D),
        onSecondary = Color(0xFF0A130D)
    )

    /** Cool teal aqua — seaside serenity */
    fun oceanBreezeColorScheme(glassify: Boolean = true): ColorScheme = lightColorScheme(
        primary = Color(0xFF4D9EA8),        // Teal wave
        secondary = Color(0xFF7BB5A0),      // Sea glass green
        tertiary = Color(0xFF5A8FA8),       // Stormy blue
        background = Color(0xFFF2F9FA),     // Soft seafoam white
        surface = if (glassify) Color(0xD9F7FCFD) else Color(0xFFF7FCFD),        // Pearlescent surface
        surfaceVariant = if (glassify) Color(0xCCE6F2F2) else Color(0xFFE6F2F2), // Sandy shore tint
        onBackground = Color(0xFF1A3035),   // Deep ocean ink
        onSurface = Color(0xFF1A3035),
        outline = Color(0xFFCCE3E6),
        onPrimary = Color.White,
        onSecondary = Color.White
    )

    /** Light Glass Theme — pure black fonts on translucent frosted glass surface */
    fun lightGlassColorScheme(): ColorScheme = lightColorScheme(
        primary = Color(0xFF000000),        // AMOLED Black font/elements
        secondary = Color(0xFF333333),
        tertiary = Color(0xFF666666),
        background = Color(0xFFF0F2F1),
        surface = Color(0xD9FFFFFF),        // Translucent White Glass Surface
        surfaceVariant = Color(0xCCE8ECEA),
        onBackground = Color(0xFF000000),   // Pure AMOLED Black
        onSurface = Color(0xFF000000),
        outline = Color(0xFFCCCCCC),
        onPrimary = Color.White,
        onSecondary = Color.White
    )

    /** Dark Glass Theme — pure white fonts on translucent dark glass surface */
    fun darkGlassColorScheme(): ColorScheme = darkColorScheme(
        primary = Color(0xFFFFFFFF),        // AMOLED White font/elements
        secondary = Color(0xFFE0E0E0),
        tertiary = Color(0xFFB0B0B0),
        background = Color(0xFF000000),     // AMOLED Black background
        surface = Color(0xCC121212),        // Translucent Dark Glass Surface
        surfaceVariant = Color(0xCC1E1E1E),
        onBackground = Color(0xFFFFFFFF),   // Pure AMOLED White
        onSurface = Color(0xFFFFFFFF),
        outline = Color(0xFF333333),
        onPrimary = Color(0xFF000000),
        onSecondary = Color(0xFF000000)
    )

    // ── Utility Functions ─────────────────────────────────────

    private fun calculateWcagTextOnBackground(bgInt: Int): Int {
        val whiteContrast = calculateContrastRatio(AndroidColor.WHITE, bgInt)
        val blackContrast = calculateContrastRatio(AndroidColor.parseColor("#36322F"), bgInt)
        return if (whiteContrast >= blackContrast) AndroidColor.WHITE else AndroidColor.parseColor("#36322F")
    }

    private fun calculateContrastRatio(color1: Int, color2: Int): Double {
        val l1 = calculateLuminance(color1) + 0.05
        val l2 = calculateLuminance(color2) + 0.05
        return if (l1 > l2) l1 / l2 else l2 / l1
    }

    private fun calculateLuminance(color: Int): Double {
        val r = AndroidColor.red(color) / 255.0
        val g = AndroidColor.green(color) / 255.0
        val b = AndroidColor.blue(color) / 255.0

        val rL = if (r <= 0.03928) r / 12.92 else Math.pow((r + 0.055) / 1.055, 2.4)
        val gL = if (g <= 0.03928) g / 12.92 else Math.pow((g + 0.055) / 1.055, 2.4)
        val bL = if (b <= 0.03928) b / 12.92 else Math.pow((b + 0.055) / 1.055, 2.4)

        return 0.2126 * rL + 0.7152 * gL + 0.0722 * bL
    }

    private fun blendColors(color: Int, overlay: Int, ratio: Float): Int {
        val inverseRatio = 1f - ratio
        val r = (AndroidColor.red(color) * inverseRatio + AndroidColor.red(overlay) * ratio).toInt()
        val g = (AndroidColor.green(color) * inverseRatio + AndroidColor.green(overlay) * ratio).toInt()
        val b = (AndroidColor.blue(color) * inverseRatio + AndroidColor.blue(overlay) * ratio).toInt()
        return AndroidColor.rgb(r, g, b)
    }

    fun hexToColor(hex: String): Color {
        return try {
            val parsedHex = if (hex.startsWith("#")) hex else "#$hex"
            Color(AndroidColor.parseColor(parsedHex))
        } catch (e: Exception) {
            Color(0xFF7C9885)
        }
    }

    private fun Int.toHex(): String {
        return String.format("#%06X", 0xFFFFFF and this)
    }

    /** Returns a human-readable display name for each theme preset */
    fun presetDisplayName(preset: ThemePreset): String = when (preset) {
        ThemePreset.DEFAULT_LIGHT -> "Linen Light"
        ThemePreset.DEFAULT_DARK -> "Midnight"
        ThemePreset.AMOLED_BLACK -> "AMOLED Black"
        ThemePreset.SUNSET_WARMTH -> "Sunset Warmth"
        ThemePreset.LAVENDER_DREAM -> "Lavender Dream"
        ThemePreset.FOREST_MIST -> "Forest Mist"
        ThemePreset.OCEAN_BREEZE -> "Ocean Breeze"
        ThemePreset.LIGHT_GLASS -> "Light Glass"
        ThemePreset.DARK_GLASS -> "Dark Glass"
        ThemePreset.SHINING_STARS_GLASS -> "Shining Stars"
        ThemePreset.CUSTOM_IMAGE -> "Custom Image"
    }

    /** Returns the primary accent color for a theme preview swatch */
    fun presetPreviewColor(preset: ThemePreset): Color = when (preset) {
        ThemePreset.DEFAULT_LIGHT -> Color(0xFF7C9885)
        ThemePreset.DEFAULT_DARK -> Color(0xFF94B4A0)
        ThemePreset.AMOLED_BLACK -> Color(0xFFA8C5B4)
        ThemePreset.SUNSET_WARMTH -> Color(0xFFCC8844)
        ThemePreset.LAVENDER_DREAM -> Color(0xFF9B89B3)
        ThemePreset.FOREST_MIST -> Color(0xFF6E9C7A)
        ThemePreset.OCEAN_BREEZE -> Color(0xFF4D9EA8)
        ThemePreset.LIGHT_GLASS -> Color(0xFF000000)
        ThemePreset.DARK_GLASS -> Color(0xFFFFFFFF)
        ThemePreset.SHINING_STARS_GLASS -> Color(0xFF3B82F6)
        ThemePreset.CUSTOM_IMAGE -> Color(0xFFC98A7C)
    }

    /** Returns the background color for a theme preview swatch */
    fun presetPreviewBackground(preset: ThemePreset): Color = when (preset) {
        ThemePreset.DEFAULT_LIGHT -> Color(0xFFFBF8F3)
        ThemePreset.DEFAULT_DARK -> Color(0xFF1A1C1E)
        ThemePreset.AMOLED_BLACK -> Color(0xFF000000)
        ThemePreset.SUNSET_WARMTH -> Color(0xFFFFF8EC)
        ThemePreset.LAVENDER_DREAM -> Color(0xFFF8F4FB)
        ThemePreset.FOREST_MIST -> Color(0xFF101A14)
        ThemePreset.OCEAN_BREEZE -> Color(0xFFF2F9FA)
        ThemePreset.LIGHT_GLASS -> Color(0xFFF0F2F1)
        ThemePreset.DARK_GLASS, ThemePreset.SHINING_STARS_GLASS -> Color(0xFF000000)
        ThemePreset.CUSTOM_IMAGE -> Color(0xFFF4EFE6)
    }
}
