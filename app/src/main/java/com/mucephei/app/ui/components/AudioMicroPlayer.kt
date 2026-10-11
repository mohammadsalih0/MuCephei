package com.mucephei.app.ui.components

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem as Media3Item
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.mucephei.app.data.model.MediaItem
import com.mucephei.app.theme.HandwritingTypography

/**
 * Serene, Nostalgic Inline Micro Audio Player:
 * Gentle voice note player for main feed cards and note details,
 * optimized with lazy ExoPlayer creation on demand to completely eliminate feed scrolling lag.
 */
@Composable
fun AudioMicroPlayer(
    mediaItem: MediaItem,
    modifier: Modifier = Modifier,
    backgroundColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    accentColor: Color = MaterialTheme.colorScheme.primary
) {
    val context = LocalContext.current
    var isPlaying by remember { mutableStateOf(false) }
    val durationSec = mediaItem.metadata.durationSec ?: 0L

    val iconTint = if (accentColor.luminance() > 0.5f) Color.Black else Color.White

    var exoPlayer by remember { mutableStateOf<ExoPlayer?>(null) }

    DisposableEffect(mediaItem.uri) {
        onDispose {
            exoPlayer?.release()
            exoPlayer = null
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(accentColor.copy(alpha = 0.08f))
            .border(1.dp, accentColor.copy(alpha = 0.20f), RoundedCornerShape(12.dp))
            .padding(horizontal = 28.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .weight(1f)
                .padding(start = 2.dp)
        ) {
            IconButton(
                onClick = {
                    if (isPlaying) {
                        exoPlayer?.pause()
                        isPlaying = false
                    } else {
                        var player = exoPlayer
                        if (player == null) {
                            player = ExoPlayer.Builder(context).build().apply {
                                try {
                                    val mediaUri = Uri.parse(mediaItem.uri)
                                    setMediaItem(Media3Item.fromUri(mediaUri))
                                    prepare()
                                    addListener(object : Player.Listener {
                                        override fun onIsPlayingChanged(playing: Boolean) {
                                            isPlaying = playing
                                        }

                                        override fun onPlaybackStateChanged(playbackState: Int) {
                                            if (playbackState == Player.STATE_ENDED) {
                                                seekTo(0)
                                                pause()
                                                isPlaying = false
                                            }
                                        }
                                    })
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                            exoPlayer = player
                        }
                        player.play()
                        isPlaying = true
                    }
                },
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(accentColor)
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = iconTint,
                    modifier = Modifier.size(20.dp)
                )
            }

            Icon(
                imageVector = Icons.Default.GraphicEq,
                contentDescription = "Waveform",
                tint = accentColor.copy(alpha = 0.75f),
                modifier = Modifier.size(20.dp)
            )

            Text(
                text = mediaItem.metadata.title ?: "Voice Note",
                style = HandwritingTypography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = formatDuration(durationSec),
            style = HandwritingTypography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
    }
}

private fun formatDuration(seconds: Long): String {
    val mins = seconds / 60
    val secs = seconds % 60
    return String.format("%02d:%02d", mins, secs)
}

private fun Color.luminance(): Float {
    val r = red
    val g = green
    val b = blue
    return 0.2126f * r + 0.7152f * g + 0.0722f * b
}
