package com.mucephei.app.ui.components

import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.mucephei.app.data.model.MediaItem
import com.mucephei.app.theme.HandwritingTypography
import java.io.File

/**
 * Dialog for transcribing an attached audio file:
 * - If a transcript was saved during recording, provides a 1-tap "Insert Saved Transcript" button.
 * - Otherwise, plays the audio recording with MediaPlayer controls and provides "Start Live Voice Dictation".
 */
@Composable
fun AttachedAudioTranscribeDialog(
    mediaItem: MediaItem,
    onTextSegment: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var isPlaying by remember { mutableStateOf(value = false) }
    var isDictating by remember { mutableStateOf(value = false) }

    val primaryColor = MaterialTheme.colorScheme.primary
    val savedTranscript = mediaItem.metadata.transcript

    LaunchedEffect(mediaItem) {
        if (savedTranscript.isNullOrBlank()) {
            try {
                val player = MediaPlayer().apply {
                    val uri = if (mediaItem.uri.startsWith("/")) {
                        Uri.fromFile(File(mediaItem.uri))
                    } else {
                        Uri.parse(mediaItem.uri)
                    }
                    setDataSource(context, uri)
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build(),
                    )
                    prepare()
                    start()
                    isPlaying = true
                    setOnCompletionListener {
                        isPlaying = false
                    }
                }
                mediaPlayer = player
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            try {
                mediaPlayer?.stop()
                mediaPlayer?.release()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun togglePlayPause() {
        val player = mediaPlayer ?: return
        try {
            isPlaying = if (player.isPlaying) {
                player.pause()
                false
            } else {
                player.start()
                true
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(18.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    imageVector = Icons.Default.RecordVoiceOver,
                    contentDescription = null,
                    tint = primaryColor,
                    modifier = Modifier.size(36.dp),
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Transcribe Attached Audio",
                    style = HandwritingTypography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = mediaItem.metadata.title ?: mediaItem.metadata.fileName ?: "audio_attachment.m4a",
                    style = HandwritingTypography.labelMedium,
                    color = primaryColor,
                )
                Spacer(modifier = Modifier.height(14.dp))

                if (!savedTranscript.isNullOrBlank()) {
                    // Instant 1-tap insertion of saved transcript
                    Text(
                        text = "Saved transcript found for this audio note:",
                        style = HandwritingTypography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            onTextSegment(savedTranscript)
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = primaryColor),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(imageVector = Icons.Default.Done, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Insert Saved Transcript", style = HandwritingTypography.titleMedium, color = MaterialTheme.colorScheme.onPrimary)
                    }
                } else {
                    // Audio Playbank Controller Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = if (isPlaying) "Playing audio recording..." else "Audio paused",
                            style = HandwritingTypography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        )
                        IconButton(onClick = { togglePlayPause() }) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                tint = primaryColor,
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    if (isDictating) {
                        // Embed live VoiceTranscribeWidget
                        VoiceTranscribeWidget(
                            onTextSegment = { segment ->
                                onTextSegment(segment)
                            },
                            onTranscriptionComplete = {
                                isDictating = false
                            },
                            onCancel = {
                                isDictating = false
                            },
                        )
                    } else {
                        Button(
                            onClick = { isDictating = true },
                            colors = ButtonDefaults.buttonColors(containerColor = primaryColor.copy(alpha = 0.15f), contentColor = primaryColor),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(imageVector = Icons.Default.Mic, contentDescription = null, tint = primaryColor)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Start Live Voice Dictation", style = HandwritingTypography.titleMedium, color = primaryColor)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = primaryColor.copy(alpha = 0.2f), contentColor = primaryColor),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Close", style = HandwritingTypography.titleMedium, color = primaryColor)
                }
            }
        }
    }
}
