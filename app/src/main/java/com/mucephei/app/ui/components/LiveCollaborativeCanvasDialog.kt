package com.mucephei.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.mucephei.app.sync.P2pSyncEngine
import com.mucephei.app.theme.HandwritingTypography

/**
 * Standalone Live Collaborative Canvas feature:
 * Activated only when a P2P session is established.
 * Completely independent of individual note drawings.
 */
@Composable
fun LiveCollaborativeCanvasDialog(
    p2pEngine: P2pSyncEngine,
    onDismiss: () -> Unit
) {
    val drawingPaths = remember { mutableStateListOf<Pair<Path, Color>>() }
    var currentPath by remember { mutableStateOf<Path?>(null) }
    val currentStrokePoints = remember { mutableStateListOf<Offset>() }
    var selectedColor by remember { mutableStateOf(Color(0xFF2B4C7E)) }

    val colors = listOf(
        Color(0xFF2B4C7E),
        Color(0xFFD45D5D),
        Color(0xFF2A9D8F),
        Color(0xFFE76F51),
        Color(0xFF264653),
        Color(0xFFF4A261)
    )

    val sessionId = "GLOBAL_LIVE_CANVAS_SESSION"

    // Register P2P callbacks for real-time live drawing co-op
    DisposableEffect(p2pEngine) {
        p2pEngine.onDrawingStrokeReceived = { targetNoteId, points, colorArgb ->
            if (targetNoteId == sessionId && points.isNotEmpty()) {
                val p = Path().apply {
                    moveTo(points.first().x, points.first().y)
                    for (i in 1 until points.size) {
                        lineTo(points[i].x, points[i].y)
                    }
                }
                drawingPaths.add(Pair(p, Color(colorArgb)))
            }
        }
        p2pEngine.onCanvasCleared = { targetNoteId ->
            if (targetNoteId == sessionId) {
                drawingPaths.clear()
            }
        }
        onDispose {
            p2pEngine.onDrawingStrokeReceived = null
            p2pEngine.onCanvasCleared = null
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Top Toolbar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.Brush, contentDescription = "Live Canvas", tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Live Collaborative Canvas",
                            style = HandwritingTypography.headlineMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = MaterialTheme.colorScheme.onSurface)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Color Palette Picker Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    colors.forEach { col ->
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(col)
                                .clickable { selectedColor = col }
                                .then(
                                    if (selectedColor == col) Modifier.padding(2.dp) else Modifier
                                )
                        )
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    Button(
                        onClick = {
                            drawingPaths.clear()
                            p2pEngine.broadcastClearCanvas(sessionId)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Red.copy(alpha = 0.8f)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Clear, contentDescription = "Clear", tint = Color.White)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Clear All", style = HandwritingTypography.labelMedium, color = Color.White)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Drawing Canvas Area
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                ) {
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                detectDragGestures(
                                    onDragStart = { offset ->
                                        currentStrokePoints.clear()
                                        currentStrokePoints.add(offset)
                                        val p = Path().apply { moveTo(offset.x, offset.y) }
                                        currentPath = p
                                    },
                                    onDrag = { change, _ ->
                                        currentStrokePoints.add(change.position)
                                        currentPath?.let { p ->
                                            val updated = Path().apply {
                                                addPath(p)
                                                lineTo(change.position.x, change.position.y)
                                            }
                                            currentPath = updated
                                        }
                                        p2pEngine.broadcastCursorPosition(
                                            noteId = sessionId,
                                            x = change.position.x,
                                            y = change.position.y,
                                            isDrawing = true
                                        )
                                    },
                                    onDragEnd = {
                                        currentPath?.let { path ->
                                            drawingPaths.add(Pair(path, selectedColor))
                                            if (currentStrokePoints.isNotEmpty()) {
                                                p2pEngine.broadcastDrawingStroke(sessionId, currentStrokePoints.toList(), selectedColor.toArgb())
                                            }
                                        }
                                        currentPath = null
                                        currentStrokePoints.clear()
                                    }
                                )
                            }
                    ) {
                        drawingPaths.forEach { (path, color) ->
                            drawPath(path = path, color = color, style = Stroke(width = 4.dp.toPx()))
                        }
                        currentPath?.let { path ->
                            drawPath(path = path, color = selectedColor, style = Stroke(width = 4.dp.toPx()))
                        }
                    }
                }
            }
        }
    }
}
