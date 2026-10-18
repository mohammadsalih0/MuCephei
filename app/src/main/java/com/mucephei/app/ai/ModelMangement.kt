package com.mucephei.app.ai

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.edit
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException

object ModelManager {
    var activeEngine: LLMEngine? = null
}

data class LocalModelInfo(
    val name: String,
    val path: String,
    val isAppFile: Boolean,
    val sizeBytes: Long = 0L
)

object ModelUtils {
    private val SUPPORTED_EXTENSIONS = setOf("bin", "task", "litertlm", "pte", "gguf")

    fun formatFileSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
        val formatted = String.format(java.util.Locale.US, "%.2f", bytes / Math.pow(1024.0, digitGroups.toDouble()))
        return "$formatted ${units[digitGroups.coerceIn(0, units.size - 1)]}"
    }

    fun hasAllFilesAccess(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            true
        }
    }

    fun requestAllFilesAccess(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = Uri.parse("package:${context.packageName}")
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                    context.startActivity(intent)
                } catch (ex: Exception) {
                    ex.printStackTrace()
                }
            }
        }
    }

    fun getDownloadsModelsDir(): File {
        val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        return File(downloads, "Mu Cephei/Models").apply {
            if (!exists()) {
                try { mkdirs() } catch (e: Exception) { e.printStackTrace() }
            }
        }
    }

    fun getAppExternalModelsDir(context: Context): File? {
        val dir = context.getExternalFilesDir("Models")
        if (dir != null && !dir.exists()) {
            try { dir.mkdirs() } catch (e: Exception) { e.printStackTrace() }
        }
        return dir
    }

    fun scanDownloadsModels(context: Context): List<LocalModelInfo> {
        val results = mutableListOf<LocalModelInfo>()

        // 1. Scan permission-free app external directory (Android/data/<package>/files/Models)
        try {
            val appExtDir = getAppExternalModelsDir(context)
            if (appExtDir != null && appExtDir.exists() && appExtDir.isDirectory) {
                val files = appExtDir.listFiles { _, name ->
                    val ext = name.substringAfterLast('.', "").lowercase()
                    ext in SUPPORTED_EXTENSIONS
                }
                files?.forEach { file ->
                    results.add(
                        LocalModelInfo(
                            name = file.name,
                            path = file.absolutePath,
                            isAppFile = true,
                            sizeBytes = file.length()
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 2. Scan public Downloads/Mu Cephei/Models directory
        try {
            val publicDownloadsDir = getDownloadsModelsDir()
            if (publicDownloadsDir.exists() && publicDownloadsDir.isDirectory) {
                val files = publicDownloadsDir.listFiles { _, name ->
                    val ext = name.substringAfterLast('.', "").lowercase()
                    ext in SUPPORTED_EXTENSIONS
                }
                files?.forEach { file ->
                    if (results.none { it.name == file.name }) {
                        results.add(
                            LocalModelInfo(
                                name = file.name,
                                path = file.absolutePath,
                                isAppFile = false,
                                sizeBytes = file.length()
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return results
    }

    fun scanAppFilesModels(context: Context): List<LocalModelInfo> {
        val dir = context.filesDir
        if (!dir.exists() || !dir.isDirectory) return emptyList()

        val files = dir.listFiles { _, name ->
            val ext = name.substringAfterLast('.', "").lowercase()
            ext in SUPPORTED_EXTENSIONS
        } ?: return emptyList()

        return files.map { file ->
            LocalModelInfo(
                name = file.name,
                path = file.absolutePath,
                isAppFile = true,
                sizeBytes = file.length()
            )
        }
    }

    fun getSavedModels(prefs: SharedPreferences, gson: Gson = Gson()): List<LocalModelInfo> {
        val json = prefs.getString("saved_models_json", null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<LocalModelInfo?>>() {}.type
            val rawList = gson.fromJson<List<LocalModelInfo?>>(json, type) ?: return emptyList()
            rawList.filterNotNull().filter { !it.name.isNullOrBlank() && !it.path.isNullOrBlank() }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    fun saveModelInfo(prefs: SharedPreferences, info: LocalModelInfo, gson: Gson = Gson()) {
        val currentList = getSavedModels(prefs, gson).toMutableList()
        currentList.removeAll { it.path == info.path || it.name == info.name }
        currentList.add(0, info)
        prefs.edit { putString("saved_models_json", gson.toJson(currentList)) }
    }

    fun removeSavedModelInfo(prefs: SharedPreferences, path: String, gson: Gson = Gson()) {
        val currentList = getSavedModels(prefs, gson).toMutableList()
        currentList.removeAll { it.path == path || it.name == File(path).name }
        prefs.edit { putString("saved_models_json", gson.toJson(currentList)) }
    }

    fun getAllAvailableModels(context: Context, prefs: SharedPreferences, gson: Gson = Gson()): List<LocalModelInfo> {
        val appModels = scanAppFilesModels(context)
        val downloadModels = scanDownloadsModels(context)
        val savedModels = getSavedModels(prefs, gson)

        val combinedMap = mutableMapOf<String, LocalModelInfo>()

        // 1. Add app files models first
        appModels.forEach { combinedMap[it.name] = it }

        // 2. Add downloads and app external models
        downloadModels.forEach { model ->
            if (!combinedMap.containsKey(model.name)) {
                combinedMap[model.name] = model
            }
        }

        // 3. Add saved models if file exists and can be read
        savedModels.forEach { model ->
            val file = File(model.path)
            if (file.exists() && file.length() > 0) {
                if (!combinedMap.containsKey(model.name)) {
                    combinedMap[model.name] = model
                }
            }
        }

        return combinedMap.values.sortedWith(
            compareByDescending<LocalModelInfo> { it.isAppFile }
                .thenBy { it.name }
        )
    }

    fun checkExistingAppFile(context: Context, fileName: String): File? {
        val file = File(context.filesDir, fileName)
        if (file.exists() && file.length() > 0L) return file

        val extDir = getAppExternalModelsDir(context)
        if (extDir != null) {
            val extFile = File(extDir, fileName)
            if (extFile.exists() && extFile.length() > 0L) return extFile
        }

        return null
    }

    fun getFileNameFromUri(context: Context, uri: Uri): String {
        var fileName: String? = null
        if (uri.scheme == "content") {
            try {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (cursor.moveToFirst() && nameIndex >= 0) {
                        fileName = cursor.getString(nameIndex)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        if (fileName.isNullOrBlank()) {
            fileName = uri.lastPathSegment?.let { File(it).name }
        }
        val safeName = fileName.takeIf { !it.isNullOrBlank() } ?: "model_${System.currentTimeMillis()}.bin"
        return File(safeName).name
    }

    fun resolveFilePathFromUri(context: Context, uri: Uri): String? {
        if (uri.scheme == "file") return uri.path

        if (uri.scheme == "content") {
            try {
                context.contentResolver.query(uri, arrayOf(android.provider.MediaStore.MediaColumns.DATA), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idx = cursor.getColumnIndex(android.provider.MediaStore.MediaColumns.DATA)
                        if (idx >= 0) {
                            val path = cursor.getString(idx)
                            if (!path.isNullOrBlank() && File(path).exists()) {
                                return path
                            }
                        }
                    }
                }
            } catch (_: Exception) {
                // Ignore query error
            }

            val docId = try {
                if (android.provider.DocumentsContract.isDocumentUri(context, uri)) {
                    android.provider.DocumentsContract.getDocumentId(uri)
                } else null
            } catch (_: Exception) {
                null
            }

            if (docId != null) {
                if (docId.startsWith("primary:")) {
                    val relPath = docId.removePrefix("primary:")
                    val file = File(Environment.getExternalStorageDirectory(), relPath)
                    if (file.exists()) return file.absolutePath
                } else if (docId.startsWith("raw:")) {
                    val rawPath = docId.removePrefix("raw:")
                    val file = File(rawPath)
                    if (file.exists()) return file.absolutePath
                }
            }

            val pathStr = Uri.decode(uri.toString())
            if (pathStr.contains("/storage/")) {
                val idx = pathStr.indexOf("/storage/")
                val candidate = pathStr.substring(idx)
                val file = File(candidate)
                if (file.exists()) return file.absolutePath
            }
        }

        return null
    }

    fun findDirectModelFile(context: Context, uri: Uri, fileName: String): File? {
        val resolvedPath = resolveFilePathFromUri(context, uri)
        if (!resolvedPath.isNullOrBlank()) {
            val file = File(resolvedPath)
            if (file.exists()) return file
        }

        val downloadsModelsFile = File(getDownloadsModelsDir(), fileName)
        if (downloadsModelsFile.exists()) return downloadsModelsFile

        val downloadsFile = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), fileName)
        if (downloadsFile.exists()) return downloadsFile

        val appExtDir = getAppExternalModelsDir(context)
        if (appExtDir != null) {
            val appExtFile = File(appExtDir, fileName)
            if (appExtFile.exists()) return appExtFile
        }

        val rawPath = Uri.decode(uri.toString())
        if (rawPath.contains("/storage/")) {
            val idx = rawPath.indexOf("/storage/")
            val candidatePath = rawPath.substring(idx)
            val file = File(candidatePath)
            if (file.exists()) return file
        }

        return null
    }

    fun copyUriToAppFiles(context: Context, uri: Uri, fileName: String): File? {
        // First attempt: internal app storage (filesDir)
        try {
            val destFile = File(context.filesDir, fileName)
            context.contentResolver.openInputStream(uri)?.use { input ->
                java.io.FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }
            if (destFile.exists() && destFile.length() > 0L) {
                return destFile
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Second attempt: external app storage (Android/data/<package>/files/Models)
        try {
            val extDir = getAppExternalModelsDir(context)
            if (extDir != null) {
                val destFile = File(extDir, fileName)
                context.contentResolver.openInputStream(uri)?.use { input ->
                    java.io.FileOutputStream(destFile).use { output ->
                        input.copyTo(output)
                    }
                }
                if (destFile.exists() && destFile.length() > 0L) {
                    return destFile
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return null
    }
}

class ModelOperation(private val context: Context) {
    private val dynamicModelManager = DynamicModelManager(context)

    val llmInference: LlmInference?
        get() = (ModelManager.activeEngine as? MediaPipeEngine)?.llmInference

    suspend fun loadModel(targetFile: File? = null) = withContext(Dispatchers.IO) {
        val modelFile = targetFile ?: findFirstAvailableModel()
        ?: throw FileNotFoundException("No valid model file was found")
        if (!modelFile.exists() || modelFile.length() == 0L) {
            throw FileNotFoundException("Model file is non-existent or corrupt ${modelFile.absolutePath}")
        }

        dynamicModelManager.switchAndLoad(modelFile)
    }

    private fun findFirstAvailableModel(): File? {
        val extensions = setOf("bin", "task", "litertlm", "pte", "gguf")

        // 1. Check internal app files
        val appFile = context.filesDir.listFiles { _, name ->
            val ext = name.substringAfterLast('.', "").lowercase()
            ext in extensions
        }?.firstOrNull()
        if (appFile != null && appFile.length() > 0L) return appFile

        // 2. Check app external files (Android/data/<package>/files/Models)
        val appExtDir = ModelUtils.getAppExternalModelsDir(context)
        if (appExtDir != null && appExtDir.exists()) {
            val extFile = appExtDir.listFiles { _, name ->
                val ext = name.substringAfterLast('.', "").lowercase()
                ext in extensions
            }?.firstOrNull()
            if (extFile != null && extFile.length() > 0L) return extFile
        }

        // 3. Check public Downloads/Mu Cephei/Models
        try {
            val downloadsDir = ModelUtils.getDownloadsModelsDir()
            if (downloadsDir.exists() && downloadsDir.isDirectory) {
                val downloadFile = downloadsDir.listFiles { _, name ->
                    val ext = name.substringAfterLast('.', "").lowercase()
                    ext in extensions
                }?.firstOrNull()
                if (downloadFile != null && downloadFile.length() > 0L) return downloadFile
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return null
    }

    fun unloadModel() {
        dynamicModelManager.unload()
    }
}
