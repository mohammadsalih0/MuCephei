package com.mucephei.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {

    @Query("SELECT * FROM notes WHERE deleted = 0 ORDER BY isPinned DESC, updatedAt DESC")
    fun getAllActiveNotes(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE id = :id LIMIT 1")
    fun getNoteByIdFlow(id: String): Flow<NoteEntity?>

    @Query("SELECT * FROM notes WHERE id = :id LIMIT 1")
    suspend fun getNoteById(id: String): NoteEntity?

    @Query("SELECT * FROM notes WHERE deleted = 0 AND isFinished = :isFinished ORDER BY isPinned DESC, updatedAt DESC")
    fun getNotesByStatus(isFinished: Boolean): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE deleted = 0 AND (title LIKE '%' || :query || '%' OR bodyText LIKE '%' || :query || '%') ORDER BY isPinned DESC, updatedAt DESC")
    fun searchNotes(query: String): Flow<List<NoteEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(note: NoteEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(notes: List<NoteEntity>)

    @Update
    suspend fun update(note: NoteEntity)

    @Query("UPDATE notes SET deleted = 1, updatedAt = :updatedAt, version = version + 1 WHERE id = :id")
    suspend fun softDelete(id: String, updatedAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun hardDelete(id: String)

    /**
     * Last-Write-Wins (LWW-Element-Set) CRDT reconciliation:
     * Only insert/update remote note if its version is higher, or if version is equal but updatedAt timestamp is newer.
     */
    @Transaction
    suspend fun reconcileLwwDelta(remoteNote: NoteEntity): Boolean {
        val existing = getNoteById(remoteNote.id)
        if (existing == null) {
            insertOrUpdate(remoteNote)
            return true
        }

        val isRemoteNewer = when {
            remoteNote.version > existing.version -> true
            remoteNote.version == existing.version && remoteNote.updatedAt > existing.updatedAt -> true
            else -> false
        }

        if (isRemoteNewer) {
            insertOrUpdate(remoteNote)
            return true
        }
        return false
    }

    @Query("SELECT * FROM notes WHERE updatedAt >= :sinceTimestamp")
    suspend fun getNotesModifiedSince(sinceTimestamp: Long): List<NoteEntity>
}
