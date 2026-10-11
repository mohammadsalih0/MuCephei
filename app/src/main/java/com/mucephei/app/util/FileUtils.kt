package com.mucephei.app.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import com.mucephei.app.data.model.MediaItem
import com.mucephei.app.data.model.MediaItemType
import java.io.File

/**
 * Shared File Utility for securely opening documents, PDFs, images, and media attachments
 * via FileProvider across all screens in MuCephei.
 */
fun openDocumentOrMedia(context: Context, mediaItem: MediaItem) {
    try {
        val uriStr = mediaItem.uri
        val uri = if (uriStr.startsWith("content://") || uriStr.startsWith("http://") || uriStr.startsWith("https://")) {
            Uri.parse(uriStr)
        } else {
            val file = File(uriStr)
            if (file.exists()) {
                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            } else {
                Uri.parse(uriStr)
            }
        }

        val mimeType = when {
            mediaItem.type == MediaItemType.PHOTO || mediaItem.type == MediaItemType.DRAWING -> "image/*"
            uriStr.endsWith(".pdf", ignoreCase = true) -> "application/pdf"
            uriStr.endsWith(".jpg", ignoreCase = true) || uriStr.endsWith(".png", ignoreCase = true) || uriStr.endsWith(".webp", ignoreCase = true) -> "image/*"
            uriStr.endsWith(".txt", ignoreCase = true) -> "text/plain"
            uriStr.endsWith(".mp4", ignoreCase = true) || uriStr.endsWith(".mkv", ignoreCase = true) -> "video/*"
            uriStr.endsWith(".mp3", ignoreCase = true) || uriStr.endsWith(".m4a", ignoreCase = true) -> "audio/*"
            else -> context.contentResolver.getType(uri) ?: "*/*"
        }

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Open file"))
    } catch (e: Exception) {
        e.printStackTrace()
        Toast.makeText(context, "Could not open file: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
    }
}

/**
 * Save media item to device gallery or downloads directory.
 */
fun saveMediaToDevice(context: Context, mediaItem: MediaItem) {
    try {
        val sourceFile = File(mediaItem.uri)
        if (!sourceFile.exists()) {
            Toast.makeText(context, "Media file not found on device", Toast.LENGTH_SHORT).show()
            return
        }
        val targetDir = when (mediaItem.type) {
            MediaItemType.PHOTO, MediaItemType.DRAWING -> android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_PICTURES)
            MediaItemType.VIDEO -> android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_MOVIES)
            else -> android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
        }
        val muCepheiDir = File(targetDir, "Mu Cephei").apply { mkdirs() }
        val destFile = File(muCepheiDir, sourceFile.name)
        sourceFile.copyTo(destFile, overwrite = true)

        val intent = Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE).apply {
            data = Uri.fromFile(destFile)
        }
        context.sendBroadcast(intent)

        Toast.makeText(context, "Saved to ${destFile.absolutePath}", Toast.LENGTH_LONG).show()
    } catch (e: Exception) {
        e.printStackTrace()
        Toast.makeText(context, "Failed to save media: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
    }
}
