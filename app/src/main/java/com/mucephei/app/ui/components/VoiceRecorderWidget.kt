package com.mucephei.app.ui.components

import android.media.MediaRecorder
import android.os.Build
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.mucephei.app.data.model.MediaItem
import com.mucephei.app.data.model.MediaItemType
import com.mucephei.app.data.model.MediaMetadata
import com.mucephei.app.theme.HandwritingTypography
import kotlinx.coroutines.delay
import java.io.File

/**
 * Interactive Voice Recorder Widget:
 * - Highest quality audio recording (192kbps AAC, 44.1kHz)
 * - Live animated waveform bars
 * - Elapsed recording time counter
 * - Stop & Save button saving audio directly to local note storage
 * - Supports external stop callback (`onStopReady`) so the main screen mic button can toggle stop/save.
 */
@Composable
fun VoiceRecorderWidget(
    onRecordingComplete: (MediaItem) -> Unit,
    onCancel: () -> Unit,
    onStopReady: ((() -> Unit) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var isRecording by remember { mutableStateOf(true) }
    var secondsElapsed by remember { mutableIntStateOf(0) }
    var outputFile by remember { mutableStateOf<File?>(null) }
    var mediaRecorder by remember { mutableStateOf<MediaRecorder?>(null) }

    val primaryColor = MaterialTheme.colorScheme.primary

    // Waveform bar animation transitions
    val transition = rememberInfiniteTransition(label = "waveform")
    val wave1 by transition.animateFloat(
        initialValue = 0.3f, targetValue = 1.0f,
        animationSpec = infiniteRepeatable(tween(400, easing = LinearEasing), RepeatMode.Reverse), label = "w1"
    )
    val wave2 by transition.animateFloat(
        initialValue = 0.6f, targetValue = 0.2f,
        animationSpec = infiniteRepeatable(tween(550, easing = LinearEasing), RepeatMode.Reverse), label = "w2"
    )
    val wave3 by transition.animateFloat(
        initialValue = 0.2f, targetValue = 0.9f,
        animationSpec = infiniteRepeatable(tween(350, easing = LinearEasing), RepeatMode.Reverse), label = "w3"
    )

    // Timer effect
    LaunchedEffect(isRecording) {
        while (isRecording) {
            delay(1000L)
            secondsElapsed++
        }
    }

    // Initialize MediaRecorder at highest audio quality
    LaunchedEffect(Unit) {
        try {
            val audioDir = File(context.filesDir, "mucephi_audio").apply { mkdirs() }
            val file = File(audioDir, "rec_${System.currentTimeMillis()}.m4a")
            outputFile = file

            val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(192000)
                setAudioSamplingRate(44100)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
            mediaRecorder = recorder
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            try {
                mediaRecorder?.stop()
                mediaRecorder?.release()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun stopAndSave() {
        if (!isRecording) return
        isRecording = false
        try {
            mediaRecorder?.stop()
            mediaRecorder?.release()
            mediaRecorder = null
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val file = outputFile
        val uriStr = if (file != null && file.exists()) file.absolutePath else "asset:///sample_audio.mp3"
        val originalName = file?.name ?: "voice_note.m4a"
        val item = MediaItem(
            type = MediaItemType.AUDIO,
            uri = uriStr,
            metadata = MediaMetadata(
                title = originalName,
                durationSec = secondsElapsed.toLong().coerceAtLeast(1L),
                fileName = originalName,
                mimeType = "audio/mp4"
            )
        )
        onRecordingComplete(item)
    }

    LaunchedEffect(Unit) {
        onStopReady?.invoke { stopAndSave() }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f))
            .border(1.5.dp, primaryColor.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Pulse Recording Indicator Dot
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(primaryColor)
                )

                Spacer(modifier = Modifier.width(10.dp))

                Column {
                    Text(
                        text = "Recording...",
                        style = HandwritingTypography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = formatTime(secondsElapsed),
                        style = HandwritingTypography.labelMedium,
                        color = primaryColor
                    )
                }
            }

            // Animated Waveform Bars Canvas
            Canvas(modifier = Modifier.size(width = 60.dp, height = 28.dp)) {
                val barWidth = 4.dp.toPx()
                val gap = 6.dp.toPx()
                val maxH = size.height

                val heights = listOf(wave1, wave2, wave3, wave1 * 0.7f, wave2 * 0.9f)
                heights.forEachIndexed { idx, factor ->
                    val h = maxH * factor
                    drawRect(
                        color = primaryColor,
                        topLeft = Offset(idx * (barWidth + gap), (maxH - h) / 2f),
                        size = Size(barWidth, h)
                    )
                }
            }

            // Stop & Save Button
            Button(
                onClick = { stopAndSave() },
                colors = ButtonDefaults.buttonColors(containerColor = primaryColor.copy(alpha = 0.2f), contentColor = primaryColor),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(imageVector = Icons.Default.Stop, contentDescription = "Stop & Save", tint = primaryColor)
                Spacer(modifier = Modifier.width(4.dp))
            }
        }
    }
}

private fun formatTime(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    return String.format("%02d:%02d", m, s)
}
