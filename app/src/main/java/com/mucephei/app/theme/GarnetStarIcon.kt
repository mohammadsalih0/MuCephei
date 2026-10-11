package com.mucephei.app.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

/**
 * Serene Mu Cephei Star Emblem:
 * A celestial blue star in the shape of the famous Mu Cephei star,
 * dynamically adapting to the active theme or custom wallpaper primary color
 * with no surrounding circle.
 */
@Composable
fun GarnetStarIcon(
    modifier: Modifier = Modifier,
    size: Dp = 36.dp,
    primaryColor: Color? = null
) {
    val starColor = primaryColor ?: Color(0xFF3A86AD) // Default celestial blue for Mu Cephei star
    val glowColor = primaryColor ?: Color(0xFF4D9EA8)

    Canvas(modifier = modifier.size(size)) {
        val width = this.size.width
        val height = this.size.height
        val center = Offset(width / 2f, height / 2f)
        val outerRadius = width * 0.46f
        val innerRadius = width * 0.18f

        // No surrounding background circle (pure star emblem)

        // Delicate radial glow matching primary color
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    glowColor.copy(alpha = 0.50f),
                    starColor.copy(alpha = 0.20f),
                    Color.Transparent
                ),
                center = center,
                radius = outerRadius * 1.35f
            ),
            radius = outerRadius * 1.35f,
            center = center
        )

        // Draw 12-pointed Mu Cephei star
        val points = 12
        val path = Path()
        val angleStep = (2 * Math.PI / points).toFloat()

        for (i in 0 until points) {
            val angle = i * angleStep - (Math.PI / 2).toFloat()
            val r = if (i % 2 == 0) outerRadius else innerRadius
            val x = center.x + r * cos(angle)
            val y = center.y + r * sin(angle)

            if (i == 0) {
                path.moveTo(x, y)
            } else {
                path.lineTo(x, y)
            }
        }
        path.close()

        // Star fill matching primary theme/wallpaper color
        drawPath(
            path = path,
            color = starColor
        )

        // Gentle stroke halo
        drawPath(
            path = path,
            color = starColor.copy(alpha = 0.6f),
            style = Stroke(width = 1.0.dp.toPx())
        )

        // Core soft flare
        drawCircle(
            color = Color.White.copy(alpha = 0.85f),
            radius = innerRadius * 0.65f,
            center = center
        )
    }
}
