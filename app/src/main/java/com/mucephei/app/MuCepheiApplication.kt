package com.mucephei.app

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.mucephei.app.data.db.AppDatabase
import com.mucephei.app.data.repository.NoteRepository
import com.mucephei.app.sync.EncryptedBackupEngine
import com.mucephei.app.sync.P2pSyncEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MuCepheiApplication : Application(), ImageLoaderFactory {

    val database by lazy { AppDatabase.getDatabase(this) }
    val repository by lazy { NoteRepository(this, database.noteDao()) }
    val p2pSyncEngine by lazy { P2pSyncEngine(database.noteDao(), repository.mediaDirectory) }
    val backupEngine by lazy { EncryptedBackupEngine(this, database.noteDao(), repository.mediaDirectory) }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizePercent(0.05)
                    .build()
            }
            .crossfade(true)
            .build()
    }

    override fun onCreate() {
        super.onCreate()
        // Asynchronously warm up database and Coil image loader on Dispatchers.IO
        CoroutineScope(Dispatchers.IO).launch {
            try {
                database.openHelper.writableDatabase
                coil.Coil.imageLoader(this@MuCepheiApplication)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
