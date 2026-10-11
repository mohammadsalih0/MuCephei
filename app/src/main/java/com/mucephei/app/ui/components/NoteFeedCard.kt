package com.mucephei.app.ui.components

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.AddReaction
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.mucephei.app.data.model.MediaItemType
import com.mucephei.app.data.model.Note
import com.mucephei.app.data.model.OffsetPosition
import com.mucephei.app.data.model.StickerType
import com.mucephei.app.theme.HandwritingTypography
import com.mucephei.app.theme.PaletteEngine
import com.mucephei.app.theme.sketchyBorder
import com.mucephei.app.util.openDocumentOrMedia

data class StickerOption(
    val stickerType: StickerType,
    val emoji: String,
    val label: String
)

val REACTION_OPTIONS = listOf(
    StickerOption(StickerType.JOY, "😂", "Joy"),
    StickerOption(StickerType.HURT, "💔", "Hurt"),
    StickerOption(StickerType.FRUSTRATED, "🔥", "Anger"),
    StickerOption(StickerType.AMAZED, "😲", "Amazed"),
    StickerOption(StickerType.EXCITED, "✨", "Excited"),
    StickerOption(StickerType.TOUCHED, "🥹", "Touched"),
    StickerOption(StickerType.ANXIOUS, "😰", "Anxious"),
    StickerOption(StickerType.BREWING, "☕", "Brewing"),
    StickerOption(StickerType.SEED, "🌱", "Seed Idea"),
    StickerOption(StickerType.POLISHED, "✨", "Polished"),
    StickerOption(StickerType.ACTION_REQUIRED, "🎯", "Action"),
    StickerOption(StickerType.PRIVATE_PERSONAL, "🔒", "Personal"),
    StickerOption(StickerType.SEEN, "👀", "Seen"),
    StickerOption(StickerType.NEEDS_DISCUSSION, "💬", "Discuss"),
    StickerOption(StickerType.LOVED, "💖", "Loved"),
    StickerOption(StickerType.AGREED, "🤝", "Agreed")
)

/**
 * Serene Diary Note Feed Card:
 * - Clean note preview card with un-truncated content and memoized media filtering
 * - Downsampled thumbnail loading for images via Coil ImageRequest to eliminate feed lag
 * - Double-click to toggle favorite (🌸), long-press to enter selection mode
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun NoteFeedCard(
    note: Note,
    isSelected: Boolean = false,
    isMultiSelectMode: Boolean = false,
    onClick: () -> Unit,
    onToggleSelect: () -> Unit = {},
    onReactNote: (StickerType, OffsetPosition) -> Unit,
    onPinToggled: (Boolean) -> Unit,
    onFavoriteToggled: (Boolean) -> Unit,
    onPrivacyToggled: (Boolean) -> Unit,
    onShare: () -> Unit,
    onCopyContent: () -> Unit,
    onSelectMultiple: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    var showReactionPicker by remember { mutableStateOf(false) }

    val themeColors = note.theme
    val cardBg = if (themeColors != null) PaletteEngine.hexToColor(themeColors.surfaceColorHex) else MaterialTheme.colorScheme.surface
    val accentColor = if (themeColors != null) PaletteEngine.hexToColor(themeColors.accentColorHex) else MaterialTheme.colorScheme.primary

    val audioMedia = remember(note.media) { note.media.filter { it.type == MediaItemType.AUDIO } }
    val videoMedia = remember(note.media) { note.media.filter { it.type == MediaItemType.VIDEO } }
    val photoMedia = remember(note.media) { note.media.filter { it.type == MediaItemType.PHOTO || it.type == MediaItemType.DRAWING } }
    val docMedia = remember(note.media) { note.media.filter { it.type == MediaItemType.DOCUMENT } }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isSelected) {
                    Modifier.border(2.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(16.dp))
                } else {
                    Modifier
                }
            )
            .sketchyBorder(color = accentColor.copy(alpha = if (isSelected) 0.8f else 0.30f), strokeWidth = if (isSelected) 2.dp else 1.dp, cornerRadius = 16.dp)
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(
                onClick = {
                    if (isMultiSelectMode) {
                        onToggleSelect()
                    } else {
                        onClick()
                    }
                },
                onLongClick = {
                    if (!isMultiSelectMode) {
                        onSelectMultiple()
                    }
                },
                onDoubleClick = {
                    onFavoriteToggled(!note.isFavorite)
                }
            ),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        shape = RoundedCornerShape(16.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {

                // Title & Badges Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = note.title,
                        style = HandwritingTypography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Unspecified,
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 10.dp)
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (note.isFavorite) {
                            BadgeBox(accentColor = accentColor) {
                                Icon(
                                    Icons.Default.Favorite,
                                    contentDescription = null,
                                    tint = accentColor,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                        if (note.isPinned) {
                            BadgeBox(accentColor = accentColor) {
                                Icon(
                                    imageVector = Icons.Default.PushPin,
                                    contentDescription = "Pinned",
                                    tint = accentColor,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                        if (!note.isFinished) {
                            BadgeBox(accentColor = accentColor) {
                                Text(
                                    text = "Draft",
                                    style = HandwritingTypography.labelSmall,
                                    color = accentColor
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Body text
                if (note.bodyText.isNotBlank()) {
                    Text(
                        text = note.bodyText,
                        style = HandwritingTypography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                        textAlign = TextAlign.Unspecified,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // Media Items Render Sections
                audioMedia.forEach { audioItem ->
                    AudioMicroPlayer(
                        mediaItem = audioItem,
                        accentColor = accentColor,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp)
                    )
                }

                videoMedia.forEach { videoItem ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                            .padding(top = 4.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.Black.copy(alpha = 0.8f)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (!videoItem.metadata.thumbnailUri.isNullOrBlank()) {
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(videoItem.metadata.thumbnailUri)
                                    .crossfade(true)
                                    .size(400, 400)
                                    .build(),
                                contentDescription = "Video Thumbnail",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.35f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayCircle,
                                contentDescription = "Play Video",
                                tint = Color.White,
                                modifier = Modifier.size(48.dp)
                            )
                        }
                    }
                }

                photoMedia.forEach { photoItem ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                            .padding(top = 4.dp)
                            .clip(RoundedCornerShape(12.dp))
                    ) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(photoItem.uri)
                                .crossfade(true)
                                .size(400, 400)
                                .build(),
                            contentDescription = "Photo",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                docMedia.forEach { docItem ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(accentColor.copy(alpha = 0.08f))
                            .clickable {
                                openDocumentOrMedia(context, docItem)
                            }
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Description,
                            contentDescription = "Document",
                            tint = accentColor,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = docItem.metadata.fileName ?: docItem.metadata.title ?: "Document File",
                            style = HandwritingTypography.labelMedium,
                            color = accentColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                if (showReactionPicker) {
                    Spacer(modifier = Modifier.height(6.dp))
                    FlowRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f))
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        REACTION_OPTIONS.forEach { opt ->
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .clickable {
                                        onReactNote(opt.stickerType, OffsetPosition(100f, 100f))
                                        showReactionPicker = false
                                    }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(text = opt.emoji, fontSize = 22.sp)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (!note.isPrivate) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(accentColor.copy(alpha = 0.12f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Icon(Icons.Default.Public,
                                    contentDescription = null,
                                    tint = accentColor.copy(alpha = 0.8f),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        IconButton(
                            onClick = { showReactionPicker = !showReactionPicker },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AddReaction,
                                contentDescription = "Add Reaction",
                                tint = accentColor.copy(alpha = 0.8f),
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        val stickerCounts = note.stickers.groupingBy { it.stickerType }.eachCount()
                        stickerCounts.forEach { (type, count) ->
                            val option = REACTION_OPTIONS.find { it.stickerType == type }
                            val emoji = option?.emoji ?: "✨"
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(accentColor.copy(alpha = 0.12f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "$emoji $count",
                                    style = HandwritingTypography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }

                    Text(
                        text = android.text.format.DateFormat.format("MMM dd, yyyy • HH:mm", note.updatedAt).toString(),
                        style = HandwritingTypography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            }
        }
    }
}

@Composable
fun ThemedActionButton(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String,
    tint: Color = MaterialTheme.colorScheme.primary,
    containerColor: Color = MaterialTheme.colorScheme.surfaceVariant
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = containerColor,
        modifier = Modifier
            .size(36.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = tint,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
fun ThemedActionTextButton(
    onClick: () -> Unit,
    text: String,
    contentDescription: String,
    tint: Color = MaterialTheme.colorScheme.primary,
    containerColor: Color = MaterialTheme.colorScheme.surfaceVariant
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = containerColor,
        modifier = Modifier
            .size(36.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            Text(text = text, fontSize = 14.sp)
        }
    }
}

@Composable
fun BadgeBox(
    accentColor: Color,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(accentColor.copy(alpha = 0.2f))
            .padding(horizontal = 6.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}
