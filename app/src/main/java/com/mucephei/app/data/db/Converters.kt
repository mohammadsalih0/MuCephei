package com.mucephei.app.data.db

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.mucephei.app.data.model.MediaItem
import com.mucephei.app.data.model.NoteTheme
import com.mucephei.app.data.model.ReactionSticker
import com.mucephei.app.data.model.TodoProgress

class Converters {
    private val gson = Gson()

    @TypeConverter
    fun fromTodoProgress(value: TodoProgress?): String {
        return gson.toJson(value ?: TodoProgress())
    }

    @TypeConverter
    fun toTodoProgress(value: String?): TodoProgress {
        if (value.isNullOrBlank()) return TodoProgress()
        return try {
            gson.fromJson(value, TodoProgress::class.java)
        } catch (e: Exception) {
            TodoProgress()
        }
    }

    @TypeConverter
    fun fromMediaItemList(value: List<MediaItem>?): String {
        return gson.toJson(value ?: emptyList<MediaItem>())
    }

    @TypeConverter
    fun toMediaItemList(value: String?): List<MediaItem> {
        if (value.isNullOrBlank()) return emptyList()
        val type = object : TypeToken<List<MediaItem>>() {}.type
        return try {
            gson.fromJson(value, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    @TypeConverter
    fun fromStickerList(value: List<ReactionSticker>?): String {
        return gson.toJson(value ?: emptyList<ReactionSticker>())
    }

    @TypeConverter
    fun toStickerList(value: String?): List<ReactionSticker> {
        if (value.isNullOrBlank()) return emptyList()
        val type = object : TypeToken<List<ReactionSticker>>() {}.type
        return try {
            gson.fromJson(value, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    @TypeConverter
    fun fromNoteTheme(value: NoteTheme?): String? {
        if (value == null) return null
        return gson.toJson(value)
    }

    @TypeConverter
    fun toNoteTheme(value: String?): NoteTheme? {
        if (value.isNullOrBlank()) return null
        return try {
            gson.fromJson(value, NoteTheme::class.java)
        } catch (e: Exception) {
            null
        }
    }

    @TypeConverter
    fun fromStringList(value: List<String>?): String {
        return gson.toJson(value ?: emptyList<String>())
    }

    @TypeConverter
    fun toStringList(value: String?): List<String> {
        if (value.isNullOrBlank()) return emptyList()
        val type = object : TypeToken<List<String>>() {}.type
        return try {
            gson.fromJson(value, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
}
