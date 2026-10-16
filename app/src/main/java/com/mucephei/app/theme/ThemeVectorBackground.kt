package com.mucephei.app.theme

import androidx.compose.animation.animateColor
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import com.mucephei.app.data.model.ThemePreset

/**
 * ThemeVectorBackground:
 * Renders serene vector artwork tailored to each theme preset.
 * - Removed entirely for Light Glass and Dark Glass.
 * - Multiplied quantity by 3 with larger, noticeable rendering for other themes.
 */
@Composable
fun ThemeVectorBackground(
    preset: ThemePreset,
    accentColor: Color,
    vectorOpacity: Float = 0.08f,
    visible: Boolean = true,
    modifier: Modifier = Modifier
) {
    if (!visible) return

    // Remove vectors from Light Glass and Dark Glass themes completely
    if (preset == ThemePreset.LIGHT_GLASS || preset == ThemePreset.DARK_GLASS) return

    val infiniteTransition = rememberInfiniteTransition(label = "StarShineTransition")

    val starColor1 by infiniteTransition.animateColor(
        initialValue = Color(0xFF2196F3),
        targetValue = Color(0xFFFFFFFF),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "StarColor1"
    )

    val starColor2 by infiniteTransition.animateColor(
        initialValue = Color(0xFFFFFFFF),
        targetValue = Color(0xFF3B82F6),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "StarColor2"
    )

    val starColor3 by infiniteTransition.animateColor(
        initialValue = Color(0xFF60A5FA),
        targetValue = Color(0xFFFFFFFF),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "StarColor3"
    )

    val starGlowScale by infiniteTransition.animateFloat(
        initialValue = 0.7f,
        targetValue = 1.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "StarGlowScale"
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height

        val strokeColor = accentColor.copy(alpha = (vectorOpacity * 1.8f).coerceIn(0.02f, 0.30f))
        val fillColor = accentColor.copy(alpha = (vectorOpacity * 1.3f).coerceIn(0.02f, 0.20f))

        when (preset) {
            ThemePreset.SHINING_STARS_GLASS -> {
                // Tripled quantity of shining stars, larger and very noticeable
                val starPositions = listOf(
                    Triple(0.10f, 0.06f, 0), Triple(0.25f, 0.12f, 1), Triple(0.40f, 0.05f, 2),
                    Triple(0.60f, 0.10f, 0), Triple(0.78f, 0.06f, 1), Triple(0.90f, 0.15f, 2),
                    Triple(0.06f, 0.22f, 1), Triple(0.20f, 0.28f, 2), Triple(0.35f, 0.18f, 0),
                    Triple(0.52f, 0.24f, 1), Triple(0.70f, 0.20f, 2), Triple(0.85f, 0.26f, 0),
                    Triple(0.12f, 0.38f, 2), Triple(0.28f, 0.42f, 0), Triple(0.45f, 0.34f, 1),
                    Triple(0.58f, 0.40f, 2), Triple(0.75f, 0.36f, 0), Triple(0.92f, 0.46f, 1),
                    Triple(0.04f, 0.54f, 0), Triple(0.22f, 0.52f, 1), Triple(0.38f, 0.60f, 2),
                    Triple(0.55f, 0.50f, 0), Triple(0.72f, 0.56f, 1), Triple(0.88f, 0.62f, 2),
                    Triple(0.08f, 0.72f, 1), Triple(0.30f, 0.68f, 2), Triple(0.48f, 0.76f, 0),
                    Triple(0.65f, 0.70f, 1), Triple(0.82f, 0.78f, 2), Triple(0.95f, 0.85f, 0),
                    Triple(0.15f, 0.88f, 2), Triple(0.35f, 0.84f, 0), Triple(0.58f, 0.90f, 1),
                    // Extra 2x cluster for 3x overall quantity multiplier
                    Triple(0.18f, 0.18f, 1), Triple(0.52f, 0.15f, 2), Triple(0.82f, 0.12f, 0),
                    Triple(0.15f, 0.50f, 2), Triple(0.48f, 0.48f, 0), Triple(0.82f, 0.54f, 1),
                    Triple(0.12f, 0.82f, 0), Triple(0.42f, 0.82f, 1), Triple(0.78f, 0.88f, 2)
                )

                starPositions.forEach { (relX, relY, phase) ->
                    val center = Offset(width * relX, height * relY)
                    val starColor = when (phase) {
                        0 -> starColor1
                        1 -> starColor2
                        else -> starColor3
                    }
                    val baseRadius = if (phase == 0) 4.5f else if (phase == 1) 3.5f else 5.5f

                    drawCircle(
                        color = starColor.copy(alpha = (vectorOpacity * 3f).coerceIn(0.15f, 0.7f)),
                        radius = baseRadius * 3f * starGlowScale,
                        center = center
                    )
                    drawCircle(color = starColor, radius = baseRadius, center = center)

                    if (phase == 2) {
                        val flareLength = baseRadius * 4f * starGlowScale
                        val flareColor = starColor.copy(alpha = 0.9f)
                        drawLine(color = flareColor, start = Offset(center.x - flareLength, center.y), end = Offset(center.x + flareLength, center.y), strokeWidth = 1.5f)
                        drawLine(color = flareColor, start = Offset(center.x, center.y - flareLength), end = Offset(center.x, center.y + flareLength), strokeWidth = 1.5f)
                    }
                }
            }

            ThemePreset.SUNSET_WARMTH -> {
                // Sunset Warmth: 3x botanical flowers & blossoms, noticeably larger
                val centers = listOf(
                    Offset(width * 0.82f, height * 0.12f),
                    Offset(width * 0.18f, height * 0.85f),
                    Offset(width * 0.50f, height * 0.45f),
                    Offset(width * 0.25f, height * 0.22f),
                    Offset(width * 0.78f, height * 0.68f),
                    Offset(width * 0.42f, height * 0.88f)
                )
                centers.forEach { center ->
                    for (i in 0..5) {
                        val angle = Math.toRadians(i * 60.0)
                        val petalX = (center.x + Math.cos(angle) * 35f).toFloat()
                        val petalY = (center.y + Math.sin(angle) * 35f).toFloat()
                        drawCircle(color = fillColor, radius = 22f, center = Offset(petalX, petalY))
                        drawCircle(color = strokeColor, radius = 22f, center = Offset(petalX, petalY), style = Stroke(width = 1.5f))
                    }
                    drawCircle(color = strokeColor, radius = 14f, center = center)
                }
            }

            ThemePreset.LAVENDER_DREAM -> {
                // Lavender Dream: Moons, clouds, and 3x lavender clusters (larger & noticeable)
                val moonCenters = listOf(Offset(width * 0.85f, height * 0.12f), Offset(width * 0.2f, height * 0.45f), Offset(width * 0.7f, height * 0.82f))
                moonCenters.forEach { mc ->
                    drawCircle(color = fillColor, radius = 55f, center = mc)
                    drawCircle(color = strokeColor, radius = 55f, center = mc, style = Stroke(width = 1.5f))
                }
                val stemStarts = listOf(width * 0.10f, width * 0.45f, width * 0.80f)
                stemStarts.forEach { sx ->
                    val stemPath = Path().apply {
                        moveTo(sx, height)
                        cubicTo(sx + 40f, height * 0.75f, sx - 20f, height * 0.55f, sx + 20f, height * 0.35f)
                    }
                    drawPath(path = stemPath, color = strokeColor, style = Stroke(width = 2f))
                }
            }

            ThemePreset.FOREST_MIST -> {
                // Forest Mist: 3x pine trees and prominent mist curves
                val treeXs = listOf(width * 0.82f, width * 0.50f, width * 0.18f)
                treeXs.forEach { tx ->
                    val treePath = Path().apply {
                        moveTo(tx + 40f, height * 0.50f)
                        lineTo(tx, height * 0.80f)
                        lineTo(tx + 20f, height * 0.80f)
                        lineTo(tx - 30f, height)
                        lineTo(tx + 110f, height)
                        close()
                    }
                    drawPath(path = treePath, color = fillColor)
                    drawPath(path = treePath, color = strokeColor, style = Stroke(width = 1.8f))
                }
                val mistYs = listOf(height * 0.35f, height * 0.58f, height * 0.75f)
                mistYs.forEach { my ->
                    val mistPath = Path().apply {
                        moveTo(0f, my)
                        cubicTo(width * 0.3f, my - 30f, width * 0.7f, my + 30f, width, my)
                    }
                    drawPath(path = mistPath, color = strokeColor, style = Stroke(width = 2f))
                }
            }

            ThemePreset.OCEAN_BREEZE -> {
                // Ocean Breeze: 3x wave ripple density and prominence
                var y = height * 0.40f
                var step = 0
                while (y < height) {
                    val offsetPhase = if (step % 2 == 0) 35f else -35f
                    val wavePath = Path().apply {
                        moveTo(0f, y)
                        cubicTo(width * 0.25f, y - offsetPhase, width * 0.75f, y + offsetPhase, width, y)
                        lineTo(width, height)
                        lineTo(0f, height)
                        close()
                    }
                    drawPath(path = wavePath, color = fillColor)
                    drawPath(path = wavePath, color = strokeColor, style = Stroke(width = 1.8f))
                    y += 35f
                    step++
                }
            }

            ThemePreset.DEFAULT_DARK, ThemePreset.AMOLED_BLACK -> {
                // Midnight / AMOLED Black: 3x constellation dots and lines
                val dots = listOf(
                    Offset(width * 0.15f, height * 0.10f),
                    Offset(width * 0.35f, height * 0.18f),
                    Offset(width * 0.75f, height * 0.12f),
                    Offset(width * 0.88f, height * 0.42f),
                    Offset(width * 0.22f, height * 0.68f),
                    Offset(width * 0.50f, height * 0.32f),
                    Offset(width * 0.68f, height * 0.78f),
                    Offset(width * 0.30f, height * 0.85f),
                    Offset(width * 0.85f, height * 0.90f)
                )
                dots.forEach { pt ->
                    drawCircle(color = strokeColor, radius = 5.5f, center = pt)
                }
                for (i in 0 until dots.size - 1) {
                    drawLine(color = strokeColor.copy(alpha = 0.6f), start = dots[i], end = dots[i + 1], strokeWidth = 1.5f)
                }
            }

            else -> {
                // DEFAULT_LIGHT: 3x cherry blossom petals, larger
                val blossomCenters = listOf(
                    Offset(width * 0.15f, height * 0.20f),
                    Offset(width * 0.82f, height * 0.70f),
                    Offset(width * 0.50f, height * 0.50f),
                    Offset(width * 0.80f, height * 0.22f),
                    Offset(width * 0.20f, height * 0.78f)
                )
                blossomCenters.forEach { bc ->
                    drawCircle(color = fillColor, radius = 24f, center = bc)
                    drawCircle(color = strokeColor, radius = 24f, center = bc, style = Stroke(width = 1.5f))
                }
            }
        }
    }
}
