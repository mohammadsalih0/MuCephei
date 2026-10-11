package com.mucephei.app.theme

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Serene Hand-Ruled Soft Border Modifier:
 * Renders a delicate, organic, hand-ruled paper border around diary cards and inputs.
 */
fun Modifier.sketchyBorder(
    color: Color = Color(0x337C9885),
    strokeWidth: Dp = 1.dp,
    cornerRadius: Dp = 14.dp,
    roughness: Float = 1.2f,
    seed: Int = 42
): Modifier = this.drawWithContent {
    drawContent()

    val pxStroke = strokeWidth.toPx()
    val pxRadius = cornerRadius.toPx()
    val width = size.width
    val height = size.height

    if (width <= 0 || height <= 0) return@drawWithContent

    val roundRect = RoundRect(
        left = 0f,
        top = 0f,
        right = width,
        bottom = height,
        cornerRadius = CornerRadius(pxRadius)
    )

    val path = Path().apply {
        addRoundRect(roundRect)
    }

    // Soft single-pass gentle border stroke
    drawPath(
        path = path,
        color = color,
        style = Stroke(width = pxStroke)
    )
}

/**
 * Soft Pencil Line Overlay for Drafts (isFinished = false)
 */
fun Modifier.draftHatchOverlay(
    isDraft: Boolean,
    hatchColor: Color = Color(0x1236322F),
    spacing: Dp = 16.dp
): Modifier = this.drawWithContent {
    drawContent()

    if (!isDraft) return@drawWithContent

    val width = size.width
    val height = size.height
    val pxSpacing = spacing.toPx()

    // Delicate single-direction soft pencil lines
    var x = -height
    while (x < width + height) {
        drawLine(
            color = hatchColor,
            start = Offset(x, 0f),
            end = Offset(x + height, height),
            strokeWidth = 1.0f
        )
        x += pxSpacing
    }
}
