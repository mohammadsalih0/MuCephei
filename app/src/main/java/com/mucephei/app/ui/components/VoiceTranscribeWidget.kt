package com.mucephei.app.ui.components

import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Done
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.mucephei.app.data.model.MediaItem
import com.mucephei.app.data.model.MediaItemType
import com.mucephei.app.data.model.MediaMetadata
import com.mucephei.app.theme.HandwritingTypography
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Interactive Voice Transcribe Widget:
 * - Uses SpeechRecognizer exclusively for live real-time speech-to-text transcription.
 * - Does NOT run MediaRecorder concurrently to prevent Android microphone locking/conflict.
 * - Streams spoken words in real-time into the note body.
 */
@Composable
fun VoiceTranscribeWidget(
    onTextSegment: (String) -> Unit,
    onTranscriptionComplete: (MediaItem) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var isRecording by remember { mutableStateOf(value = true) }
    var secondsElapsed by remember { mutableIntStateOf(0) }
    var statusText by remember { mutableStateOf(value = "Listening for speech...") }

    var speechRecognizer by remember { mutableStateOf<SpeechRecognizer?>(null) }
    var currentUtteranceText by remember { mutableStateOf(value = "") }

    val primaryColor = MaterialTheme.colorScheme.primary

    val transition = rememberInfiniteTransition(label = "transcribe_waveform")
    val wave1 by transition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(tween(400, easing = LinearEasing), RepeatMode.Reverse),
        label = "tw1",
    )
    val wave2 by transition.animateFloat(
        initialValue = 0.6f,
        targetValue = 0.2f,
        animationSpec = infiniteRepeatable(tween(550, easing = LinearEasing), RepeatMode.Reverse),
        label = "tw2",
    )
    val wave3 by transition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(tween(350, easing = LinearEasing), RepeatMode.Reverse),
        label = "tw3",
    )

    // Timer effect
    LaunchedEffect(isRecording) {
        while (isRecording) {
            delay(1.seconds)
            secondsElapsed++
        }
    }

    fun createRecognizerIntent(): Intent {
        return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
        }
    }

    fun safeStartListening(recognizer: SpeechRecognizer?) {
        if ((!isRecording) || (recognizer == null)) return
        try {
            recognizer.startListening(createRecognizerIntent())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // Initialize SpeechRecognizer with exclusive microphone access
    LaunchedEffect(Unit) {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
            speechRecognizer = recognizer

            val listener = object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    if (isRecording) statusText = "Listening for speech..."
                }

                override fun onBeginningOfSpeech() {
                    if (isRecording) statusText = "Transcribing live..."
                }

                override fun onRmsChanged(rmsdB: Float) {}

                override fun onBufferReceived(buffer: ByteArray?) {}

                override fun onEndOfSpeech() {
                    if (isRecording) statusText = "Processing speech..."
                }

                override fun onError(error: Int) {
                    if (!isRecording) return
                    statusText = "Listening..."
                    coroutineScope.launch {
                        delay(300.milliseconds)
                        if (isRecording) {
                            safeStartListening(speechRecognizer)
                        }
                    }
                }

                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty()) {
                        val fullText = matches[0]
                        if (fullText.isNotBlank()) {
                            val diff = if (fullText.startsWith(currentUtteranceText)) {
                                fullText.substring(currentUtteranceText.length)
                            } else {
                                " $fullText"
                            }
                            if (diff.isNotBlank()) {
                                onTextSegment(diff)
                            }
                        }
                    }
                    currentUtteranceText = ""
                    if (isRecording) {
                        coroutineScope.launch {
                            delay(200.milliseconds)
                            if (isRecording) {
                                safeStartListening(speechRecognizer)
                            }
                        }
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty()) {
                        val fullText = matches[0]
                        if (fullText.isNotBlank() && (fullText != currentUtteranceText)) {
                            val diff = if (fullText.startsWith(currentUtteranceText)) {
                                fullText.substring(currentUtteranceText.length)
                            } else {
                                " $fullText"
                            }
                            if (diff.isNotBlank()) {
                                currentUtteranceText = fullText
                                onTextSegment(diff)
                            }
                        }
                    }
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            }
            recognizer.setRecognitionListener(listener)

            safeStartListening(recognizer)
        } else {
            statusText = "Speech engine unavailable"
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            try {
                speechRecognizer?.stopListening()
                speechRecognizer?.destroy()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun stopAndSave() {
        if (!isRecording) return
        isRecording = false

        try {
            speechRecognizer?.stopListening()
            speechRecognizer?.destroy()
            speechRecognizer = null
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val timestamp = System.currentTimeMillis()
        val dummyItem = MediaItem(
            type = MediaItemType.AUDIO,
            uri = "transcribe_$timestamp",
            metadata = MediaMetadata(
                title = "transcribe_$timestamp.m4a",
                durationSec = secondsElapsed.toLong().coerceAtLeast(1L),
                fileName = "transcribe_$timestamp.m4a",
                mimeType = "audio/mp4",
            ),
        )
        onTranscriptionComplete(dummyItem)
    }

    fun cancelRecording() {
        if (!isRecording) return
        isRecording = false
        try {
            speechRecognizer?.stopListening()
            speechRecognizer?.destroy()
            speechRecognizer = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
        onCancel()
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.90f))
            .border(1.5.dp, primaryColor.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
            .padding(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { cancelRecording() }, modifier = Modifier.size(32.dp)) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Cancel", tint = MaterialTheme.colorScheme.error)
                }
                Spacer(modifier = Modifier.width(4.dp))
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(primaryColor),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = statusText,
                        style = HandwritingTypography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = formatTime(secondsElapsed),
                        style = HandwritingTypography.labelMedium,
                        color = primaryColor,
                    )
                }
            }

            Canvas(modifier = Modifier.size(width = 40.dp, height = 24.dp)) {
                val barWidth = 3.dp.toPx()
                val gap = 4.dp.toPx()
                val maxH = size.height

                val heights = listOf(wave1, wave2, wave3, wave1 * 0.8f)
                heights.forEachIndexed { idx, factor ->
                    val h = maxH * factor
                    drawRect(
                        color = primaryColor,
                        topLeft = Offset(idx * (barWidth + gap), (maxH - h) / 2f),
                        size = Size(barWidth, h),
                    )
                }
            }

            Button(
                onClick = { stopAndSave() },
                colors = ButtonDefaults.buttonColors(containerColor = primaryColor.copy(alpha = 0.2f), contentColor = primaryColor),
                shape = RoundedCornerShape(12.dp),
            ) {
                Icon(imageVector = Icons.Default.Done, contentDescription = "Done Transcribing", tint = primaryColor)
                Spacer(modifier = Modifier.width(4.dp))
                Text("Done", style = HandwritingTypography.labelMedium)
            }
        }
    }
}

private fun formatTime(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    return String.format(Locale.getDefault(), "%02d:%02d", m, s)
}
