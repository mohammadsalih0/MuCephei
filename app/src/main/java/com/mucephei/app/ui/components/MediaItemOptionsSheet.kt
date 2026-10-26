package com.mucephei.app.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.content.FileProvider
import com.mucephei.app.data.model.MediaItem
import com.mucephei.app.data.model.MediaItemType
import com.mucephei.app.theme.HandwritingTypography
import com.mucephei.app.theme.ThemePreferences
import com.mucephei.app.util.saveMediaToDevice
import java.io.File
import java.io.FileOutputStream

/**
 * Media Item Options Dialog:
 * Shows Replace, Rename, Save/Download, Transcribe, Set as Wallpaper, Share, and Delete options for attached media items.
 */
@Composable
fun MediaItemOptionsSheet(
    mediaItem: MediaItem,
    onReplace: () -> Unit,
    onDelete: () -> Unit,
    onRename: (String) -> Unit,
    onDismiss: () -> Unit,
    onTranscribe: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    var isRenaming by remember { mutableStateOf(false) }
    var renameText by remember { mutableStateOf(mediaItem.metadata.title ?: mediaItem.metadata.fileName ?: "") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(18.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Media Options",
                        style = HandwritingTypography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }

                // Media info
                Text(
                    text = mediaItem.metadata.title ?: mediaItem.metadata.fileName ?: mediaItem.type.name,
                    style = HandwritingTypography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                Spacer(modifier = Modifier.height(8.dp))

                // Rename inline editing
                if (isRenaming) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextField(
                            value = renameText,
                            onValueChange = { renameText = it },
                            textStyle = HandwritingTypography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            placeholder = {
                                Text("New name...", style = HandwritingTypography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                            },
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                                unfocusedIndicatorColor = Color.Transparent
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Save",
                            style = HandwritingTypography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    onRename(renameText)
                                    isRenaming = false
                                }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // Option rows
                OptionRow(
                    icon = Icons.Default.SwapHoriz,
                    label = "Replace",
                    description = "Replace with different file",
                    onClick = {
                        onReplace()
                        onDismiss()
                    }
                )

                OptionRow(
                    icon = Icons.Default.Edit,
                    label = "Rename",
                    description = "Change the display name",
                    onClick = { isRenaming = !isRenaming }
                )

                OptionRow(
                    icon = Icons.Default.Download,
                    label = "Save to Device",
                    description = "Save media to gallery or downloads",
                    onClick = {
                        saveMediaToDevice(context, mediaItem)
                        onDismiss()
                    }
                )

                // Transcribe Audio Option for attached audio recordings
                if ((mediaItem.type == MediaItemType.AUDIO) || (mediaItem.metadata.mimeType?.startsWith("audio/") == true)) {
                    OptionRow(
                        icon = Icons.Default.RecordVoiceOver,
                        label = "Transcribe to Text",
                        description = "Extract text from audio and append to note body",
                        onClick = {
                            onTranscribe?.invoke()
                            onDismiss()
                        },
                    )
                }

                // Set as Wallpaper option for photos & drawings (robust copying to wallpapers dir)
                if (mediaItem.type == MediaItemType.PHOTO || mediaItem.type == MediaItemType.DRAWING) {
                    OptionRow(
                        icon = Icons.Default.Wallpaper,
                        label = "Set as Wallpaper",
                        description = "Change app wallpaper to this image",
                        onClick = {
                            try {
                                val wallpaperDir = File(context.filesDir, "wallpapers").apply { mkdirs() }
                                val targetFile = File(wallpaperDir, "wallpaper_${System.currentTimeMillis()}.jpg")

                                val uri = Uri.parse(mediaItem.uri)
                                if (uri.scheme == "content") {
                                    context.contentResolver.openInputStream(uri)?.use { input ->
                                        FileOutputStream(targetFile).use { output ->
                                            input.copyTo(output)
                                        }
                                    }
                                } else {
                                    val sourceFile = File(mediaItem.uri)
                                    if (sourceFile.exists()) {
                                        sourceFile.copyTo(targetFile, overwrite = true)
                                    } else {
                                        targetFile.writeText(mediaItem.uri)
                                    }
                                }

                                if (targetFile.exists() && targetFile.length() > 0) {
                                    val themePrefs = ThemePreferences(context)
                                    themePrefs.wallpaperPath = targetFile.absolutePath
                                    themePrefs.hideVectors = true
                                    Toast.makeText(context, "Wallpaper set! Please restart the app for it to take effect.", Toast.LENGTH_LONG).show()
                                } else {
                                    Toast.makeText(context, "Failed to set wallpaper", Toast.LENGTH_SHORT).show()
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                                Toast.makeText(context, "Error setting wallpaper: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                            }
                            onDismiss()
                        }
                    )
                }

                // Share only for shareable types
                if (mediaItem.type in listOf(MediaItemType.PHOTO, MediaItemType.VIDEO, MediaItemType.AUDIO, MediaItemType.DOCUMENT)) {
                    OptionRow(
                        icon = Icons.Default.Share,
                        label = "Share",
                        description = "Share via other apps",
                        onClick = {
                            shareMedia(context, mediaItem)
                            onDismiss()
                        }
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                OptionRow(
                    icon = Icons.Default.Delete,
                    label = "Remove",
                    description = "Remove from this note",
                    tint = Color(0xFFD45D5D),
                    onClick = {
                        onDelete()
                        onDismiss()
                    }
                )
            }
        }
    }
}

@Composable
private fun OptionRow(
    icon: ImageVector,
    label: String,
    description: String,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(horizontal = 4.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = tint.copy(alpha = 0.8f),
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column {
            Text(
                text = label,
                style = HandwritingTypography.titleMedium,
                color = tint
            )
            Text(
                text = description,
                style = HandwritingTypography.labelMedium,
                color = tint.copy(alpha = 0.55f)
            )
        }
    }
}

private fun shareMedia(context: Context, mediaItem: MediaItem) {
    try {
        val file = File(mediaItem.uri)
        val uri = if (file.exists()) {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        } else {
            Uri.parse(mediaItem.uri)
        }

        val intent = Intent(Intent.ACTION_SEND).apply {
            val mimeType = when (mediaItem.type) {
                MediaItemType.PHOTO -> "image/*"
                MediaItemType.VIDEO -> "video/*"
                MediaItemType.AUDIO -> "audio/*"
                MediaItemType.DOCUMENT -> "application/*"
                else -> "*/*"
            }
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share media"))
    } catch (e: Exception) {
        e.printStackTrace()
    }
}
