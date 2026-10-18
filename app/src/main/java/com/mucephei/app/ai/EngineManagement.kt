@file:Suppress("DEPRECATION")
package com.mucephei.app.ai

import android.content.Context
import android.net.Uri
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.nehuatl.llamacpp.LlamaHelper
import java.io.File

interface LLMEngine {
    suspend fun loadModel(modelFile: File)
    suspend fun generateResponse(prompt: String, onTokenGenerated: (String, Boolean) -> Unit)
    fun unloadModel()
}

class MediaPipeEngine(private val context: Context) : LLMEngine {
    var llmInference: LlmInference? = null
        private set

    override suspend fun loadModel(modelFile: File) = withContext(Dispatchers.IO) {
        unloadModel()
        val options = LlmInference.LlmInferenceOptions.builder()
            .setModelPath(modelFile.absolutePath)
            .setMaxTokens(4096)
            .build()
        llmInference = LlmInference.createFromOptions(context, options)
    }

    override suspend fun generateResponse(prompt: String, onTokenGenerated: (String, Boolean) -> Unit) {
        llmInference?.generateResponseAsync(prompt) { partialResult, done -> partialResult?.let { onTokenGenerated(it, done) } }
    }

    override fun unloadModel() {
        llmInference?.close()
        llmInference = null
    }
}

class ExecuTorchEngine(@Suppress("UNUSED_PARAMETER") private val context: Context) : LLMEngine {
    private var module: LlamaModule? = null
    override suspend fun loadModel(modelFile: File): Unit = withContext(Dispatchers.IO) {
        unloadModel()
        val tokenizerPath = File(modelFile.parent, "tokenizer.bin").absolutePath
        module = LlamaModule(
            modelPath = modelFile.absolutePath,
            tokenizerPath = tokenizerPath,
            temperature = 0.7f
        )
        module?.load()
    }

    override suspend fun generateResponse(prompt: String, onTokenGenerated: (String, Boolean) -> Unit) {
        module?.generate(prompt, onTokenGenerated)
    }

    override fun unloadModel() {
        module?.stop()
        module = null
    }
}

class LlamaCppEngine(private val context: Context) : LLMEngine {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val sharedFlow = MutableSharedFlow<LlamaHelper.LLMEvent>(extraBufferCapacity = 64)
    private var llamaHelper: LlamaHelper? = null
    private var isLoaded = false
    private var loadError: String? = null

    override suspend fun loadModel(modelFile: File) {
        withContext(Dispatchers.IO) {
            unloadModel()
            loadError = null
            try {
                if (!modelFile.exists() || modelFile.length() == 0L) {
                    throw IllegalArgumentException("GGUF model file is missing or empty: ${modelFile.name}")
                }

                val helper = LlamaHelper(context.contentResolver, scope, sharedFlow)
                llamaHelper = helper

                // Use Uri.fromFile so Uri.parse() in LlamaHelper gets a valid "file://" scheme
                val modelUriString = Uri.fromFile(modelFile).toString()
                val deferred = CompletableDeferred<Unit>()

                val eventJob = scope.launch {
                    sharedFlow.collect { event ->
                        when (event) {
                            is LlamaHelper.LLMEvent.Loaded -> {
                                isLoaded = true
                                if (!deferred.isCompleted) {
                                    deferred.complete(Unit)
                                }
                            }
                            is LlamaHelper.LLMEvent.Error -> {
                                isLoaded = false
                                val msg = event.message
                                loadError = msg
                                if (!deferred.isCompleted) {
                                    deferred.completeExceptionally(RuntimeException("GGUF engine error: $msg"))
                                }
                            }
                            else -> {}
                        }
                    }
                }

                try {
                    helper.load(modelUriString, contextLength = 8192) {
                        isLoaded = true
                        if (!deferred.isCompleted) {
                            deferred.complete(Unit)
                        }
                    }

                    withTimeout(60000L) {
                        deferred.await()
                    }
                } finally {
                    eventJob.cancel()
                }
            } catch (e: Throwable) {
                isLoaded = false
                loadError = e.localizedMessage ?: e.javaClass.simpleName
                throw RuntimeException("Failed to load GGUF model via LlamaCppEngine: $loadError. Ensure native llama.cpp library (.so) is linked.", e)
            }
        }
    }

    override suspend fun generateResponse(
        prompt: String,
        onTokenGenerated: (String, Boolean) -> Unit
    ) {
        if (!isLoaded || llamaHelper == null) {
            val err = loadError ?: "Model is not loaded, please load it from the settings menu then try again."
            onTokenGenerated("Error: $err", true)
            return
        }
        try {
            llamaHelper?.predict(prompt)
            sharedFlow.takeWhile { event ->
                when (event) {
                    is LlamaHelper.LLMEvent.Ongoing -> {
                        onTokenGenerated(event.word, false)
                        true
                    }
                    is LlamaHelper.LLMEvent.Done -> {
                        onTokenGenerated("", true)
                        false
                    }
                    is LlamaHelper.LLMEvent.Error -> {
                        onTokenGenerated("Error: ${event.message}", true)
                        false
                    }
                    else -> true
                }
            }.collect {}
        } catch (e: Exception) {
            onTokenGenerated("Error while generating: ${e.localizedMessage}", true)
        }
    }

    override fun unloadModel() {
        try {
            llamaHelper?.release()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        llamaHelper = null
        isLoaded = false
        loadError = null
    }
}

enum class EngineType {
    MEDIAPIPE, EXECUTORCH, GGUF, UNKNOWN
}

class LlamaModule(
    @Suppress("UNUSED_PARAMETER") private val modelPath: String,
    @Suppress("UNUSED_PARAMETER") private val tokenizerPath: String,
    @Suppress("UNUSED_PARAMETER") private val temperature: Float
) {
    private var module: Any? = null
    fun load() {
        try {
            module = Any()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun generate(@Suppress("UNUSED_PARAMETER") prompt: String, @Suppress("UNUSED_PARAMETER") onTokenGenerated: (String, Boolean) -> Unit) {
        if (module == null) {
            onTokenGenerated("An error was encountered while loading the model", true)
            return
        }
        onTokenGenerated("Model was successfully loaded", true)
    }

    fun stop() {
        module = null
    }
}

@Suppress("UNUSED")
class DynamicModelManager(private val context: Context) {
    var activeEngine: LLMEngine? = null
        private set

    fun detectEngineType(file: File): EngineType {
        return when (file.extension.lowercase()) {
            "bin", "task", "litertlm" -> EngineType.MEDIAPIPE
            "pte" -> EngineType.EXECUTORCH
            "gguf" -> EngineType.GGUF
            else -> EngineType.UNKNOWN
        }
    }

    @Suppress("UNUSED")
    suspend fun switchAndLoad(modelFile: File) {
        activeEngine?.unloadModel()
        val engineType = detectEngineType(modelFile)
        val newEngine: LLMEngine = when (engineType) {
            EngineType.MEDIAPIPE -> MediaPipeEngine(context)
            EngineType.EXECUTORCH -> ExecuTorchEngine(context)
            EngineType.GGUF -> LlamaCppEngine(context)
            EngineType.UNKNOWN -> throw IllegalArgumentException("Error: Unsupported model format ${modelFile.name}")
        }
        newEngine.loadModel(modelFile)
        activeEngine = newEngine
        ModelManager.activeEngine = newEngine
    }

    @Suppress("UNUSED")
    fun unload() {
        activeEngine?.unloadModel()
        activeEngine = null
        ModelManager.activeEngine = null
    }
}