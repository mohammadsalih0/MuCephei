package com.mucephei.app.data.repository

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import com.mucephei.app.data.db.NoteDao
import com.mucephei.app.data.db.NoteEntity
import com.mucephei.app.data.model.MediaItem
import com.mucephei.app.data.model.MediaItemType
import com.mucephei.app.data.model.MediaMetadata
import com.mucephei.app.data.model.Note
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class NoteRepository(
    private val context: Context,
    private val noteDao: NoteDao
) {
    val mediaDirectory: File by lazy {
        File(context.filesDir, "mucephei_media").apply { if (!exists()) mkdirs() }
    }

    val allNotes: Flow<List<Note>> = noteDao.getAllActiveNotes().map { entities ->
        entities.map { it.toNote() }
    }

    fun searchNotes(query: String): Flow<List<Note>> = noteDao.searchNotes(query).map { entities ->
        entities.map { it.toNote() }
    }

    fun getNotesByStatus(isFinished: Boolean): Flow<List<Note>> = noteDao.getNotesByStatus(isFinished).map { entities ->
        entities.map { it.toNote() }
    }

    fun getNoteByIdFlow(id: String): Flow<Note?> = noteDao.getNoteByIdFlow(id).map { it?.toNote() }

    suspend fun getNoteById(id: String): Note? = withContext(Dispatchers.IO) {
        noteDao.getNoteById(id)?.toNote()
    }

    suspend fun saveNote(note: Note) {
        val updatedNote = note.copy(
            updatedAt = System.currentTimeMillis(),
            version = note.version + 1
        )
        noteDao.insertOrUpdate(NoteEntity.fromNote(updatedNote))
    }

    suspend fun softDeleteNote(id: String) {
        noteDao.softDelete(id)
    }

    fun queryOriginalFileName(uri: Uri): String {
        var name = "attachment_${System.currentTimeMillis()}"
        try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) {
                        name = cursor.getString(idx) ?: name
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        if (name == "attachment_${System.currentTimeMillis()}" && uri.lastPathSegment != null) {
            name = uri.lastPathSegment ?: name
        }
        return name
    }

    /**
     * Reliably attach an image to a note by copying it to local app media directory,
     * preserving its exact original file name without any renaming or prefixes.
     */
    suspend fun attachImage(note: Note, imageUri: Uri): Note = withContext(Dispatchers.IO) {
        var localFilePath: String = imageUri.toString()
        var aspectRatio = 1.0f
        val originalName = queryOriginalFileName(imageUri)

        try {
            val destFile = File(mediaDirectory, originalName)

            context.contentResolver.openInputStream(imageUri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }

            if (destFile.exists() && destFile.length() > 0) {
                localFilePath = destFile.absolutePath
                val bitmap = BitmapFactory.decodeFile(destFile.absolutePath)
                if (bitmap != null && bitmap.height > 0) {
                    aspectRatio = bitmap.width.toFloat() / bitmap.height.toFloat()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val mediaItem = MediaItem(
            type = MediaItemType.PHOTO,
            uri = localFilePath,
            metadata = MediaMetadata(
                title = originalName,
                fileName = originalName,
                aspectRatio = aspectRatio
            )
        )

        val updatedNote = note.copy(
            media = note.media + mediaItem,
            updatedAt = System.currentTimeMillis()
        )
        saveNote(updatedNote)
        updatedNote
    }

    /**
     * Reliably attach a video to a note, preserving its exact original file name.
     */
    suspend fun attachVideo(note: Note, videoUri: Uri): Note = withContext(Dispatchers.IO) {
        var localFilePath: String = videoUri.toString()
        var thumbPath: String? = null
        val originalName = queryOriginalFileName(videoUri)

        try {
            val destFile = File(mediaDirectory, originalName)
            context.contentResolver.openInputStream(videoUri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }
            if (destFile.exists() && destFile.length() > 0) {
                localFilePath = destFile.absolutePath
                try {
                    val retriever = android.media.MediaMetadataRetriever()
                    retriever.setDataSource(destFile.absolutePath)
                    val bitmap = retriever.getFrameAtTime(0, android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    retriever.release()
                    if (bitmap != null) {
                        val thumbFile = File(mediaDirectory, "thumb_${System.currentTimeMillis()}.jpg")
                        FileOutputStream(thumbFile).use { out ->
                            bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 80, out)
                        }
                        if (thumbFile.exists() && thumbFile.length() > 0) {
                            thumbPath = thumbFile.absolutePath
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val mediaItem = MediaItem(
            type = MediaItemType.VIDEO,
            uri = localFilePath,
            metadata = MediaMetadata(
                title = originalName,
                fileName = originalName,
                thumbnailUri = thumbPath
            )
        )

        val updatedNote = note.copy(
            media = note.media + mediaItem,
            updatedAt = System.currentTimeMillis()
        )
        saveNote(updatedNote)
        updatedNote
    }

    /**
     * Reliably attach a document to a note, preserving its exact original file name.
     */
    suspend fun attachDocument(note: Note, docUri: Uri): Note = withContext(Dispatchers.IO) {
        var localFilePath: String = docUri.toString()
        val originalName = queryOriginalFileName(docUri)

        try {
            val destFile = File(mediaDirectory, originalName)
            context.contentResolver.openInputStream(docUri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }
            if (destFile.exists() && destFile.length() > 0) {
                localFilePath = destFile.absolutePath
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val mediaItem = MediaItem(
            type = MediaItemType.DOCUMENT,
            uri = localFilePath,
            metadata = MediaMetadata(
                title = originalName,
                fileName = originalName
            )
        )

        val updatedNote = note.copy(
            media = note.media + mediaItem,
            updatedAt = System.currentTimeMillis()
        )
        saveNote(updatedNote)
        updatedNote
    }

    /**
     * Legacy method alias maintained for backward compatibility.
     */
    suspend fun attachImageAndExtractTheme(note: Note, imageUri: Uri): Note {
        return attachImage(note, imageUri)
    }
}
