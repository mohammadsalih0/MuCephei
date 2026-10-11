package com.mucephei.app.ui.components

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mucephei.app.data.model.OffsetPosition
import com.mucephei.app.data.model.ReactionSticker
import com.mucephei.app.data.model.StickerType
import kotlin.math.roundToInt

/**
 * Renders reaction stickers placed on note cards or drawing canvas accurately at exact (x, y) coordinates.
 */
@Composable
fun StickerCanvasOverlay(
    stickers: List<ReactionSticker>,
    modifier: Modifier = Modifier,
    onStickerPlaced: ((OffsetPosition) -> Unit)? = null
) {
    var containerSize by remember { mutableStateOf(IntSize.Zero) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { coordinates ->
                containerSize = coordinates.size
            }
            .then(
                if (onStickerPlaced != null) {
                    Modifier.pointerInput(Unit) {
                        detectTapGestures { offset ->
                            onStickerPlaced(OffsetPosition(offset.x, offset.y))
                        }
                    }
                } else Modifier
            )
    ) {
        val widthPx = containerSize.width.toFloat()
        val heightPx = containerSize.height.toFloat()

        if (widthPx > 0f && heightPx > 0f) {
            stickers.forEach { sticker ->
                val xPx = if (sticker.position.x in 0f..1f) {
                    sticker.position.x * widthPx
                } else {
                    sticker.position.x
                }

                val yPx = if (sticker.position.y in 0f..1f) {
                    sticker.position.y * heightPx
                } else {
                    sticker.position.y
                }

                Box(
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                x = (xPx - 14.dp.toPx()).roundToInt().coerceAtLeast(0),
                                y = (yPx - 14.dp.toPx()).roundToInt().coerceAtLeast(0)
                            )
                        }
                        .size(28.dp)
                ) {
                    RenderStickerContent(stickerType = sticker.stickerType)
                }
            }
        }
    }
}

@Composable
fun RenderStickerContent(stickerType: StickerType) {
    val emoji = when (stickerType) {
        StickerType.JOY -> "😂"
        StickerType.HURT -> "💔"
        StickerType.FRUSTRATED -> "🔥"
        StickerType.AMAZED -> "😲"
        StickerType.EXCITED -> "✨"
        StickerType.TOUCHED -> "🥹"
        StickerType.ANXIOUS -> "😰"
        StickerType.BREWING -> "☕"
        StickerType.SEED -> "🌱"
        StickerType.POLISHED -> "✨"
        StickerType.ACTION_REQUIRED -> "🎯"
        StickerType.PRIVATE_PERSONAL -> "🔒"
        StickerType.SEEN -> "👀"
        StickerType.NEEDS_DISCUSSION -> "💬"
        StickerType.LOVED -> "💖"
        StickerType.AGREED -> "🤝"
        StickerType.STAR -> "⭐"
        StickerType.SPARK -> "✨"
        StickerType.PIN -> "📌"
        StickerType.HEART -> "❤️"
        StickerType.COFFEE -> "☕"
        StickerType.FIRE -> "🔥"
        StickerType.LAUGH -> "😂"
        StickerType.FLOWER -> "🌸"
        StickerType.MOON -> "🌙"
        StickerType.THUMBS_UP -> "👍"
        StickerType.CLAP -> "👏"
        StickerType.CROWN -> "👑"
        StickerType.CUSTOM_SVG -> "✨"
    }

    Text(
        text = emoji,
        fontSize = 20.sp
    )
}
