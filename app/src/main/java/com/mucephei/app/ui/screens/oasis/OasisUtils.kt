package com.mucephei.app.ui.screens.oasis

import android.content.Context
import android.net.Uri
import android.webkit.URLUtil
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.URL

val extractTextJs: String = """
    (function() {
        var selText = '';
        var sel = window.getSelection();
        if (sel && sel.rangeCount > 0) {
            selText = sel.getRangeAt(0).toString().trim();
        }
        if (!selText) {
            selText = sel ? sel.toString().trim() : '';
        }
        if (selText && selText.length > 0) {
            return JSON.stringify(selText);
        }
        return JSON.stringify("");
    })();
""".trimIndent()

fun isUrlInput(input: String): Boolean {
    val trimmed = input.trim()
    return trimmed.startsWith("http://") || trimmed.startsWith("https://") ||
           (trimmed.contains(".") && !trimmed.contains(" ") && !trimmed.startsWith("localhost"))
}

fun getFaviconUrl(url: String, customFaviconUrl: String? = null): String {
    if (!customFaviconUrl.isNullOrBlank()) return customFaviconUrl
    if (url.isBlank()) return ""
    val cleanUrl = if (url.startsWith("http://") || url.startsWith("https://")) url else "https://$url"
    val domain = try {
        val uri = Uri.parse(cleanUrl)
        uri.host?.takeIf { it.isNotBlank() } ?: cleanUrl
    } catch (_: Exception) {
        cleanUrl
    }
    return "https://www.google.com/s2/favicons?domain=$domain&sz=128"
}

fun parseContentDispositionFileName(header: String?): String? {
    if (header.isNullOrBlank()) return null
    try {
        if (header.contains("filename*=", ignoreCase = true)) {
            val raw = header.substringAfter("filename*=", "").substringBefore(";").trim()
            val clean = raw.removePrefix("UTF-8''").removePrefix("utf-8''").removeSurrounding("\"").removeSurrounding("'")
            val decoded = Uri.decode(clean)
            if (decoded.isNotBlank()) return File(decoded).name
        }
        if (header.contains("filename=", ignoreCase = true)) {
            val raw = header.substringAfter("filename=", "").substringBefore(";").trim()
            val clean = raw.removeSurrounding("\"").removeSurrounding("'")
            val decoded = Uri.decode(clean)
            if (decoded.isNotBlank()) return File(decoded).name
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return null
}

fun extractFileNameFromUrlPath(urlStr: String?): String? {
    if (urlStr.isNullOrBlank()) return null
    try {
        val uri = Uri.parse(urlStr)
        val path = uri.path ?: return null
        val segment = path.substringAfterLast('/')
        if (segment.isNotBlank() && segment.contains('.')) {
            val decoded = Uri.decode(segment)
            val ext = decoded.substringAfterLast('.', "").lowercase()
            if (ext.length in 1..8 && ext !in setOf("php", "html", "htm", "jsp", "asp", "aspx")) {
                return File(decoded).name
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return null
}

fun resolveBestFileName(
    url: String,
    contentDisposition: String?,
    headerContentDisposition: String?,
    redirectedUrl: String?,
    mimeType: String?
): String {
    val cdHeaderName = parseContentDispositionFileName(headerContentDisposition)
    if (!cdHeaderName.isNullOrBlank() && cdHeaderName.contains('.')) return cdHeaderName

    val listenerCdName = parseContentDispositionFileName(contentDisposition)
    if (!listenerCdName.isNullOrBlank() && listenerCdName.contains('.')) return listenerCdName

    val urlPathName = extractFileNameFromUrlPath(url)
    if (!urlPathName.isNullOrBlank()) return urlPathName

    val redirectPathName = extractFileNameFromUrlPath(redirectedUrl)
    if (!redirectPathName.isNullOrBlank()) return redirectPathName

    return URLUtil.guessFileName(redirectedUrl ?: url, headerContentDisposition ?: contentDisposition, mimeType)
}

fun parseJsString(gson: Gson, result: String?): String {
    if (result == null || result == "null") return ""
    return try {
        gson.fromJson(result, String::class.java) ?: ""
    } catch (e: Exception) {
        e.printStackTrace()
        result.removeSurrounding("\"")
            .replace("\\n", "\n")
            .replace("\\t", "\t")
            .replace("\\r", "\r")
            .replace("\\\"", "\"")
            .replace("\\\\", "\\")
            .trim()
    }
}

suspend fun saveImageFromUrl(context: Context, imageUrl: String, targetDir: File): File? = withContext(Dispatchers.IO) {
    try {
        targetDir.mkdirs()
        val fileName = "img_${System.currentTimeMillis()}.jpg"
        val destFile = File(targetDir, fileName)

        if (imageUrl.startsWith("data:image/")) {
            val base64Data = imageUrl.substringAfter(",")
            val bytes = android.util.Base64.decode(base64Data, android.util.Base64.DEFAULT)
            FileOutputStream(destFile).use { it.write(bytes) }
            if (destFile.exists() && destFile.length() > 0) return@withContext destFile
        } else if (imageUrl.startsWith("http://") || imageUrl.startsWith("https://")) {
            val connection = URL(imageUrl).openConnection() as java.net.HttpURLConnection
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            connection.inputStream.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }
            if (destFile.exists() && destFile.length() > 0) return@withContext destFile
        } else if (imageUrl.startsWith("content://") || imageUrl.startsWith("file://")) {
            val uri = Uri.parse(imageUrl)
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }
            if (destFile.exists() && destFile.length() > 0) return@withContext destFile
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    null
}
