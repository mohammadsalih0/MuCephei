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
    val selectedPreset: String,
    val customThemeJson: String?,
    val wallpaperPath: String?,
    val blurRadius: Float,
    val wallpaperAlpha: Float,
    val autoNightModeEnabled: Boolean,
    val fontScale: Float,
    val isGridView: Boolean,
    val vectorOpacity: Float,
    val hideVectors: Boolean,
    val textTransparencyEnabled: Boolean,
    val textTransparencyAlpha: Float,
    val glassifyEnabled: Boolean,
    val lastHostIp: String,
    val customRoomCode: String,
    val capsuleHavenSensitivity: Float?,
    val oasisSensitivity: Float?
)

enum class RestoreMode { ALL, SETTINGS_ONLY, WALLPAPER_ONLY }

/**
 * Backup & Restore Engine supporting AES-256-GCM Encrypted and Unencrypted .mucephi File Archives.
 *
 * BACKUP SCHEMA VERSION: V2
 * Packed Data Types & Structures in V2 Archives:
 * 1. `version.txt`: UTF-8 Plain text version string ("V2").
 * 2. `notes_db.json`: JSON array of Room database `NoteEntity` records (including NoteLocation).
 * 3. `settings.json`: AppSettings JSON preferences (Theme preferences & Capsule/Haven/Oasis swipe sensitivities).
 * 4. `haven_ai.json`: Haven AI preferences, chat history, and context window.
 * 5. `oasis_browser.json`: Oasis browser preferences (tabs, history, bookmarks, download dir, custom extensions).
 * 6. `media/`: Binary attachments (Photos, Audio, Video, Sketches, Documents) from `mucephei_media`.
 * 7. `vpn_profiles/`: Saved OpenVPN configuration profiles (`.ovpn` files) from `mucephi_vpn`.
 * 8. `wallpapers/`: Saved custom background wallpaper images from `filesDir/wallpapers`.
 * 9. `oasis_data/`: Oasis WebView persistent storage (Local Storage, IndexedDB, WebSQL databases, cookies) excluding cache.
 * 10. Encryption: Optional AES-256-GCM with PBKDF2WithHmacSHA256 (10,000 iterations, 16-byte salt, 12-byte IV, 128-bit tag).
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
     * Exports all notes, app settings, Haven AI state, Oasis browser state, custom wallpapers, media blobs, VPN profiles, and WebView persistent data into a .mucephi archive.
     */
    suspend fun exportArchive(destinationFile: File, password: CharArray?, encrypt: Boolean = true): Boolean = withContext(Dispatchers.IO) {
        try {
            val notes = noteDao.getNotesModifiedSince(sinceTimestamp = 0L)
            val jsonPayload = gson.toJson(notes)

            val swipePrefs = context.getSharedPreferences("app_swipe_settings", Context.MODE_PRIVATE)
            val capsuleHavenSen = swipePrefs.getFloat("capsule_haven_sensitivity", 15f)
            val oasisSen = swipePrefs.getFloat("oasis_sensitivity", 15f)

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
     * Restores archive with support for selective restore modes (ALL, SETTINGS_ONLY, WALLPAPER_ONLY).
     */
    suspend fun restoreArchive(
        archiveFile: File,
        password: CharArray?,
        encrypted: Boolean = true,
        restoreMode: RestoreMode = RestoreMode.ALL
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
                    restoreV2ArchiveData(decryptedZipBytes, restoreMode)
                }
                detectedVersion.startsWith("V1") || detectedVersion == "1" || detectedVersion.isBlank() -> {
                    restoreV1ArchiveData(decryptedZipBytes, restoreMode)
                }
                else -> {
                    restoreV1ArchiveData(decryptedZipBytes, restoreMode)
                }
            }

            restoreResult
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private suspend fun restoreV2ArchiveData(
        decryptedZipBytes: ByteArray,
        restoreMode: RestoreMode
    ): Boolean {
        var restoredNotesJson: String? = null
        var restoredSettingsJson: String? = null
        var restoredHavenJson: String? = null
        var restoredOasisJson: String? = null
        var restoredEntriesCount = 0

        try {
            ZipInputStream(ByteArrayInputStream(decryptedZipBytes)).use { zis ->
                var entry: ZipEntry? = zis.nextEntry
                while (entry != null) {
                    restoredEntriesCount++
                    when {
                        entry.name == "notes_db.json" && (restoreMode == RestoreMode.ALL) -> {
                            restoredNotesJson = zis.bufferedReader(Charsets.UTF_8).readText()
                        }
                        entry.name == "settings.json" && (restoreMode == RestoreMode.ALL || restoreMode == RestoreMode.SETTINGS_ONLY) -> {
                            restoredSettingsJson = zis.bufferedReader(Charsets.UTF_8).readText()
                        }
                        entry.name == "haven_ai.json" && (restoreMode == RestoreMode.ALL || restoreMode == RestoreMode.SETTINGS_ONLY) -> {
                            restoredHavenJson = zis.bufferedReader(Charsets.UTF_8).readText()
                        }
                        entry.name == "oasis_browser.json" && (restoreMode == RestoreMode.ALL || restoreMode == RestoreMode.SETTINGS_ONLY) -> {
                            restoredOasisJson = zis.bufferedReader(Charsets.UTF_8).readText()
                        }
                        entry.name.startsWith("media/") && (restoreMode == RestoreMode.ALL) -> {
                            val fileName = entry.name.removePrefix("media/")
                            if (fileName.isNotBlank()) {
                                val targetMediaFile = File(mediaDirectory, fileName)
                                FileOutputStream(targetMediaFile).use { fos ->
                                    zis.copyTo(fos)
                                }
                            }
                        }
                        entry.name.startsWith("vpn_profiles/") && (restoreMode == RestoreMode.ALL) -> {
                            val fileName = entry.name.removePrefix("vpn_profiles/")
                            if (fileName.isNotBlank()) {
                                val vpnDir = File(mediaDirectory.parentFile, "mucephi_vpn").apply { mkdirs() }
                                val targetVpnFile = File(vpnDir, fileName)
                                FileOutputStream(targetVpnFile).use { fos ->
                                    zis.copyTo(fos)
                                }
                            }
                        }
                        entry.name.startsWith("wallpapers/") && (restoreMode == RestoreMode.ALL || restoreMode == RestoreMode.WALLPAPER_ONLY) -> {
                            val fileName = entry.name.removePrefix("wallpapers/")
                            if (fileName.isNotBlank()) {
                                val wpDir = File(context.filesDir, "wallpapers").apply { mkdirs() }
                                val targetWpFile = File(wpDir, fileName)
                                FileOutputStream(targetWpFile).use { fos ->
                                    zis.copyTo(fos)
                                }
                                if (targetWpFile.exists() && targetWpFile.length() > 0) {
                                    themePrefs.wallpaperPath = targetWpFile.absolutePath
                                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                                        android.widget.Toast.makeText(context, "Wallpaper restored! Please restart the app for it to take effect.", android.widget.Toast.LENGTH_LONG).show()
                                    }
                                }
                            }
                        }
                        entry.name.startsWith("oasis_data/") && (restoreMode == RestoreMode.ALL) -> {
                            val relativePath = entry.name.removePrefix("oasis_data/")
                            if (relativePath.isNotBlank()) {
                                val webViewDir = File(context.dataDir, "app_webview").apply { mkdirs() }
                                val targetFile = File(webViewDir, relativePath)
                                targetFile.parentFile?.mkdirs()
                                FileOutputStream(targetFile).use { fos ->
                                    zis.copyTo(fos)
                                }
                            }
                        }
                    }
                    entry = zis.nextEntry
                }
            }

            if (restoredEntriesCount == 0) {
                return false
            }

            var hasAnySuccessfulRestoration = false

            // Restore settings
            restoredSettingsJson?.let { settingsJson ->
                try {
                    val settings = gson.fromJson(settingsJson, AppSettings::class.java)
                    if (settings != null) {
                        themePrefs.selectedPreset = try { com.mucephei.app.data.model.ThemePreset.valueOf(settings.selectedPreset) } catch (e: Exception) { com.mucephei.app.data.model.ThemePreset.SUNSET_WARMTH }
                        if (settings.customThemeJson != null) {
                            themePrefs.customTheme = gson.fromJson(settings.customThemeJson, com.mucephei.app.data.model.NoteTheme::class.java)
                        } else {
                            themePrefs.customTheme = null
                        }
                        if (restoreMode == RestoreMode.ALL) {
                            themePrefs.wallpaperPath = settings.wallpaperPath
                        }
                        themePrefs.blurRadius = settings.blurRadius
                        themePrefs.wallpaperAlpha = settings.wallpaperAlpha
                        themePrefs.autoNightModeEnabled = settings.autoNightModeEnabled
                        themePrefs.fontScale = settings.fontScale
                        themePrefs.isGridView = settings.isGridView
                        themePrefs.vectorOpacity = settings.vectorOpacity
                        themePrefs.hideVectors = settings.hideVectors
                        themePrefs.textTransparencyEnabled = settings.textTransparencyEnabled
                        themePrefs.textTransparencyAlpha = settings.textTransparencyAlpha
                        themePrefs.glassifyEnabled = settings.glassifyEnabled
                        themePrefs.lastHostIp = settings.lastHostIp
                        themePrefs.customRoomCode = settings.customRoomCode

                        val swipePrefs = context.getSharedPreferences("app_swipe_settings", Context.MODE_PRIVATE)
                        swipePrefs.edit()
                            .putFloat("capsule_haven_sensitivity", settings.capsuleHavenSensitivity ?: 15f)
                            .putFloat("oasis_sensitivity", settings.oasisSensitivity ?: 15f)
                            .apply()

                        hasAnySuccessfulRestoration = true
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            // Restore Haven AI prefs (handling Gson Double number deserialization properly)
            restoredHavenJson?.let { havenJson ->
                try {
                    val type = object : TypeToken<Map<String, Any?>>() {}.type
                    val map = gson.fromJson<Map<String, Any?>>(havenJson, type)
                    val havenPrefs = context.getSharedPreferences("haven_ai_prefs", Context.MODE_PRIVATE)
                    val editor = havenPrefs.edit()
                    editor.clear()
                    map?.forEach { (k, v) ->
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
                        }
                    }
                    editor.apply()
                    hasAnySuccessfulRestoration = true
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            // Restore Oasis browser prefs (handling Gson Double number deserialization properly)
            restoredOasisJson?.let { oasisJson ->
                try {
                    val type = object : TypeToken<Map<String, Any?>>() {}.type
                    val map = gson.fromJson<Map<String, Any?>>(oasisJson, type)
                    val oasisPrefs = context.getSharedPreferences("oasis_browser_prefs", Context.MODE_PRIVATE)
                    val editor = oasisPrefs.edit()
                    editor.clear()
                    map?.forEach { (k, v) ->
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
                        }
                    }
                    editor.apply()
                    hasAnySuccessfulRestoration = true
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            // Restore notes
            if (restoreMode == RestoreMode.ALL) {
                restoredNotesJson?.let { json ->
                    try {
                        val restoredEntities = gson.fromJson(json, Array<NoteEntity>::class.java).toList()
                        restoredEntities.forEach { entity ->
                            noteDao.reconcileLwwDelta(entity)
                        }
                        hasAnySuccessfulRestoration = true
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }

            if (restoreMode == RestoreMode.WALLPAPER_ONLY || restoredEntriesCount > 0) {
                hasAnySuccessfulRestoration = true
            }

            return hasAnySuccessfulRestoration
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    /**
     * V1 Version-Specific Restore Handler:
     * Unpacks and restores V1/V1.1 data types for backward compatibility.
     */
    private suspend fun restoreV1ArchiveData(
        decryptedZipBytes: ByteArray,
        restoreMode: RestoreMode
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
                        entry.name == "notes_db.json" && (restoreMode == RestoreMode.ALL) -> {
                            restoredJson = zis.bufferedReader(Charsets.UTF_8).readText()
                        }
                        entry.name == "settings.json" && (restoreMode == RestoreMode.ALL || restoreMode == RestoreMode.SETTINGS_ONLY) -> {
                            restoredSettingsJson = zis.bufferedReader(Charsets.UTF_8).readText()
                        }
                        entry.name.startsWith("media/") && (restoreMode == RestoreMode.ALL) -> {
                            val fileName = entry.name.removePrefix("media/")
                            if (fileName.isNotBlank()) {
                                val targetMediaFile = File(mediaDirectory, fileName)
                                FileOutputStream(targetMediaFile).use { fos ->
                                    zis.copyTo(fos)
                                }
                            }
                        }
                        entry.name.startsWith("vpn_profiles/") && (restoreMode == RestoreMode.ALL) -> {
                            val fileName = entry.name.removePrefix("vpn_profiles/")
                            if (fileName.isNotBlank()) {
                                val vpnDir = File(mediaDirectory.parentFile, "mucephi_vpn").apply { mkdirs() }
                                val targetVpnFile = File(vpnDir, fileName)
                                FileOutputStream(targetVpnFile).use { fos ->
                                    zis.copyTo(fos)
                                }
                            }
                        }
                        entry.name.startsWith("wallpapers/") && (restoreMode == RestoreMode.ALL || restoreMode == RestoreMode.WALLPAPER_ONLY) -> {
                            val fileName = entry.name.removePrefix("wallpapers/")
                            if (fileName.isNotBlank()) {
                                val wpDir = File(context.filesDir, "wallpapers").apply { mkdirs() }
                                val targetWpFile = File(wpDir, fileName)
                                FileOutputStream(targetWpFile).use { fos ->
                                    zis.copyTo(fos)
                                }
                                if (targetWpFile.exists() && targetWpFile.length() > 0) {
                                    themePrefs.wallpaperPath = targetWpFile.absolutePath
                                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                                        android.widget.Toast.makeText(context, "Wallpaper restored! Please restart the app for it to take effect.", android.widget.Toast.LENGTH_LONG).show()
                                    }
                                }
                            }
                        }
                    }
                    entry = zis.nextEntry
                }
            }

            if (restoredEntriesCount == 0) {
                return false
            }

            var hasAnySuccessfulRestoration = false

            restoredSettingsJson?.let { settingsJson ->
                try {
                    val settings = gson.fromJson(settingsJson, AppSettings::class.java)
                    if (settings != null) {
                        themePrefs.selectedPreset = try { com.mucephei.app.data.model.ThemePreset.valueOf(settings.selectedPreset) } catch (e: Exception) { com.mucephei.app.data.model.ThemePreset.SUNSET_WARMTH }
                        if (settings.customThemeJson != null) {
                            themePrefs.customTheme = gson.fromJson(settings.customThemeJson, com.mucephei.app.data.model.NoteTheme::class.java)
                        } else {
                            themePrefs.customTheme = null
                        }
                        if (restoreMode == RestoreMode.ALL) {
                            themePrefs.wallpaperPath = settings.wallpaperPath
                        }
                        themePrefs.blurRadius = settings.blurRadius
                        themePrefs.wallpaperAlpha = settings.wallpaperAlpha
                        themePrefs.autoNightModeEnabled = settings.autoNightModeEnabled
                        themePrefs.fontScale = settings.fontScale
                        themePrefs.isGridView = settings.isGridView
                        themePrefs.vectorOpacity = settings.vectorOpacity
                        themePrefs.hideVectors = settings.hideVectors
                        themePrefs.textTransparencyEnabled = settings.textTransparencyEnabled
                        themePrefs.textTransparencyAlpha = settings.textTransparencyAlpha
                        themePrefs.glassifyEnabled = settings.glassifyEnabled
                        themePrefs.lastHostIp = settings.lastHostIp
                        themePrefs.customRoomCode = settings.customRoomCode
                        hasAnySuccessfulRestoration = true
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            if (restoreMode == RestoreMode.ALL) {
                restoredJson?.let { json ->
                    try {
                        val restoredEntities = gson.fromJson(json, Array<NoteEntity>::class.java).toList()
                        restoredEntities.forEach { entity ->
                            noteDao.reconcileLwwDelta(entity)
                        }
                        hasAnySuccessfulRestoration = true
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }

            if (restoreMode == RestoreMode.WALLPAPER_ONLY || restoredEntriesCount > 0) {
                hasAnySuccessfulRestoration = true
            }

            return hasAnySuccessfulRestoration
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    suspend fun restoreEncryptedArchive(archiveFile: File, password: CharArray): Boolean {
        return restoreArchive(archiveFile, password, true, RestoreMode.ALL)
    }

    /**
     * Inspects and returns the version label from an archive (e.g., "V2").
     */
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

            ZipInputStream(ByteArrayInputStream(decryptedZipBytes)).use { zis ->
                var entry: ZipEntry? = zis.nextEntry
                while (entry != null) {
                    if (entry.name == VERSION_FILE_NAME || entry.name == "version.txt" || entry.name == "label.txt") {
                        return@withContext zis.bufferedReader(Charsets.UTF_8).readText().trim()
                    }
                    entry = zis.nextEntry
                }
            }
            "V1"
        } catch (e: Exception) {
            "UNKNOWN"
        }
    }

    private fun deriveKey(password: CharArray, salt: ByteArray): SecretKey {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(password, salt, ITERATIONS, KEY_SIZE)
        val keyBytes = factory.generateSecret(spec).encoded
        return SecretKeySpec(keyBytes, "AES")
    }
}
