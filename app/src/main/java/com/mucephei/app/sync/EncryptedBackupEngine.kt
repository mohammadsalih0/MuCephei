package com.mucephei.app.sync

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.mucephei.app.data.db.NoteDao
import com.mucephei.app.data.db.NoteEntity
import com.mucephei.app.theme.ThemePreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.SecureRandom
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.crypto.Cipher
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
 * Backup & Restore Engine supporting AES-256-GCM Encrypted and Unencrypted .mucephi File Archives.
 *
 * BACKUP SCHEMA VERSION: V2
 * Packed Data Types & Structures in V2 Archives:
 * 1. `version.txt`: UTF-8 Plain text version string ("V2").
 * 2. `notes_db.json`: JSON array of Room database `NoteEntity` records (including NoteLocation).
 * 3. `settings.json`: AppSettings JSON preferences (Theme preferences & Capsule/Haven/Oasis swipe sensitivities).
 * 4. `haven_ai.json`: Haven AI preferences, chat history, and context window.
 * 5. `oasis_browser.json`: Oasis browser preferences (tabs, history, bookmarks, speed dials with favicons, download dir, custom extensions).
 * 6. `media/`: Binary attachments (Photos, Videos, Documents, Thumbnails) from `mucephei_media`.
 * 7. `drawings/`: Handwritten canvas sketches from `mucephei_drawings`.
 * 8. `audio/`: Voice memo recordings from `mucephi_audio`.
 * 9. `connections/`: Saved P2P connection profiles (.muconn files) from `mucephi_connections`.
 * 10. `vpn_profiles/`: Saved OpenVPN configuration profiles (`.ovpn` files) from `mucephi_vpn`.
 * 11. `wallpapers/`: Saved custom background wallpaper images from `filesDir/wallpapers`.
 * 12. `oasis_data/`: Oasis WebView persistent storage (Local Storage, IndexedDB, WebSQL databases, cookies) excluding cache.
 * 13. Encryption: Optional AES-256-GCM with PBKDF2WithHmacSHA256 (10,000 iterations, 16-byte salt, 12-byte IV, 128-bit tag).
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
     * Exports all notes, app settings, Haven AI state, Oasis browser state, custom wallpapers, media blobs, drawings, audio, connection profiles, VPN profiles, and WebView persistent data into a .mucephi archive.
     */
    suspend fun exportArchive(destinationFile: File, password: CharArray?, encrypt: Boolean = true): Boolean = withContext(Dispatchers.IO) {
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

            val baos = ByteArrayOutputStream()
            ZipOutputStream(baos).use { zos ->
                val versionEntry = ZipEntry(VERSION_FILE_NAME)
                zos.putNextEntry(versionEntry)
                zos.write(BACKUP_VERSION_LABEL.toByteArray(Charsets.UTF_8))
                zos.closeEntry()

                val dbEntry = ZipEntry("notes_db.json")
                zos.putNextEntry(dbEntry)
                zos.write(jsonPayload.toByteArray(Charsets.UTF_8))
                zos.closeEntry()

                val settingsEntry = ZipEntry("settings.json")
                zos.putNextEntry(settingsEntry)
                zos.write(settingsJson.toByteArray(Charsets.UTF_8))
                zos.closeEntry()

                val havenEntry = ZipEntry("haven_ai.json")
                zos.putNextEntry(havenEntry)
                zos.write(havenJson.toByteArray(Charsets.UTF_8))
                zos.closeEntry()

                val oasisEntry = ZipEntry("oasis_browser.json")
                zos.putNextEntry(oasisEntry)
                zos.write(oasisJson.toByteArray(Charsets.UTF_8))
                zos.closeEntry()

                if (mediaDirectory.exists() && mediaDirectory.isDirectory) {
                    mediaDirectory.listFiles()?.forEach { file ->
                        if (file.isFile) {
                            val mediaEntry = ZipEntry("media/${file.name}")
                            zos.putNextEntry(mediaEntry)
                            FileInputStream(file).use { fis ->
                                fis.copyTo(zos)
                            }
                            zos.closeEntry()
                        }
                    }
                }

                val drawingsDir = File(context.filesDir, "mucephei_drawings")
                if (drawingsDir.exists() && drawingsDir.isDirectory) {
                    drawingsDir.listFiles()?.forEach { file ->
                        if (file.isFile) {
                            val drawingEntry = ZipEntry("drawings/${file.name}")
                            zos.putNextEntry(drawingEntry)
                            FileInputStream(file).use { fis ->
                                fis.copyTo(zos)
                            }
                            zos.closeEntry()
                        }
                    }
                }

                val audioDir = File(context.filesDir, "mucephi_audio")
                if (audioDir.exists() && audioDir.isDirectory) {
                    audioDir.listFiles()?.forEach { file ->
                        if (file.isFile) {
                            val audioEntry = ZipEntry("audio/${file.name}")
                            zos.putNextEntry(audioEntry)
                            FileInputStream(file).use { fis ->
                                fis.copyTo(zos)
                            }
                            zos.closeEntry()
                        }
                    }
                }

                val connDir = File(context.filesDir, "mucephi_connections")
                if (connDir.exists() && connDir.isDirectory) {
                    connDir.listFiles()?.forEach { file ->
                        if (file.isFile) {
                            val connEntry = ZipEntry("connections/${file.name}")
                            zos.putNextEntry(connEntry)
                            FileInputStream(file).use { fis ->
                                fis.copyTo(zos)
                            }
                            zos.closeEntry()
                        }
                    }
                }

                val vpnDirectory = File(mediaDirectory.parentFile, "mucephi_vpn")
                if (vpnDirectory.exists() && vpnDirectory.isDirectory) {
                    vpnDirectory.listFiles()?.forEach { file ->
                        if (file.isFile) {
                            val vpnEntry = ZipEntry("vpn_profiles/${file.name}")
                            zos.putNextEntry(vpnEntry)
                            FileInputStream(file).use { fis ->
                                fis.copyTo(zos)
                            }
                            zos.closeEntry()
                        }
                    }
                }

                val wallpaperDirectory = File(context.filesDir, "wallpapers")
                if (wallpaperDirectory.exists() && wallpaperDirectory.isDirectory) {
                    wallpaperDirectory.listFiles()?.forEach { file ->
                        if (file.isFile) {
                            val wpEntry = ZipEntry("wallpapers/${file.name}")
                            zos.putNextEntry(wpEntry)
                            FileInputStream(file).use { fis ->
                                fis.copyTo(zos)
                            }
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
                                val webEntry = ZipEntry("oasis_data/$relativePath")
                                zos.putNextEntry(webEntry)
                                FileInputStream(file).use { fis ->
                                    fis.copyTo(zos)
                                }
                                zos.closeEntry()
                            }
                        }
                    }
                }
            }
            val unencryptedZipBytes = baos.toByteArray()

            if (encrypt && password != null && password.isNotEmpty()) {
                val salt = ByteArray(SALT_SIZE).apply { SecureRandom().nextBytes(this) }
                val iv = ByteArray(IV_SIZE).apply { SecureRandom().nextBytes(this) }
                val secretKey = deriveKey(password, salt)

                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                val spec = GCMParameterSpec(TAG_LENGTH_BITS, iv)
                cipher.init(Cipher.ENCRYPT_MODE, secretKey, spec)
                val encryptedBytes = cipher.doFinal(unencryptedZipBytes)

                FileOutputStream(destinationFile).use { fos ->
                    fos.write(salt)
                    fos.write(iv)
                    fos.write(encryptedBytes)
                }
            } else {
                FileOutputStream(destinationFile).use { fos ->
                    fos.write(unencryptedZipBytes)
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun exportEncryptedArchive(destinationFile: File, password: CharArray): Boolean {
        return exportArchive(destinationFile, password, true)
    }

    private fun isZipArchive(bytes: ByteArray): Boolean {
        if (bytes.size < 4) return false
        return bytes[0] == 0x50.toByte() &&
               bytes[1] == 0x4B.toByte() &&
               bytes[2] == 0x03.toByte() &&
               bytes[3] == 0x04.toByte()
    }

    /**
     * Restores archive with granular RestoreOptions.
     */
    suspend fun restoreArchive(
        archiveFile: File,
        password: CharArray?,
        encrypted: Boolean = true,
        restoreOptions: RestoreOptions = RestoreOptions.ALL
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            if (!archiveFile.exists() || archiveFile.length() == 0L) {
                return@withContext false
            }

            val fileBytes = archiveFile.readBytes()
            if (fileBytes.isEmpty()) return@withContext false

            val decryptedZipBytes = if (isZipArchive(fileBytes)) {
                fileBytes
            } else {
                if (password == null || password.isEmpty()) {
                    return@withContext false
                }
                if (fileBytes.size < SALT_SIZE + IV_SIZE) {
                    return@withContext false
                }

                val salt = fileBytes.copyOfRange(0, SALT_SIZE)
                val iv = fileBytes.copyOfRange(SALT_SIZE, SALT_SIZE + IV_SIZE)
                val encryptedBytes = fileBytes.copyOfRange(SALT_SIZE + IV_SIZE, fileBytes.size)

                val secretKey = deriveKey(password, salt)
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                val spec = GCMParameterSpec(TAG_LENGTH_BITS, iv)
                cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)

                val decrypted = cipher.doFinal(encryptedBytes)
                if (!isZipArchive(decrypted)) {
                    return@withContext false
                }
                decrypted
            }

            var detectedVersion = BACKUP_VERSION_LABEL

            ZipInputStream(ByteArrayInputStream(decryptedZipBytes)).use { zis ->
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

            android.util.Log.d("EncryptedBackupEngine", "Restoring archive with version label: $detectedVersion")

            val restoreResult = when {
                detectedVersion.startsWith("V2") || detectedVersion == "2" -> {
                    restoreV2ArchiveData(decryptedZipBytes, restoreOptions)
                }
                detectedVersion.startsWith("V1") || detectedVersion == "1" || detectedVersion.isBlank() -> {
                    restoreV1ArchiveData(decryptedZipBytes, restoreOptions)
                }
                else -> {
                    restoreV1ArchiveData(decryptedZipBytes, restoreOptions)
                }
            }

            restoreResult
        } catch (e: Exception) {
            e.printStackTrace()
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

    private suspend fun restoreV2ArchiveData(
        decryptedZipBytes: ByteArray,
        restoreOptions: RestoreOptions
    ): Boolean {
        var restoredNotesJson: String? = null
        var restoredSettingsJson: String? = null
        var restoredHavenJson: String? = null
        var restoredOasisJson: String? = null
        var restoredEntriesCount = 0

        try {
            // Pass 1: Extract binary files based on RestoreOptions
            ZipInputStream(ByteArrayInputStream(decryptedZipBytes)).use { zis ->
                var entry: ZipEntry? = zis.nextEntry
                while (entry != null) {
                    restoredEntriesCount++
                    when {
                        entry.name == "notes_db.json" && restoreOptions.restoreCapsuleEntries -> {
                            restoredNotesJson = zis.bufferedReader(Charsets.UTF_8).readText()
                        }
                        entry.name == "settings.json" && (restoreOptions.restoreCapsuleSettings || restoreOptions.restoreWallpapers) -> {
                            restoredSettingsJson = zis.bufferedReader(Charsets.UTF_8).readText()
                        }
                        entry.name == "haven_ai.json" && (restoreOptions.restoreHavenSettings || restoreOptions.restoreHavenHistory) -> {
                            restoredHavenJson = zis.bufferedReader(Charsets.UTF_8).readText()
                        }
                        entry.name == "oasis_browser.json" && (restoreOptions.restoreOasisSettings || restoreOptions.restoreOasisHistoryAndData) -> {
                            restoredOasisJson = zis.bufferedReader(Charsets.UTF_8).readText()
                        }
                        entry.name.startsWith("media/") && restoreOptions.restoreCapsuleEntries -> {
                            val fileName = entry.name.removePrefix("media/")
                            if (fileName.isNotBlank()) {
                                FileOutputStream(File(mediaDirectory, fileName)).use { fos -> zis.copyTo(fos) }
                            }
                        }
                        entry.name.startsWith("drawings/") && restoreOptions.restoreCapsuleEntries -> {
                            val fileName = entry.name.removePrefix("drawings/")
                            if (fileName.isNotBlank()) {
                                val drawingsDir = File(context.filesDir, "mucephei_drawings").apply { mkdirs() }
                                FileOutputStream(File(drawingsDir, fileName)).use { fos -> zis.copyTo(fos) }
                            }
                        }
                        entry.name.startsWith("audio/") && restoreOptions.restoreCapsuleEntries -> {
                            val fileName = entry.name.removePrefix("audio/")
                            if (fileName.isNotBlank()) {
                                val audioDir = File(context.filesDir, "mucephi_audio").apply { mkdirs() }
                                FileOutputStream(File(audioDir, fileName)).use { fos -> zis.copyTo(fos) }
                            }
                        }
                        entry.name.startsWith("connections/") && restoreOptions.restoreCapsuleEntries -> {
                            val fileName = entry.name.removePrefix("connections/")
                            if (fileName.isNotBlank()) {
                                val connDir = File(context.filesDir, "mucephi_connections").apply { mkdirs() }
                                FileOutputStream(File(connDir, fileName)).use { fos -> zis.copyTo(fos) }
                            }
                        }
                        entry.name.startsWith("vpn_profiles/") && restoreOptions.restoreCapsuleEntries -> {
                            val fileName = entry.name.removePrefix("vpn_profiles/")
                            if (fileName.isNotBlank()) {
                                val vpnDir = File(mediaDirectory.parentFile, "mucephi_vpn").apply { mkdirs() }
                                FileOutputStream(File(vpnDir, fileName)).use { fos -> zis.copyTo(fos) }
                            }
                        }
                        entry.name.startsWith("wallpapers/") && restoreOptions.restoreWallpapers -> {
                            val fileName = entry.name.removePrefix("wallpapers/")
                            if (fileName.isNotBlank()) {
                                val wpDir = File(context.filesDir, "wallpapers").apply { mkdirs() }
                                FileOutputStream(File(wpDir, fileName)).use { fos -> zis.copyTo(fos) }
                            }
                        }
                        entry.name.startsWith("oasis_data/") && restoreOptions.restoreOasisHistoryAndData -> {
                            val relativePath = entry.name.removePrefix("oasis_data/")
                            if (relativePath.isNotBlank()) {
                                val webViewDir = File(context.dataDir, "app_webview").apply { mkdirs() }
                                val targetFile = File(webViewDir, relativePath)
                                targetFile.parentFile?.mkdirs()
                                FileOutputStream(targetFile).use { fos -> zis.copyTo(fos) }
                            }
                        }
                    }
                    entry = zis.nextEntry
                }
            }

            if (restoredEntriesCount == 0) return false

            // Pass 2: Restore preferences and notes selectively
            restoredSettingsJson?.let { settingsJson ->
                try {
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
                                if (firstWp != null) themePrefs.wallpaperPath = firstWp.absolutePath else themePrefs.wallpaperPath = null
                            }
                            if (!themePrefs.wallpaperPath.isNullOrBlank()) {
                                android.os.Handler(android.os.Looper.getMainLooper()).post {
                                    android.widget.Toast.makeText(context, "Wallpaper restored successfully!", android.widget.Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            restoredHavenJson?.let { havenJson ->
                try {
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
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            restoredOasisJson?.let { oasisJson ->
                try {
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
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            if (restoreOptions.restoreCapsuleEntries) {
                restoredNotesJson?.let { json ->
                    try {
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
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }

            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    private suspend fun restoreV1ArchiveData(
        decryptedZipBytes: ByteArray,
        restoreOptions: RestoreOptions
    ): Boolean {
        var restoredJson: String? = null
        var restoredSettingsJson: String? = null
        var restoredEntriesCount = 0

        try {
            ZipInputStream(ByteArrayInputStream(decryptedZipBytes)).use { zis ->
                var entry: ZipEntry? = zis.nextEntry
                while (entry != null) {
                    restoredEntriesCount++
                    when {
                        entry.name == "notes_db.json" && restoreOptions.restoreCapsuleEntries -> {
                            restoredJson = zis.bufferedReader(Charsets.UTF_8).readText()
                        }
                        entry.name == "settings.json" && (restoreOptions.restoreCapsuleSettings || restoreOptions.restoreWallpapers) -> {
                            restoredSettingsJson = zis.bufferedReader(Charsets.UTF_8).readText()
                        }
                        entry.name.startsWith("media/") && restoreOptions.restoreCapsuleEntries -> {
                            val fileName = entry.name.removePrefix("media/")
                            if (fileName.isNotBlank()) {
                                FileOutputStream(File(mediaDirectory, fileName)).use { fos -> zis.copyTo(fos) }
                            }
                        }
                        entry.name.startsWith("vpn_profiles/") && restoreOptions.restoreCapsuleEntries -> {
                            val fileName = entry.name.removePrefix("vpn_profiles/")
                            if (fileName.isNotBlank()) {
                                val vpnDir = File(mediaDirectory.parentFile, "mucephi_vpn").apply { mkdirs() }
                                FileOutputStream(File(vpnDir, fileName)).use { fos -> zis.copyTo(fos) }
                            }
                        }
                        entry.name.startsWith("wallpapers/") && restoreOptions.restoreWallpapers -> {
                            val fileName = entry.name.removePrefix("wallpapers/")
                            if (fileName.isNotBlank()) {
                                val wpDir = File(context.filesDir, "wallpapers").apply { mkdirs() }
                                FileOutputStream(File(wpDir, fileName)).use { fos -> zis.copyTo(fos) }
                            }
                        }
                    }
                    entry = zis.nextEntry
                }
            }

            if (restoredEntriesCount == 0) return false

            restoredSettingsJson?.let { settingsJson ->
                try {
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
                        }

                        if (restoreOptions.restoreWallpapers) {
                            val healedWp = fixLocalFilePath(settings.wallpaperPath)
                            if (!healedWp.isNullOrBlank() && File(healedWp).exists()) {
                                themePrefs.wallpaperPath = healedWp
                            } else {
                                val wpDir = File(context.filesDir, "wallpapers")
                                val firstWp = wpDir.listFiles()?.firstOrNull { it.isFile }
                                if (firstWp != null) themePrefs.wallpaperPath = firstWp.absolutePath else themePrefs.wallpaperPath = null
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            if (restoreOptions.restoreCapsuleEntries) {
                restoredJson?.let { json ->
                    try {
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
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }

            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
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
            val fileBytes = archiveFile.readBytes()
            if (fileBytes.isEmpty()) return@withContext "UNKNOWN"

            val decryptedZipBytes = if (isZipArchive(fileBytes)) {
                fileBytes
            } else {
                if (password == null || password.isEmpty() || fileBytes.size < SALT_SIZE + IV_SIZE) {
                    return@withContext "UNKNOWN"
                }

                val salt = fileBytes.copyOfRange(0, SALT_SIZE)
                val iv = fileBytes.copyOfRange(SALT_SIZE, SALT_SIZE + IV_SIZE)
                val encryptedBytes = fileBytes.copyOfRange(SALT_SIZE + IV_SIZE, fileBytes.size)

                val secretKey = deriveKey(password, salt)
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                val spec = GCMParameterSpec(TAG_LENGTH_BITS, iv)
                cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)

                val decrypted = cipher.doFinal(encryptedBytes)
                if (!isZipArchive(decrypted)) {
                    return@withContext "UNKNOWN"
                }
                decrypted
            }

            var detectedVersion = BACKUP_VERSION_LABEL
            ZipInputStream(ByteArrayInputStream(decryptedZipBytes)).use { zis ->
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
