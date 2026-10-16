package com.mucephei.app.data.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.mucephei.app.data.model.MediaItem
import com.mucephei.app.data.model.NoteTheme
import com.mucephei.app.data.model.ReactionSticker
import com.mucephei.app.data.model.TodoProgress

@Database(
    entities = [NoteEntity::class],
    version = 1,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun noteDao(): NoteDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabaseWithRecovery(context).also { INSTANCE = it }
            }
        }

        private fun buildDatabaseWithRecovery(context: Context): AppDatabase {
            val dbName = "mucephi_database"
            val dbFile = context.getDatabasePath(dbName)
            val dbWalFile = context.getDatabasePath("$dbName-wal")
            val dbShmFile = context.getDatabasePath("$dbName-shm")

            try {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    dbName
                )
                .fallbackToDestructiveMigration(true)
                .build()

                // Verify database is readable/unlocked/un-corrupted
                instance.openHelper.writableDatabase
                return instance
            } catch (e: Throwable) {
                e.printStackTrace()
                // Database corruption or failure detected! Initiate robust recovery routine.
                try {
                    val timestamp = System.currentTimeMillis()
                    val backupFile = context.getDatabasePath("${dbName}_corrupt_backup_$timestamp")
                    val rescuedNotes = mutableListOf<NoteEntity>()

                    if (dbFile.exists()) {
                        try {
                            dbFile.copyTo(backupFile, overwrite = true)
                            if (dbWalFile.exists()) dbWalFile.copyTo(context.getDatabasePath("${dbName}_corrupt_backup_${timestamp}-wal"), overwrite = true)
                            if (dbShmFile.exists()) dbShmFile.copyTo(context.getDatabasePath("${dbName}_corrupt_backup_${timestamp}-shm"), overwrite = true)

                            val rawDb = SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
                            val cursor = rawDb.rawQuery("SELECT name FROM sqlite_master WHERE type='table' AND name='notes'", null)
                            val hasNotesTable = cursor.use { it.moveToFirst() }
                            if (hasNotesTable) {
                                val noteCursor = rawDb.rawQuery("SELECT * FROM notes", null)
                                noteCursor.use { c ->
                                    val idCol = c.getColumnIndex("id")
                                    val titleCol = c.getColumnIndex("title")
                                    val bodyCol = c.getColumnIndex("bodyText")
                                    val createdAtCol = c.getColumnIndex("createdAt")
                                    val updatedAtCol = c.getColumnIndex("updatedAt")
                                    val isFinishedCol = c.getColumnIndex("isFinished")
                                    val isPrivateCol = c.getColumnIndex("isPrivate")
                                    val isPinnedCol = c.getColumnIndex("isPinned")
                                    val isFavoriteCol = c.getColumnIndex("isFavorite")
                                    val todoProgressCol = c.getColumnIndex("todoProgress")
                                    val mediaCol = c.getColumnIndex("media")
                                    val stickersCol = c.getColumnIndex("stickers")
                                    val themeCol = c.getColumnIndex("theme")
                                    val versionCol = c.getColumnIndex("version")
                                    val deletedCol = c.getColumnIndex("deleted")

                                    val gson = Gson()
                                    while (c.moveToNext()) {
                                        try {
                                            val id = if (idCol >= 0) c.getString(idCol) ?: java.util.UUID.randomUUID().toString() else java.util.UUID.randomUUID().toString()
                                            val title = if (titleCol >= 0) c.getString(titleCol) ?: "" else ""
                                            val bodyText = if (bodyCol >= 0) c.getString(bodyCol) ?: "" else ""
                                            val createdAt = if (createdAtCol >= 0) c.getLong(createdAtCol) else System.currentTimeMillis()
                                            val updatedAt = if (updatedAtCol >= 0) c.getLong(updatedAtCol) else System.currentTimeMillis()
                                            val isFinished = if (isFinishedCol >= 0) c.getInt(isFinishedCol) == 1 else false
                                            val isPrivate = if (isPrivateCol >= 0) c.getInt(isPrivateCol) == 1 else false
                                            val isPinned = if (isPinnedCol >= 0) c.getInt(isPinnedCol) == 1 else false
                                            val isFavorite = if (isFavoriteCol >= 0) c.getInt(isFavoriteCol) == 1 else false

                                            val todoProgress = try {
                                                if (todoProgressCol >= 0) c.getString(todoProgressCol)?.let { gson.fromJson(it, TodoProgress::class.java) } ?: TodoProgress() else TodoProgress()
                                            } catch (_: Exception) { TodoProgress() }

                                            val media = try {
                                                if (mediaCol >= 0) c.getString(mediaCol)?.let { gson.fromJson<List<MediaItem>>(it, object : TypeToken<List<MediaItem>>() {}.type) } ?: emptyList() else emptyList()
                                            } catch (_: Exception) { emptyList() }

                                            val stickers = try {
                                                if (stickersCol >= 0) c.getString(stickersCol)?.let { gson.fromJson<List<ReactionSticker>>(it, object : TypeToken<List<ReactionSticker>>() {}.type) } ?: emptyList() else emptyList()
                                            } catch (_: Exception) { emptyList() }

                                            val theme = try {
                                                if (themeCol >= 0) c.getString(themeCol)?.let { gson.fromJson(it, NoteTheme::class.java) } else null
                                            } catch (_: Exception) { null }

                                            val version = if (versionCol >= 0) c.getLong(versionCol) else 1L
                                            val deleted = if (deletedCol >= 0) c.getInt(deletedCol) == 1 else false

                                            rescuedNotes.add(
                                                NoteEntity(
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
                                            )
                                        } catch (rowEx: Exception) {
                                            rowEx.printStackTrace()
                                        }
                                    }
                                }
                            }
                            rawDb.close()
                        } catch (rescueEx: Exception) {
                            rescueEx.printStackTrace()
                        }
                    }

                    // Delete corrupted DB files
                    context.deleteDatabase(dbName)
                    dbFile.delete()
                    dbWalFile.delete()
                    dbShmFile.delete()

                    // Build fresh database
                    val freshInstance = Room.databaseBuilder(
                        context.applicationContext,
                        AppDatabase::class.java,
                        dbName
                    )
                    .fallbackToDestructiveMigration(true)
                    .build()

                    freshInstance.openHelper.writableDatabase

                    // Restore rescued notes
                    if (rescuedNotes.isNotEmpty()) {
                        try {
                            kotlinx.coroutines.runBlocking {
                                freshInstance.noteDao().insertAll(rescuedNotes)
                            }
                        } catch (restoreEx: Exception) {
                            restoreEx.printStackTrace()
                        }
                    }

                    return freshInstance
                } catch (recoveryEx: Exception) {
                    recoveryEx.printStackTrace()
                    context.deleteDatabase(dbName)
                    return Room.databaseBuilder(
                        context.applicationContext,
                        AppDatabase::class.java,
                        dbName
                    )
                    .fallbackToDestructiveMigration(true)
                    .build().also { it.openHelper.writableDatabase }
                }
            }
        }
    }
}
