package com.mucephei.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.NoteAdd
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mucephei.app.theme.HandwritingTypography

@Composable
fun TextSelectionToolbar(
    visible: Boolean,
    selectedText: String,
    onDismiss: () -> Unit,
    onSummarizeWithHaven: () -> Unit,
    onAnalyzeWithHaven: () -> Unit,
    onDefineWithHaven: () -> Unit,
    onAddToNewNote: () -> Unit,
    onAppendToLastNote: () -> Unit,
    onSearchWithOasis: () -> Unit,
    modifier: Modifier = Modifier,
    disabledForever: Boolean = false,
    orientation: String = "HORIZONTAL",
    transparency: Float = 0.95f,
    sizePreset: String = "MEDIUM",
    functionOrder: List<String> = listOf("new_note", "append_note", "summarize", "analyze", "define", "search_oasis"),
    disabledFunctions: Set<String> = emptySet(),
    noAutoPopupAtAll: Boolean = false
) {
    if (disabledForever || noAutoPopupAtAll) return
    val effectiveVisible = visible && selectedText.isNotBlank()

    val cornerRadius = when (sizePreset) {
        "SMALL" -> 16.dp
        "LARGE" -> 32.dp
        else -> 24.dp
    }
    val paddingHorizontal = when (sizePreset) {
        "SMALL" -> 8.dp
        "LARGE" -> 16.dp
        else -> 12.dp
    }
    val paddingVertical = when (sizePreset) {
        "SMALL" -> 4.dp
        "LARGE" -> 12.dp
        else -> 8.dp
    }
    val textSize = when (sizePreset) {
        "SMALL" -> HandwritingTypography.labelSmall
        "LARGE" -> HandwritingTypography.labelLarge
        else -> HandwritingTypography.labelMedium
    }
    val iconSize = when (sizePreset) {
        "SMALL" -> 14.dp
        "LARGE" -> 20.dp
        else -> 16.dp
    }
    val spacing = when (sizePreset) {
        "SMALL" -> 4.dp
        "LARGE" -> 8.dp
        else -> 6.dp
    }

    AnimatedVisibility(
        visible = effectiveVisible,
        enter = fadeIn() + slideInVertically { -it / 2 },
        exit = fadeOut() + slideOutVertically { -it / 2 },
        modifier = modifier
    ) {
        Surface(
            shape = RoundedCornerShape(cornerRadius),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = transparency),
            tonalElevation = 8.dp,
            shadowElevation = 10.dp,
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .border(
                    1.dp,
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                    RoundedCornerShape(cornerRadius)
                )
        ) {
            Column(
                modifier = Modifier.padding(horizontal = paddingHorizontal, vertical = paddingVertical)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Actions",
                        style = textSize,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close Toolbar",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                val orderedActions = functionOrder.filter { it !in disabledFunctions }

                if (orientation == "VERTICAL") {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(spacing)
                    ) {
                        orderedActions.forEach { key ->
                            Box(modifier = Modifier.fillMaxWidth()) {
                                ActionChipByKey(
                                    key = key,
                                    textSize = textSize,
                                    iconSize = iconSize,
                                    onSummarize = onSummarizeWithHaven,
                                    onAnalyze = onAnalyzeWithHaven,
                                    onDefine = onDefineWithHaven,
                                    onNewNote = onAddToNewNote,
                                    onAppendNote = onAppendToLastNote,
                                    onSearchOasis = onSearchWithOasis
                                )
                            }
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(spacing),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        orderedActions.forEach { key ->
                            ActionChipByKey(
                                key = key,
                                textSize = textSize,
                                iconSize = iconSize,
                                onSummarize = onSummarizeWithHaven,
                                onAnalyze = onAnalyzeWithHaven,
                                onDefine = onDefineWithHaven,
                                onNewNote = onAddToNewNote,
                                onAppendNote = onAppendToLastNote,
                                onSearchOasis = onSearchWithOasis
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionChipByKey(
    key: String,
    textSize: androidx.compose.ui.text.TextStyle,
    iconSize: androidx.compose.ui.unit.Dp,
    onSummarize: () -> Unit,
    onAnalyze: () -> Unit,
    onDefine: () -> Unit,
    onNewNote: () -> Unit,
    onAppendNote: () -> Unit,
    onSearchOasis: () -> Unit
) {
    when (key) {
        "summarize" -> AssistChip(
            onClick = onSummarize,
            label = { Text("Haven: Summarize", style = textSize) },
            leadingIcon = { Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(iconSize)) },
            colors = AssistChipDefaults.assistChipColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                labelColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
        )
        "analyze" -> AssistChip(
            onClick = onAnalyze,
            label = { Text("Haven: Analyze", style = textSize) },
            leadingIcon = { Icon(Icons.Default.Psychology, contentDescription = null, modifier = Modifier.size(iconSize)) },
            colors = AssistChipDefaults.assistChipColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                labelColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
        )
        "define" -> AssistChip(
            onClick = onDefine,
            label = { Text("Haven: Define", style = textSize) },
            leadingIcon = { Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null, modifier = Modifier.size(iconSize)) },
            colors = AssistChipDefaults.assistChipColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                labelColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
        )
        "new_note" -> AssistChip(
            onClick = onNewNote,
            label = { Text("New Note", style = textSize) },
            leadingIcon = { Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(iconSize)) },
            colors = AssistChipDefaults.assistChipColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                labelColor = MaterialTheme.colorScheme.onSecondaryContainer
            )
        )
        "append_note" -> AssistChip(
            onClick = onAppendNote,
            label = { Text("Append to Note", style = textSize) },
            leadingIcon = { Icon(Icons.Default.PostAdd, contentDescription = null, modifier = Modifier.size(iconSize)) },
            colors = AssistChipDefaults.assistChipColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                labelColor = MaterialTheme.colorScheme.onSecondaryContainer
            )
        )
        "search_oasis" -> AssistChip(
            onClick = onSearchOasis,
            label = { Text("Search Oasis", style = textSize) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(iconSize)) },
            colors = AssistChipDefaults.assistChipColors(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                labelColor = MaterialTheme.colorScheme.onTertiaryContainer
            )
        )
    }
}

@Composable
fun ImageSelectionToolbar(
    visible: Boolean,
    imageUrl: String,
    onDismiss: () -> Unit,
    onSendToCapsule: () -> Unit,
    onSetAsWallpaper: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible && imageUrl.isNotBlank(),
        enter = fadeIn() + slideInVertically { -it / 2 },
        exit = fadeOut() + slideOutVertically { -it / 2 },
        modifier = modifier
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f),
            tonalElevation = 8.dp,
            shadowElevation = 10.dp,
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .border(
                    1.dp,
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                    RoundedCornerShape(24.dp)
                )
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Image Actions",
                        style = HandwritingTypography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close Toolbar",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = onSendToCapsule,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.NoteAdd,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Send to Capsule", style = HandwritingTypography.labelMedium)
                    }

                    Button(
                        onClick = onSetAsWallpaper,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondary
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            Icons.Default.Wallpaper,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Set as Wallpaper", style = HandwritingTypography.labelMedium)
                    }
                }
            }
        }
    }
}
