package com.mucephei.app.ai

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class WhisperCppEngine(@Suppress("UNUSED_PARAMETER") private val context: Context) : LLMEngine {
    private var contextPtr: Long = 0L
    private var isLoaded = false
    private var modelPath: String? = null

    override suspend fun loadModel(modelFile: File) = withContext(Dispatchers.IO) {
        unloadModel()
        if ((!modelFile.exists()) || (modelFile.length() == 0L)) {
            throw IllegalArgumentException("Whisper model file is missing or empty: ${modelFile.name}")
        }
        modelPath = modelFile.absolutePath
        try {
            System.loadLibrary("whisper_jni")
            contextPtr = WhisperJni.nativeInit(modelFile.absolutePath)
            if (contextPtr == 0L) {
                throw RuntimeException("Failed to initialize Whisper context from ${modelFile.absolutePath}")
            }
            isLoaded = true
        } catch (e: UnsatisfiedLinkError) {
            isLoaded = true
            throw RuntimeException("Native library whisper_jni not found or failed to load: ${e.localizedMessage}", e)
        }
    }

    override suspend fun generateResponse(prompt: String, onTokenGenerated: (String, Boolean) -> Unit) {
        onTokenGenerated("WhisperEngine is specialized for audio transcription (transcribeAudio method).", true)
    }

    suspend fun transcribeAudio(samples: FloatArray): String = withContext(Dispatchers.IO) {
        if ((!isLoaded) || (contextPtr == 0L)) {
            throw IllegalStateException("Whisper model is not loaded.")
        }
        val text = WhisperJni.nativeFullTranscribe(contextPtr, samples, 4)
        text
    }

    override fun unloadModel() {
        if (contextPtr != 0L) {
            try {
                WhisperJni.nativeFree(contextPtr)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            contextPtr = 0L
        }
        isLoaded = false
        modelPath = null
    }
}

object WhisperJni {
    init {
        try {
            System.loadLibrary("whisper_jni")
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    external fun nativeInit(modelPath: String): Long
    external fun nativeFree(contextPtr: Long)
    external fun nativeFullTranscribe(contextPtr: Long, samples: FloatArray, numThreads: Int): String
}
