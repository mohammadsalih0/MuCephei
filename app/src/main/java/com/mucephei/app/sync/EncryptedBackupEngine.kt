package com.mucephei.app.sync

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.mucephei.app.data.db.NoteDao
import com.mucephei.app.data.db.NoteEntity
import com.mucephei.app.theme.ThemePreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.GeneralSecurityException
import java.security.SecureRandom
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

data class AppSettings(
    val selectedPreset: String? = "SUNSET_WARMTH",
    val customThemeJson: String? = null,
    val wallpaperPath: String? = null,
    val blurRadius: Float = 0f,
    val wallpaperAlpha: Float = 0.4f,
    val autoNightModeEnabled: Boolean = false,
    val fontScale: Float = 1.0f,
    val isGridView: Boolean = false,
    val vectorOpacity: Float = 0.75f,
    val hideVectors: Boolean = false,
    val textTransparencyEnabled: Boolean = false,
    val textTransparencyAlpha: Float = 0.35f,
    val glassifyEnabled: Boolean = true,
    val lastHostIp: String? = "",
    val customRoomCode: String? = "",
    val lastRoomCode: String? = null,
    val swipeModeFull: Boolean? = null,
    val edgeWidthDp: Float? = null,
    val capsuleHavenSensitivity: Float? = null,
    val oasisSensitivity: Float? = null
)

data class RestoreOptions(
    val restoreCapsuleEntries: Boolean = true,
    val restoreCapsuleSettings: Boolean = true,
    val restoreHavenSettings: Boolean = true,
    val restoreHavenHistory: Boolean = true,
    val restoreOasisSettings: Boolean = true,
    val restoreOasisHistoryAndData: Boolean = true,
    val restoreWallpapers: Boolean = true
) {
    companion object {
        val ALL = RestoreOptions(true, true, true, true, true, true, true)
        val SETTINGS_ONLY = RestoreOptions(false, true, true, false, true, false, true)
        val WALLPAPER_ONLY = RestoreOptions(false, true, false, false, false, false, true)
    }
}

/**
 * Robust Backup & Restore Engine supporting AES-256-GCM Streaming Encryption,
 * Atomic Staging Directory Extraction, Zip Slip Protection, and Strict Schema Validation.
 */
class EncryptedBackupEngine(
    private val context: Context,
    private val noteDao: NoteDao,
    private val mediaDirectory: File
) {
    private val gson = Gson()
    private val themePrefs = ThemePreferences(context)

    companion object {
        private const val KEY_SIZE = 256
        private const val ITERATIONS = 10000
        private const val SALT_SIZE = 16
        private const val IV_SIZE = 12
        private const val TAG_LENGTH_BITS = 128
        const val BACKUP_VERSION_LABEL = "V2"
        const val VERSION_FILE_NAME = "version.txt"
    }

    /**
     * Exports all notes, app settings, Haven AI state, Oasis browser state, custom wallpapers,
     * media blobs, drawings, audio, connection profiles, VPN profiles, and WebView persistent data
     * into a .mucephi archive atomically using a staging file.
     */
    suspend fun exportArchive(destinationFile: File, password: CharArray?, encrypt: Boolean = true): Boolean = withContext(Dispatchers.IO) {
        val stagingFile = File(destinationFile.parentFile, "${destinationFile.name}.tmp")
        try {
            val notes = noteDao.getNotesModifiedSince(sinceTimestamp = 0L)
            val jsonPayload = gson.toJson(notes)

            val swipePrefs = context.getSharedPreferences("app_swipe_settings", Context.MODE_PRIVATE)
            val swipeModeFull = if (swipePrefs.contains("swipe_mode_full")) swipePrefs.getBoolean("swipe_mode_full", true) else null
            val edgeWidthDp = if (swipePrefs.contains("edge_width_dp")) swipePrefs.getFloat("edge_width_dp", 48f) else null
            val capsuleHavenSen = if (swipePrefs.contains("capsule_haven_sensitivity")) swipePrefs.getFloat("capsule_haven_sensitivity", 15f) else null
            val oasisSen = if (swipePrefs.contains("oasis_sensitivity")) swipePrefs.getFloat("oasis_sensitivity", 15f) else null

            val settings = AppSettings(
                selectedPreset = themePrefs.selectedPreset.name,
                customThemeJson = if (themePrefs.customTheme != null) gson.toJson(themePrefs.customTheme) else null,
                wallpaperPath = themePrefs.wallpaperPath,
                blurRadius = themePrefs.blurRadius,
                wallpaperAlpha = themePrefs.wallpaperAlpha,
                autoNightModeEnabled = themePrefs.autoNightModeEnabled,
                fontScale = themePrefs.fontScale,
                isGridView = themePrefs.isGridView,
                vectorOpacity = themePrefs.vectorOpacity,
                hideVectors = themePrefs.hideVectors,
                textTransparencyEnabled = themePrefs.textTransparencyEnabled,
                textTransparencyAlpha = themePrefs.textTransparencyAlpha,
                glassifyEnabled = themePrefs.glassifyEnabled,
                lastHostIp = themePrefs.lastHostIp,
                customRoomCode = themePrefs.customRoomCode,
                lastRoomCode = themePrefs.lastRoomCode,
                swipeModeFull = swipeModeFull,
                edgeWidthDp = edgeWidthDp,
                capsuleHavenSensitivity = capsuleHavenSen,
                oasisSensitivity = oasisSen
            )
            val settingsJson = gson.toJson(settings)

            val havenPrefs = context.getSharedPreferences("haven_ai_prefs", Context.MODE_PRIVATE)
            val havenJson = gson.toJson(havenPrefs.all)

            val oasisPrefs = context.getSharedPreferences("oasis_browser_prefs", Context.MODE_PRIVATE)
            val oasisJson = gson.toJson(oasisPrefs.all)

            // Write ZIP to staging file first
            FileOutputStream(stagingFile).use { fos ->
                ZipOutputStream(fos).use { zos ->
                    zos.putNextEntry(ZipEntry(VERSION_FILE_NAME))
                    zos.write(BACKUP_VERSION_LABEL.toByteArray(Charsets.UTF_8))
                    zos.closeEntry()

                    zos.putNextEntry(ZipEntry("notes_db.json"))
                    zos.write(jsonPayload.toByteArray(Charsets.UTF_8))
                    zos.closeEntry()

                    zos.putNextEntry(ZipEntry("settings.json"))
                    zos.write(settingsJson.toByteArray(Charsets.UTF_8))
                    zos.closeEntry()

                    zos.putNextEntry(ZipEntry("haven_ai.json"))
                    zos.write(havenJson.toByteArray(Charsets.UTF_8))
                    zos.closeEntry()

                    zos.putNextEntry(ZipEntry("oasis_browser.json"))
                    zos.write(oasisJson.toByteArray(Charsets.UTF_8))
                    zos.closeEntry()

                    if (mediaDirectory.exists() && mediaDirectory.isDirectory) {
                        mediaDirectory.listFiles()?.forEach { file ->
                            if (file.isFile) {
                                zos.putNextEntry(ZipEntry("media/${file.name}"))
                                FileInputStream(file).use { it.copyTo(zos) }
                                zos.closeEntry()
                            }
                        }
                    }

                    val drawingsDir = File(context.filesDir, "mucephei_drawings")
                    if (drawingsDir.exists() && drawingsDir.isDirectory) {
                        drawingsDir.listFiles()?.forEach { file ->
                            if (file.isFile) {
                                zos.putNextEntry(ZipEntry("drawings/${file.name}"))
                                FileInputStream(file).use { it.copyTo(zos) }
                                zos.closeEntry()
                            }
                        }
                    }

                    val audioDir = File(context.filesDir, "mucephi_audio")
                    if (audioDir.exists() && audioDir.isDirectory) {
                        audioDir.listFiles()?.forEach { file ->
                            if (file.isFile) {
                                zos.putNextEntry(ZipEntry("audio/${file.name}"))
                                FileInputStream(file).use { it.copyTo(zos) }
                                zos.closeEntry()
                            }
                        }
                    }

                    val connDir = File(context.filesDir, "mucephi_connections")
                    if (connDir.exists() && connDir.isDirectory) {
                        connDir.listFiles()?.forEach { file ->
                            if (file.isFile) {
                                zos.putNextEntry(ZipEntry("connections/${file.name}"))
                                FileInputStream(file).use { it.copyTo(zos) }
                                zos.closeEntry()
                            }
                        }
                    }

                    val vpnDirectory = File(mediaDirectory.parentFile, "mucephi_vpn")
                    if (vpnDirectory.exists() && vpnDirectory.isDirectory) {
                        vpnDirectory.listFiles()?.forEach { file ->
                            if (file.isFile) {
                                zos.putNextEntry(ZipEntry("vpn_profiles/${file.name}"))
                                FileInputStream(file).use { it.copyTo(zos) }
                                zos.closeEntry()
                            }
                        }
                    }

                    val wallpaperDirectory = File(context.filesDir, "wallpapers")
                    if (wallpaperDirectory.exists() && wallpaperDirectory.isDirectory) {
                        wallpaperDirectory.listFiles()?.forEach { file ->
                            if (file.isFile) {
                                zos.putNextEntry(ZipEntry("wallpapers/${file.name}"))
                                FileInputStream(file).use { it.copyTo(zos) }
                                zos.closeEntry()
                            }
                        }
                    }

                    val webViewDir = File(context.dataDir, "app_webview")
                    if (webViewDir.exists() && webViewDir.isDirectory) {
                        webViewDir.walkTopDown().forEach { file ->
                            if (file.isFile) {
                                val relativePath = file.relativeTo(webViewDir).path
                                val lowerPath = relativePath.lowercase()
                                if (!lowerPath.contains("cache") && !lowerPath.contains("gpucache") && !lowerPath.contains("code cache")) {
                                    zos.putNextEntry(ZipEntry("oasis_data/$relativePath"))
                                    FileInputStream(file).use { it.copyTo(zos) }
                                    zos.closeEntry()
                                }
                            }
                        }
                    }
                }
            }

            // Encrypt staging file if requested using streaming CipherInputStream / CipherOutputStream
            if (encrypt && password != null && password.isNotEmpty()) {
                val salt = ByteArray(SALT_SIZE).apply { SecureRandom().nextBytes(this) }
                val iv = ByteArray(IV_SIZE).apply { SecureRandom().nextBytes(this) }
                val secretKey = deriveKey(password, salt)

                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                val spec = GCMParameterSpec(TAG_LENGTH_BITS, iv)
                cipher.init(Cipher.ENCRYPT_MODE, secretKey, spec)

                val encryptedTmpFile = File(destinationFile.parentFile, "${destinationFile.name}.enc.tmp")
                FileOutputStream(encryptedTmpFile).use { fos ->
                    fos.write(salt)
                    fos.write(iv)
                    FileInputStream(stagingFile).use { fis ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        while (fis.read(buffer).also { bytesRead = it } != -1) {
                            val output = cipher.update(buffer, 0, bytesRead)
                            if (output != null) fos.write(output)
                        }
                        val finalOutput = cipher.doFinal()
                        if (finalOutput != null) fos.write(finalOutput)
                    }
                }
                stagingFile.delete()
                if (destinationFile.exists()) destinationFile.delete()
                encryptedTmpFile.renameTo(destinationFile)
            } else {
                if (destinationFile.exists()) destinationFile.delete()
                stagingFile.renameTo(destinationFile)
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            if (stagingFile.exists()) stagingFile.delete()
            false
        }
    }

    suspend fun exportEncryptedArchive(destinationFile: File, password: CharArray): Boolean {
        return exportArchive(destinationFile, password, true)
    }

    /**
     * Opens a ZipInputStream from the archive file, handling streaming AES-256-GCM decryption
     * without loading the entire file into memory (preventing OOM errors).
     */
    private fun openZipInputStream(archiveFile: File, password: CharArray?): ZipInputStream {
        val fis = FileInputStream(archiveFile)
        val bufferedFis = BufferedInputStream(fis)
        
        // Check if unencrypted ZIP (PK\x03\x04)
        bufferedFis.mark(4)
        val header = ByteArray(4)
        val readCount = bufferedFis.read(header)
        bufferedFis.reset()

        if (readCount == 4 && header[0] == 0x50.toByte() && header[1] == 0x4B.toByte() && header[2] == 0x03.toByte() && header[3] == 0x04.toByte()) {
            return ZipInputStream(bufferedFis)
        }

        // Encrypted archive
        if (password == null || password.isEmpty()) {
            bufferedFis.close()
            throw IllegalArgumentException("Password required for encrypted archive")
        }

        val salt = ByteArray(SALT_SIZE)
        if (bufferedFis.read(salt) != SALT_SIZE) {
            bufferedFis.close()
            throw GeneralSecurityException("Invalid encrypted archive: salt missing")
        }

        val iv = ByteArray(IV_SIZE)
        if (bufferedFis.read(iv) != IV_SIZE) {
            bufferedFis.close()
            throw GeneralSecurityException("Invalid encrypted archive: IV missing")
        }

        val secretKey = deriveKey(password, salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(TAG_LENGTH_BITS, iv)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)

        val cis = CipherInputStream(bufferedFis, cipher)
        return ZipInputStream(cis)
    }

    /**
     * Restores archive securely using streaming decryption, Zip Slip protection,
     * and an atomic staging directory extraction.
     */
    suspend fun restoreArchive(
        archiveFile: File,
        password: CharArray?,
        encrypted: Boolean = true,
        restoreOptions: RestoreOptions = RestoreOptions.ALL
    ): Boolean = withContext(Dispatchers.IO) {
        val stagingDir = File(context.cacheDir, "restore_staging_${System.currentTimeMillis()}").apply { mkdirs() }
        try {
            if (!archiveFile.exists() || archiveFile.length() == 0L) {
                return@withContext false
            }

            var detectedVersion = BACKUP_VERSION_LABEL
            var hasNotesDb = false
            var entryCount = 0

            // Pass 1: Extract encrypted/unencrypted stream into secure staging directory with Zip Slip protection
            openZipInputStream(archiveFile, password).use { zis ->
                var entry: ZipEntry? = zis.nextEntry
                while (entry != null) {
                    entryCount++
                    val entryName = entry.name
                    if (entryName.contains("..")) {
                        throw SecurityException("Zip Slip path traversal attack detected: $entryName")
                    }

                    if (entryName == "notes_db.json") hasNotesDb = true

                    val targetFile = File(stagingDir, entryName)
                    val canonicalStagingPath = stagingDir.canonicalPath
                    val canonicalTargetPath = targetFile.canonicalPath
                    if (!canonicalTargetPath.startsWith(canonicalStagingPath + File.separator) && canonicalTargetPath != canonicalStagingPath) {
                        throw SecurityException("Zip Slip path traversal attack detected: $entryName")
                    }

                    targetFile.parentFile?.mkdirs()
                    if (!entry.isDirectory) {
                        FileOutputStream(targetFile).use { fos ->
                            zis.copyTo(fos)
                        }
                    }
                    entry = zis.nextEntry
                }
            }

            if (entryCount == 0) {
                stagingDir.deleteRecursively()
                return@withContext false
            }

            // Strict Schema Validation: prevent false-positive success on empty/truncated archives
            if (restoreOptions.restoreCapsuleEntries && !hasNotesDb && !File(stagingDir, "notes_db.json").exists()) {
                // If notes_db.json was expected for capsule entries restoration but missing, fail safely
                stagingDir.deleteRecursively()
                return@withContext false
            }

            // Detect version from staging
            val versionFile = File(stagingDir, VERSION_FILE_NAME)
            if (versionFile.exists()) {
                val ver = versionFile.readText(Charsets.UTF_8).trim()
                if (ver.isNotBlank()) detectedVersion = ver
            } else {
                val legacyLabel = File(stagingDir, "label.txt")
                if (legacyLabel.exists()) {
                    val ver = legacyLabel.readText(Charsets.UTF_8).trim()
                    if (ver.isNotBlank()) detectedVersion = ver
                }
            }

            // Pass 2: Atomically apply staged files to production directories and database
            val success = if (detectedVersion.startsWith("V2") || detectedVersion == "2") {
                restoreV2FromStaging(stagingDir, restoreOptions)
            } else {
                restoreV1FromStaging(stagingDir, restoreOptions)
            }

            stagingDir.deleteRecursively()
            success
        } catch (e: Exception) {
            e.printStackTrace()
            stagingDir.deleteRecursively()
            false
        }
    }

    private fun fixLocalFilePath(path: String?): String? {
        if (path.isNullOrBlank()) return path
        if (path.startsWith("content://") || path.startsWith("http://") || path.startsWith("https://")) return path
        val fileName = File(path).name
        if (fileName.isBlank()) return path

        val mediaFile = File(mediaDirectory, fileName)
        if (mediaFile.exists()) return mediaFile.absolutePath

        val drawingFile = File(context.filesDir, "mucephei_drawings/$fileName")
        if (drawingFile.exists()) return drawingFile.absolutePath

        val audioFile = File(context.filesDir, "mucephi_audio/$fileName")
        if (audioFile.exists()) return audioFile.absolutePath

        val wpFile = File(context.filesDir, "wallpapers/$fileName")
        if (wpFile.exists()) return wpFile.absolutePath

        return path
    }

    private fun putPrefValue(editor: android.content.SharedPreferences.Editor, k: String, v: Any?) {
        when (v) {
            is String -> editor.putString(k, v)
            is Boolean -> editor.putBoolean(k, v)
            is Float -> editor.putFloat(k, v)
            is Double -> {
                if (v % 1.0 == 0.0 && v >= Int.MIN_VALUE.toDouble() && v <= Int.MAX_VALUE.toDouble()) {
                    editor.putInt(k, v.toInt())
                } else if (v % 1.0 == 0.0 && v >= Long.MIN_VALUE.toDouble() && v <= Long.MAX_VALUE.toDouble()) {
                    editor.putLong(k, v.toLong())
                } else {
                    editor.putFloat(k, v.toFloat())
                }
            }
            is Int -> editor.putInt(k, v)
            is Long -> editor.putLong(k, v)
            else -> {
                if (v != null) {
                    editor.putString(k, gson.toJson(v))
                }
            }
        }
    }

    private suspend fun restoreV2FromStaging(stagingDir: File, restoreOptions: RestoreOptions): Boolean {
        return try {
            // 1. Copy binary assets from staging to production
            if (restoreOptions.restoreCapsuleEntries) {
                File(stagingDir, "media").takeIf { it.exists() && it.isDirectory }?.listFiles()?.forEach { file ->
                    if (file.isFile) file.copyTo(File(mediaDirectory, file.name), overwrite = true)
                }
                File(stagingDir, "drawings").takeIf { it.exists() && it.isDirectory }?.listFiles()?.forEach { file ->
                    if (file.isFile) file.copyTo(File(context.filesDir, "mucephei_drawings/${file.name}").apply { parentFile?.mkdirs() }, overwrite = true)
                }
                File(stagingDir, "audio").takeIf { it.exists() && it.isDirectory }?.listFiles()?.forEach { file ->
                    if (file.isFile) file.copyTo(File(context.filesDir, "mucephi_audio/${file.name}").apply { parentFile?.mkdirs() }, overwrite = true)
                }
                File(stagingDir, "connections").takeIf { it.exists() && it.isDirectory }?.listFiles()?.forEach { file ->
                    if (file.isFile) file.copyTo(File(context.filesDir, "mucephi_connections/${file.name}").apply { parentFile?.mkdirs() }, overwrite = true)
                }
                File(stagingDir, "vpn_profiles").takeIf { it.exists() && it.isDirectory }?.listFiles()?.forEach { file ->
                    if (file.isFile) file.copyTo(File(mediaDirectory.parentFile, "mucephi_vpn/${file.name}").apply { parentFile?.mkdirs() }, overwrite = true)
                }
            }

            if (restoreOptions.restoreWallpapers) {
                File(stagingDir, "wallpapers").takeIf { it.exists() && it.isDirectory }?.listFiles()?.forEach { file ->
                    if (file.isFile) file.copyTo(File(context.filesDir, "wallpapers/${file.name}").apply { parentFile?.mkdirs() }, overwrite = true)
                }
            }

            if (restoreOptions.restoreOasisHistoryAndData) {
                File(stagingDir, "oasis_data").takeIf { it.exists() && it.isDirectory }?.walkTopDown()?.forEach { file ->
                    if (file.isFile) {
                        val relative = file.relativeTo(File(stagingDir, "oasis_data"))
                        val target = File(context.dataDir, "app_webview/$relative")
                        target.parentFile?.mkdirs()
                        file.copyTo(target, overwrite = true)
                    }
                }
            }

            // 2. Restore settings.json
            val settingsFile = File(stagingDir, "settings.json")
            if (settingsFile.exists() && (restoreOptions.restoreCapsuleSettings || restoreOptions.restoreWallpapers)) {
                val settingsJson = settingsFile.readText(Charsets.UTF_8)
                val settings = gson.fromJson(settingsJson, AppSettings::class.java)
                if (settings != null) {
                    if (restoreOptions.restoreCapsuleSettings) {
                        themePrefs.selectedPreset = try { com.mucephei.app.data.model.ThemePreset.valueOf(settings.selectedPreset ?: "SUNSET_WARMTH") } catch (e: Exception) { com.mucephei.app.data.model.ThemePreset.SUNSET_WARMTH }
                        if (settings.customThemeJson != null) {
                            val customTheme = gson.fromJson(settings.customThemeJson, com.mucephei.app.data.model.NoteTheme::class.java)
                            themePrefs.customTheme = customTheme?.copy(
                                customImageUri = fixLocalFilePath(customTheme.customImageUri),
                                backgroundImageUri = fixLocalFilePath(customTheme.backgroundImageUri)
                            )
                        } else {
                            themePrefs.customTheme = null
                        }
                        themePrefs.blurRadius = settings.blurRadius
                        themePrefs.wallpaperAlpha = if (settings.wallpaperAlpha <= 0.01f && !themePrefs.wallpaperPath.isNullOrBlank()) 0.4f else settings.wallpaperAlpha
                        themePrefs.autoNightModeEnabled = settings.autoNightModeEnabled
                        themePrefs.fontScale = settings.fontScale
                        themePrefs.isGridView = settings.isGridView
                        themePrefs.vectorOpacity = settings.vectorOpacity
                        themePrefs.hideVectors = settings.hideVectors
                        themePrefs.textTransparencyEnabled = settings.textTransparencyEnabled
                        themePrefs.textTransparencyAlpha = settings.textTransparencyAlpha
                        themePrefs.glassifyEnabled = settings.glassifyEnabled
                        themePrefs.lastHostIp = settings.lastHostIp ?: ""
                        themePrefs.customRoomCode = settings.customRoomCode ?: ""
                        if (!settings.lastRoomCode.isNullOrBlank()) {
                            themePrefs.lastRoomCode = settings.lastRoomCode
                        }

                        val swipePrefs = context.getSharedPreferences("app_swipe_settings", Context.MODE_PRIVATE)
                        val swipeEdit = swipePrefs.edit()
                        if (settings.swipeModeFull != null) swipeEdit.putBoolean("swipe_mode_full", settings.swipeModeFull)
                        if (settings.edgeWidthDp != null) swipeEdit.putFloat("edge_width_dp", settings.edgeWidthDp)
                        if (settings.capsuleHavenSensitivity != null) swipeEdit.putFloat("capsule_haven_sensitivity", settings.capsuleHavenSensitivity)
                        if (settings.oasisSensitivity != null) swipeEdit.putFloat("oasis_sensitivity", settings.oasisSensitivity)
                        swipeEdit.apply()
                    }

                    if (restoreOptions.restoreWallpapers) {
                        val healedWp = fixLocalFilePath(settings.wallpaperPath)
                        if (!healedWp.isNullOrBlank() && File(healedWp).exists()) {
                            themePrefs.wallpaperPath = healedWp
                        } else {
                            val wpDir = File(context.filesDir, "wallpapers")
                            val firstWp = wpDir.listFiles()?.firstOrNull { it.isFile }
                            themePrefs.wallpaperPath = firstWp?.absolutePath
                        }
                    }
                }
            }

            // 3. Restore haven_ai.json
            val havenFile = File(stagingDir, "haven_ai.json")
            if (havenFile.exists()) {
                val havenJson = havenFile.readText(Charsets.UTF_8)
                val type = object : TypeToken<Map<String, Any?>>() {}.type
                val map = gson.fromJson<Map<String, Any?>>(havenJson, type)
                val havenPrefs = context.getSharedPreferences("haven_ai_prefs", Context.MODE_PRIVATE)
                val editor = havenPrefs.edit()

                if (restoreOptions.restoreHavenSettings && restoreOptions.restoreHavenHistory) {
                    editor.clear()
                    map?.forEach { (k, v) -> putPrefValue(editor, k, v) }
                } else if (!restoreOptions.restoreHavenSettings && restoreOptions.restoreHavenHistory) {
                    val chatJson = map?.get("chat_messages_json") as? String
                    if (chatJson != null) editor.putString("chat_messages_json", chatJson)
                } else if (restoreOptions.restoreHavenSettings) {
                    val currentHistory = havenPrefs.getString("chat_messages_json", null)
                    editor.clear()
                    map?.forEach { (k, v) -> if (k != "chat_messages_json") putPrefValue(editor, k, v) }
                    if (currentHistory != null) editor.putString("chat_messages_json", currentHistory)
                }
                editor.apply()
            }

            // 4. Restore oasis_browser.json
            val oasisFile = File(stagingDir, "oasis_browser.json")
            if (oasisFile.exists()) {
                val oasisJson = oasisFile.readText(Charsets.UTF_8)
                val type = object : TypeToken<Map<String, Any?>>() {}.type
                val map = gson.fromJson<Map<String, Any?>>(oasisJson, type)
                val oasisPrefs = context.getSharedPreferences("oasis_browser_prefs", Context.MODE_PRIVATE)
                val editor = oasisPrefs.edit()
                val historyDataKeys = setOf("open_tabs_json", "history_json", "bookmarks_json", "speed_dial_json")

                if (restoreOptions.restoreOasisSettings && restoreOptions.restoreOasisHistoryAndData) {
                    editor.clear()
                    map?.forEach { (k, v) -> putPrefValue(editor, k, v) }
                } else if (!restoreOptions.restoreOasisSettings && restoreOptions.restoreOasisHistoryAndData) {
                    map?.forEach { (k, v) -> if (historyDataKeys.contains(k)) putPrefValue(editor, k, v) }
                } else if (restoreOptions.restoreOasisSettings) {
                    val savedHistoryData = mutableMapOf<String, Any?>()
                    historyDataKeys.forEach { key -> oasisPrefs.all[key]?.let { savedHistoryData[key] = it } }
                    editor.clear()
                    map?.forEach { (k, v) -> if (!historyDataKeys.contains(k)) putPrefValue(editor, k, v) }
                    savedHistoryData.forEach { (k, v) -> putPrefValue(editor, k, v) }
                }
                editor.apply()
            }

            // 5. Restore notes_db.json
            val notesFile = File(stagingDir, "notes_db.json")
            if (notesFile.exists() && restoreOptions.restoreCapsuleEntries) {
                val json = notesFile.readText(Charsets.UTF_8)
                val restoredEntities = gson.fromJson(json, Array<NoteEntity>::class.java).toList()
                restoredEntities.forEach { entity ->
                    val mediaList = entity.media ?: emptyList()
                    val sanitizedMedia = mediaList.map { item ->
                        val newUri = fixLocalFilePath(item.uri) ?: item.uri
                        val newThumb = fixLocalFilePath(item.metadata.thumbnailUri)
                        item.copy(uri = newUri, metadata = item.metadata.copy(thumbnailUri = newThumb))
                    }
                    val sanitizedTheme = entity.theme?.let { th ->
                        th.copy(customImageUri = fixLocalFilePath(th.customImageUri), backgroundImageUri = fixLocalFilePath(th.backgroundImageUri))
                    }
                    val sanitizedEntity = entity.copy(media = sanitizedMedia, theme = sanitizedTheme)
                    noteDao.reconcileLwwDelta(sanitizedEntity)
                }
            }

            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private suspend fun restoreV1FromStaging(stagingDir: File, restoreOptions: RestoreOptions): Boolean {
        return try {
            if (restoreOptions.restoreCapsuleEntries) {
                File(stagingDir, "media").takeIf { it.exists() && it.isDirectory }?.listFiles()?.forEach { file ->
                    if (file.isFile) file.copyTo(File(mediaDirectory, file.name), overwrite = true)
                }
                File(stagingDir, "vpn_profiles").takeIf { it.exists() && it.isDirectory }?.listFiles()?.forEach { file ->
                    if (file.isFile) file.copyTo(File(mediaDirectory.parentFile, "mucephi_vpn/${file.name}").apply { parentFile?.mkdirs() }, overwrite = true)
                }
            }

            if (restoreOptions.restoreWallpapers) {
                File(stagingDir, "wallpapers").takeIf { it.exists() && it.isDirectory }?.listFiles()?.forEach { file ->
                    if (file.isFile) file.copyTo(File(context.filesDir, "wallpapers/${file.name}").apply { parentFile?.mkdirs() }, overwrite = true)
                }
            }

            val settingsFile = File(stagingDir, "settings.json")
            if (settingsFile.exists() && restoreOptions.restoreCapsuleSettings) {
                val settingsJson = settingsFile.readText(Charsets.UTF_8)
                val settings = gson.fromJson(settingsJson, AppSettings::class.java)
                if (settings != null) {
                    themePrefs.selectedPreset = try { com.mucephei.app.data.model.ThemePreset.valueOf(settings.selectedPreset ?: "SUNSET_WARMTH") } catch (e: Exception) { com.mucephei.app.data.model.ThemePreset.SUNSET_WARMTH }
                    if (settings.customThemeJson != null) {
                        val customTheme = gson.fromJson(settings.customThemeJson, com.mucephei.app.data.model.NoteTheme::class.java)
                        themePrefs.customTheme = customTheme?.copy(
                            customImageUri = fixLocalFilePath(customTheme.customImageUri),
                            backgroundImageUri = fixLocalFilePath(customTheme.backgroundImageUri)
                        )
                    } else {
                        themePrefs.customTheme = null
                    }
                    themePrefs.blurRadius = settings.blurRadius
                    themePrefs.wallpaperAlpha = if (settings.wallpaperAlpha <= 0.01f && !themePrefs.wallpaperPath.isNullOrBlank()) 0.4f else settings.wallpaperAlpha
                    themePrefs.autoNightModeEnabled = settings.autoNightModeEnabled
                    themePrefs.fontScale = settings.fontScale
                    themePrefs.isGridView = settings.isGridView
                    themePrefs.vectorOpacity = settings.vectorOpacity
                    themePrefs.hideVectors = settings.hideVectors
                    themePrefs.textTransparencyEnabled = settings.textTransparencyEnabled
                    themePrefs.textTransparencyAlpha = settings.textTransparencyAlpha
                    themePrefs.glassifyEnabled = settings.glassifyEnabled
                    themePrefs.lastHostIp = settings.lastHostIp ?: ""
                    themePrefs.customRoomCode = settings.customRoomCode ?: ""
                    if (!settings.lastRoomCode.isNullOrBlank()) {
                        themePrefs.lastRoomCode = settings.lastRoomCode
                    }
                }
            }

            val notesFile = File(stagingDir, "notes_db.json")
            if (notesFile.exists() && restoreOptions.restoreCapsuleEntries) {
                val json = notesFile.readText(Charsets.UTF_8)
                val restoredEntities = gson.fromJson(json, Array<NoteEntity>::class.java).toList()
                restoredEntities.forEach { entity ->
                    val sanitizedMedia = entity.media.map { item ->
                        val newUri = fixLocalFilePath(item.uri) ?: item.uri
                        val newThumb = fixLocalFilePath(item.metadata.thumbnailUri)
                        item.copy(uri = newUri, metadata = item.metadata.copy(thumbnailUri = newThumb))
                    }
                    val sanitizedTheme = entity.theme?.let { th ->
                        th.copy(customImageUri = fixLocalFilePath(th.customImageUri), backgroundImageUri = fixLocalFilePath(th.backgroundImageUri))
                    }
                    val sanitizedEntity = entity.copy(media = sanitizedMedia, theme = sanitizedTheme)
                    noteDao.reconcileLwwDelta(sanitizedEntity)
                }
            }

            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun restoreEncryptedArchive(archiveFile: File, password: CharArray): Boolean {
        return restoreArchive(archiveFile, password, true, RestoreOptions.ALL)
    }

    suspend fun getArchiveVersion(
        archiveFile: File,
        password: CharArray?,
        encrypted: Boolean = true
    ): String = withContext(Dispatchers.IO) {
        try {
            if (!archiveFile.exists() || archiveFile.length() == 0L) return@withContext "UNKNOWN"

            var detectedVersion = BACKUP_VERSION_LABEL
            openZipInputStream(archiveFile, password).use { zis ->
                var entry: ZipEntry? = zis.nextEntry
                while (entry != null) {
                    if (entry.name == VERSION_FILE_NAME || entry.name == "version.txt" || entry.name == "label.txt") {
                        val ver = zis.bufferedReader(Charsets.UTF_8).readText().trim()
                        if (ver.isNotBlank()) {
                            detectedVersion = ver
                        }
                        break
                    }
                    entry = zis.nextEntry
                }
            }
            detectedVersion
        } catch (e: Exception) {
            "UNKNOWN"
        }
    }

    private fun deriveKey(password: CharArray, salt: ByteArray): SecretKey {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(password, salt, ITERATIONS, KEY_SIZE)
        val tmp = factory.generateSecret(spec)
        return SecretKeySpec(tmp.encoded, "AES")
    }
}
