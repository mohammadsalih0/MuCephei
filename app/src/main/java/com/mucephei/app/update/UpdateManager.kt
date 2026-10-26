package com.mucephei.app.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

object UpdateManager {
    private const val TAG = "UpdateManager"
    private const val VERSION_JSON_URL = "https://raw.githubusercontent.com/mohammadsalih0/MuCephei/main/version.json"
    private const val UPDATES_DIR = "updates"

    fun cleanupOldApks(context: Context) {
        try {
            val updatesDir = File(context.cacheDir, UPDATES_DIR)
            if (updatesDir.exists() && updatesDir.isDirectory) {
                updatesDir.listFiles()?.forEach { file ->
                    if (file.name.endsWith(".apk")) {
                        file.delete()
                        Log.d(TAG, "Cleaned up old update APK: ${file.name}")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning up old APKs", e)
        }
    }

    suspend fun checkForUpdates(context: Context): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            val currentVersionCode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                packageInfo.longVersionCode.toInt()
            } else {
                @Suppress("DEPRECATION")
                packageInfo.versionCode
            }

            val url = URL(VERSION_JSON_URL)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10000
                readTimeout = 10000
                setRequestProperty("User-Agent", "MuCephei-Android-App")
            }

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val jsonString = connection.inputStream.bufferedReader().use { it.readText() }
                val gson = Gson()
                val updateInfo = gson.fromJson(jsonString, UpdateInfo::class.java)
                if ((updateInfo != null) && (updateInfo.versionCode > currentVersionCode)) {
                    return@withContext updateInfo
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to check for updates", e)
        }
        return@withContext null
    }

    suspend fun downloadAndVerifyApk(
        context: Context,
        updateInfo: UpdateInfo,
        onStateChanged: (DownloadState) -> Unit,
    ): File? = withContext(Dispatchers.IO) {
        try {
            onStateChanged(DownloadState.Downloading(0f))
            val updatesDir = File(context.cacheDir, UPDATES_DIR)
            if (!updatesDir.exists()) {
                updatesDir.mkdirs()
            }

            val apkFile = File(updatesDir, "MuCephei-${updateInfo.versionName}.apk")
            if (apkFile.exists()) {
                apkFile.delete()
            }

            val url = URL(updateInfo.downloadUrl)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 15000
                readTimeout = 30000
            }

            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                onStateChanged(DownloadState.Error("Server returned HTTP ${connection.responseCode}"))
                return@withContext null
            }

            val fileLength = connection.contentLength.toLong()
            connection.inputStream.use { input ->
                FileOutputStream(apkFile).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesDownloaded: Long = 0
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        bytesDownloaded += bytesRead
                        if (fileLength > 0) {
                            val progress = bytesDownloaded.toFloat() / fileLength.toFloat()
                            onStateChanged(DownloadState.Downloading(progress))
                        }
                    }
                    output.flush()
                }
            }

            onStateChanged(DownloadState.Verifying)

            // Verify SHA-256 if provided in updateInfo
            if (!updateInfo.sha256.isNullOrBlank()) {
                val calculatedHash = computeSha256(apkFile)
                if (!calculatedHash.equals(updateInfo.sha256, ignoreCase = true)) {
                    Log.w(TAG, "SHA-256 mismatch! Expected: ${updateInfo.sha256}, Got: $calculatedHash")
                    apkFile.delete()
                    onStateChanged(DownloadState.CorruptHashMismatch)
                    return@withContext null
                }
            }

            onStateChanged(DownloadState.ReadyToInstall(apkFile))
            return@withContext apkFile
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading or verifying APK", e)
            onStateChanged(DownloadState.Error(e.localizedMessage ?: "Unknown error"))
            return@withContext null
        }
    }

    private fun computeSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { fis ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (fis.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun installApk(context: Context, file: File) {
        try {
            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to install APK", e)
        }
    }

    fun openReleasePage(context: Context, url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, url.toUri()).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open release page", e)
        }
    }
}
