package com.mucephei.app.data.model

import java.util.UUID

enum class MediaItemType { PHOTO, AUDIO, VIDEO, DOCUMENT, DRAWING, LINK }

data class MediaMetadata(
    val title: String? = null,
    val durationSec: Long? = null, // For audio/video
    val aspectRatio: Float? = null,
    val thumbnailUri: String? = null,
    val fileName: String? = null,
    val fileSize: Long? = null,
    val mimeType: String? = null
)

data class MediaItem(
    val id: String = UUID.randomUUID().toString(),
    val type: MediaItemType,
    val uri: String, // Local storage file path or URI
    val metadata: MediaMetadata = MediaMetadata()
)

enum class StickerType {
    JOY,
    HURT,
    FRUSTRATED,
    AMAZED,
    EXCITED,
    TOUCHED,
    ANXIOUS,
    BREWING,
    SEED,
    POLISHED,
    ACTION_REQUIRED,
    PRIVATE_PERSONAL,
    SEEN,
    NEEDS_DISCUSSION,
    LOVED,
    AGREED,
    // Legacy fallbacks
    STAR, SPARK, PIN, HEART, COFFEE, FIRE, LAUGH, FLOWER, MOON, THUMBS_UP, CLAP, CROWN, CUSTOM_SVG
}

enum class ThemePreset {
    DEFAULT_LIGHT,
    DEFAULT_DARK,
    AMOLED_BLACK,
    SUNSET_WARMTH,
    LAVENDER_DREAM,
    FOREST_MIST,
    OCEAN_BREEZE,
    LIGHT_GLASS,
    DARK_GLASS,
    SHINING_STARS_GLASS,
    CUSTOM_IMAGE
}

data class OffsetPosition(val x: Float, val y: Float)

data class ReactionSticker(
    val id: String = UUID.randomUUID().toString(),
    val stickerType: StickerType,
    val position: OffsetPosition,
    val placedBy: String, // User ID
    val timestamp: Long = System.currentTimeMillis()
)

data class NoteTheme(
    val dominantColorsHex: List<String>, // 2-3 extracted Hex strings
    val backgroundColorHex: String,
    val surfaceColorHex: String,
    val textColorHex: String,
    val accentColorHex: String,
    val customImageUri: String? = null, // Source image for palette extraction
    val backgroundImageUri: String? = null, // Actual displayed wallpaper behind content
    val backgroundBlurRadius: Float = 0f, // 0..25 dp blur
    val selectedPreset: ThemePreset = ThemePreset.DEFAULT_LIGHT
)

data class TodoProgress(val total: Int = 0, val completed: Int = 0)

data class NoteLocation(
    val latitude: Double,
    val longitude: Double,
    val placeName: String? = null
)

data class Note(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val bodyText: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val isFinished: Boolean = false, // Controls visual draft shading/opacity
    val isPrivate: Boolean = true, // If true, note is NOT synced over P2P
    val isPinned: Boolean = false, // If true, note is pinned at the top of the feed list
    val isFavorite: Boolean = false, // If true, note is favorited (flower tab 🌸)
    val todoProgress: TodoProgress = TodoProgress(),
    val media: List<MediaItem> = emptyList(),
    val stickers: List<ReactionSticker> = emptyList(),
    val theme: NoteTheme? = null,
    val location: NoteLocation? = null, // V1.1 Geotag location metadata
    val version: Long = 1L, // Incrementing counter for CRDT/LWW resolution
    val deleted: Boolean = false
)

/**
 * Peer-to-Peer delta payload for CRDT state updates
 */
data class NoteDelta(
    val noteId: String,
    val updatedNote: Note,
    val timestamp: Long = System.currentTimeMillis(),
    val senderDeviceId: String
)

/**
 * Real-time active remote cursor position for P2P co-op canvas drawing
 */
data class RemoteCursorPosition(
    val peerId: String,
    val noteId: String,
    val x: Float,
    val y: Float,
    val isDrawing: Boolean = false,
    val strokeColorHex: String = "#7C9885"
)
