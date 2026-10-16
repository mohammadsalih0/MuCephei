package com.mucephei.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.mucephei.app.data.model.MediaItem
import com.mucephei.app.data.model.Note
import com.mucephei.app.data.model.NoteTheme
import com.mucephei.app.data.model.ReactionSticker
import com.mucephei.app.data.model.TodoProgress

@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey
    val id: String,
    val title: String,
    val bodyText: String,
    val createdAt: Long,
    val updatedAt: Long,
    val isFinished: Boolean,
    val isPrivate: Boolean = false,
    val isPinned: Boolean = false,
    val isFavorite: Boolean = false,
    val todoProgress: TodoProgress,
    val media: List<MediaItem>,
    val stickers: List<ReactionSticker>,
    val theme: NoteTheme?,
    val version: Long,
    val deleted: Boolean
) {
    fun toNote(): Note {
        return Note(
            id = id,
            title = title,
            bodyText = bodyText,
            createdAt = createdAt,
            updatedAt = updatedAt,
            isFinished = isFinished,
            isPrivate = isPrivate,
            isPinned = isPinned,
            isFavorite = isFavorite,
            todoProgress = todoProgress,
            media = media,
            stickers = stickers,
            theme = theme,
            version = version,
            deleted = deleted
        )
    }

    companion object {
        fun fromNote(note: Note): NoteEntity {
            return NoteEntity(
                id = note.id,
                title = note.title,
                bodyText = note.bodyText,
                createdAt = note.createdAt,
                updatedAt = note.updatedAt,
                isFinished = note.isFinished,
                isPrivate = note.isPrivate,
                isPinned = note.isPinned,
                isFavorite = note.isFavorite,
                todoProgress = note.todoProgress,
                media = note.media,
                stickers = note.stickers,
                theme = note.theme,
                version = note.version,
                deleted = note.deleted
            )
        }
    }
}
