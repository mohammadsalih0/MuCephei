@file:Suppress("DEPRECATION")
package com.mucephei.app.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.mucephei.app.ai.LocalModelInfo
import com.mucephei.app.ai.ModelManager
import com.mucephei.app.ai.ModelOperation
import com.mucephei.app.ai.ModelUtils
import com.mucephei.app.ai.SuggestionGenerator
import com.mucephei.app.data.model.Note
import com.mucephei.app.data.repository.NoteRepository
import com.mucephei.app.theme.HandwritingTypography
import com.mucephei.app.ui.components.MarkdownText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import java.util.UUID

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val isUser: Boolean,
    val text: String,
    val suggestions: List<String> = emptyList(),
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Structured request models for accepting tasks, inputs, and prompts from other composables.
 */
sealed interface HavenRequest {
    val promptText: String
    val displayTitle: String? get() = null

    data class TextPrompt(
        val prompt: String
    ) : HavenRequest {
        override val promptText: String get() = prompt
    }

    data class Summarize(
        val content: String,
        val title: String? = null
    ) : HavenRequest {
        override val displayTitle: String? get() = title
        override val promptText: String
            get() = if (!title.isNullOrBlank()) {
                "Please summarize the following document/note titled '$title':\n\n$content"
            } else {
                "Please summarize the following text:\n\n$content"
            }
    }

    data class Explain(
        val content: String
    ) : HavenRequest {
        override val promptText: String
            get() = "Please explain the following text in simple, clear terms:\n\n$content"
    }

    data class Define(
        val term: String
    ) : HavenRequest {
        override val promptText: String
            get() = "Please define and explain the term or concept: '$term'"
    }

    data class AnalyzeNote(
        val title: String,
        val bodyText: String
    ) : HavenRequest {
        override val displayTitle: String get() = title
        override val promptText: String
            get() = "Here is a note titled '$title':\n\n$bodyText\n\nPlease analyze this note, extract key takeaways, and suggest actionable next steps or improvements."
    }

    data class CustomAction(
        val actionName: String,
        val payload: String
    ) : HavenRequest {
        override val promptText: String
            get() = "Task: $actionName\n\nContent:\n$payload"
    }
}

data class TweakOption(
    val promptText: String,
    val label: String,
    val keyword: String
)

/**
 * Haven Screen: Local AI conversation haven.
 * Follows active theme parameters, avoids top/bottom bars from Capsule,
 * uses maximum available cores by default, uses existing AI implementations in com/mucephei/app/ai,
 * respects system insets, and includes natural language settings with guideline prompt support.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Haven(
    pagerState: androidx.compose.foundation.pager.PagerState,
    repository: NoteRepository,
    incomingRequest: HavenRequest? = null,
    incomingPrompt: String? = null,
    onPromptConsumed: () -> Unit = {},
    onSearchWithOasis: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    val maxCores = remember { Runtime.getRuntime().availableProcessors() }
    val prefs = remember { context.getSharedPreferences("haven_ai_prefs", Context.MODE_PRIVATE) }
    val gson = remember { Gson() }

    var selectedModelPath by remember { mutableStateOf(prefs.getString("model_path", null)) }
    var availableModels by remember { mutableStateOf(ModelUtils.getAllAvailableModels(context, prefs, gson)) }
    var selectedPreset by remember { mutableStateOf(prefs.getString("ai_preset", "AUTO") ?: "AUTO") }
    var cpuCores by remember { mutableFloatStateOf(prefs.getFloat("cpu_cores", maxCores.toFloat())) }
    var creativityLevel by remember { mutableFloatStateOf(prefs.getFloat("temperature", 0.7f)) }
    var maxAnswerLength by remember {
        val saved = prefs.getFloat("max_tokens", 4096f)
        mutableFloatStateOf(if (saved < 64f) 4096f else saved)
    }
    var topP by remember { mutableFloatStateOf(prefs.getFloat("top_p", 0.9f)) }
    var topK by remember { mutableFloatStateOf(prefs.getFloat("top_k", 40f)) }
    var repeatPenalty by remember { mutableFloatStateOf(prefs.getFloat("repeat_penalty", 1.1f)) }
    var contextLength by remember { mutableFloatStateOf(prefs.getFloat("context_length", 4096f)) }
    var guidelinePrompt by remember { mutableStateOf(prefs.getString("guideline_prompt", "") ?: "") }
    var showAdvancedSettings by remember { mutableStateOf(prefs.getBoolean("show_advanced", false)) }
    var showWordCountDialog by remember { mutableStateOf(false) }

    val wordCountTweaks = remember {
        listOf(
            TweakOption("Answer in 50 words or less.", "50 Words", "50 words"),
            TweakOption("Answer in 100 words or less.", "100 Words", "100 words"),
            TweakOption("Answer in 200 words or less.", "200 Words", "200 words")
        )
    }

    val toneTweaks = remember {
        listOf(
            TweakOption("Use a rigorous scientific, objective, and empirical tone.", "Scientific", "scientific"),
            TweakOption("Use an empathetic, warm, and emotional tone.", "Emotional", "emotional"),
            TweakOption("Use a deep, thoughtful, and reflectional tone.", "Reflectional", "reflectional")
        )
    }

    val formatTweaks = remember {
        listOf(
            TweakOption("Format response as clear bullet points.", "Bullet Points", "bullet"),
            TweakOption("Format response as an ordered numbered list.", "Numbered List", "numbered"),
            TweakOption("Format response in well-structured prose paragraphs.", "Paragraphs", "prose")
        )
    }

    val audienceTweaks = remember {
        listOf(
            TweakOption("Explain for a complete beginner using simple analogies.", "Beginner", "beginner"),
            TweakOption("Target an informed audience with moderate technical detail.", "Intermediate", "informed"),
            TweakOption("Provide an advanced, deep-dive technical response for experts.", "Expert", "expert")
        )
    }

    val focusTweaks = remember {
        listOf(
            TweakOption("Focus on practical, actionable takeaways and concrete steps.", "Actionable", "actionable"),
            TweakOption("Present a balanced, multi-perspective, and neutral breakdown.", "Balanced", "balanced"),
            TweakOption("Analyze critically, highlighting risks, edge cases, and pitfalls.", "Critical", "critical")
        )
    }

    var selectedWordCountPrompt by remember {
        val saved = prefs.getString("tweak_word_count", null)
        mutableStateOf(
            saved ?: wordCountTweaks.find { guidelinePrompt.contains(it.keyword, ignoreCase = true) }?.promptText ?: ""
        )
    }

    var selectedTonePrompt by remember {
        val saved = prefs.getString("tweak_tone", null)
        mutableStateOf(
            saved ?: toneTweaks.find { guidelinePrompt.contains(it.keyword, ignoreCase = true) }?.promptText ?: ""
        )
    }

    var selectedFormatPrompt by remember {
        val saved = prefs.getString("tweak_format", null)
        mutableStateOf(
            saved ?: formatTweaks.find { guidelinePrompt.contains(it.keyword, ignoreCase = true) }?.promptText ?: ""
        )
    }

    var selectedAudiencePrompt by remember {
        val saved = prefs.getString("tweak_audience", null)
        mutableStateOf(
            saved ?: audienceTweaks.find { guidelinePrompt.contains(it.keyword, ignoreCase = true) }?.promptText ?: ""
        )
    }

    var selectedFocusPrompt by remember {
        val saved = prefs.getString("tweak_focus", null)
        mutableStateOf(
            saved ?: focusTweaks.find { guidelinePrompt.contains(it.keyword, ignoreCase = true) }?.promptText ?: ""
        )
    }

    val updateTweaks: (String, String, String, String, String) -> Unit = { newWordCount, newTone, newFormat, newAudience, newFocus ->
        selectedWordCountPrompt = newWordCount
        selectedTonePrompt = newTone
        selectedFormatPrompt = newFormat
        selectedAudiencePrompt = newAudience
        selectedFocusPrompt = newFocus

        val combined = listOf(newWordCount, newTone, newFormat, newAudience, newFocus)
            .filter { it.isNotBlank() }
            .joinToString(" ")
        guidelinePrompt = combined

        prefs.edit()
            .putString("tweak_word_count", newWordCount)
            .putString("tweak_tone", newTone)
            .putString("tweak_format", newFormat)
            .putString("tweak_audience", newAudience)
            .putString("tweak_focus", newFocus)
            .putString("guideline_prompt", combined)
            .apply()
    }

    val applyPreset: (String) -> Unit = { presetName ->
        selectedPreset = presetName
        prefs.edit().putString("ai_preset", presetName).apply()
        when (presetName) {
            "MAX" -> {
                cpuCores = maxCores.toFloat()
                creativityLevel = 0.8f
                maxAnswerLength = 8192f
                topP = 0.95f
                topK = 40f
                repeatPenalty = 1.1f
                contextLength = 8192f
            }
            "AUTO" -> {
                cpuCores = maxCores.toFloat()
                creativityLevel = 0.7f
                maxAnswerLength = 4096f
                topP = 0.9f
                topK = 40f
                repeatPenalty = 1.1f
                contextLength = 4096f
            }
            "MEMORY_EFFICIENT" -> {
                cpuCores = (maxCores / 2).coerceAtLeast(1).toFloat()
                creativityLevel = 0.6f
                maxAnswerLength = 2048f
                topP = 0.85f
                topK = 20f
                repeatPenalty = 1.15f
                contextLength = 2048f
            }
            "SPEED_OPTIMIZED" -> {
                cpuCores = maxCores.toFloat()
                creativityLevel = 0.7f
                maxAnswerLength = 2048f
                topP = 0.9f
                topK = 30f
                repeatPenalty = 1.1f
                contextLength = 4096f
            }
            "BATTERY_SAVER" -> {
                cpuCores = 2f.coerceAtMost(maxCores.toFloat())
                creativityLevel = 0.5f
                maxAnswerLength = 1024f
                topP = 0.8f
                topK = 20f
                repeatPenalty = 1.1f
                contextLength = 2048f
            }
        }
        prefs.edit()
            .putFloat("cpu_cores", cpuCores)
            .putFloat("temperature", creativityLevel)
            .putFloat("max_tokens", maxAnswerLength)
            .putFloat("top_p", topP)
            .putFloat("top_k", topK)
            .putFloat("repeat_penalty", repeatPenalty)
            .putFloat("context_length", contextLength)
            .apply()
    }

    var messages by remember {
        val json = prefs.getString("chat_messages_json", null)
        val type = object : TypeToken<List<ChatMessage>>() {}.type
        mutableStateOf(
            if (json != null) {
                try {
                    gson.fromJson<List<ChatMessage>>(json, type) ?: listOf(
                        ChatMessage(
                            isUser = false,
                            text = "Welcome to AI Haven. Converse with your local model or configure settings via the top-right button."
                        )
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                    listOf(
                        ChatMessage(
                            isUser = false,
                            text = "Welcome to AI Haven. Converse with your local model or configure settings via the top-right button."
                        )
                    )
                }
            } else {
                listOf(
                    ChatMessage(
                        isUser = false,
                        text = "Welcome to AI Haven. Converse with your local model or configure settings via the top-right button."
                    )
                )
            }
        )
    }

    LaunchedEffect(messages) {
        prefs.edit().putString("chat_messages_json", gson.toJson(messages)).apply()
    }

    var inputPrompt by remember { mutableStateOf("") }
    var isGenerating by remember { mutableStateOf(false) }
    var isGeneratingSuggestions by remember { mutableStateOf(false) }
    var currentGenerationJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var activeAiMessageIndex by remember { mutableStateOf<Int?>(null) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var messageToExport by remember { mutableStateOf<String?>(null) }
    var modelStatusText by remember {
        mutableStateOf(if (ModelManager.activeEngine != null) "Model ready" else "Model not loaded")
    }

    val stopGeneration: () -> Unit = {
        currentGenerationJob?.cancel()
        currentGenerationJob = null
        isGenerating = false
        isGeneratingSuggestions = false
        try {
            ModelManager.activeEngine?.unloadModel()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        activeAiMessageIndex?.let { idx ->
            if (idx in messages.indices) {
                messages = messages.mapIndexed { i, m ->
                    if (i == idx) {
                        val t = m.text
                        val stoppedText = if (t == "..." || t.isBlank()) "[Generation stopped by user]" else "${t.trimEnd()} [Stopped]"
                        m.copy(text = stoppedText)
                    } else {
                        m
                    }
                }
            }
        }
        activeAiMessageIndex = null
    }

    val processPrompt: (String) -> Unit = { promptText ->
        if (promptText.isNotBlank() && !isGenerating) {
            val userMsg = ChatMessage(isUser = true, text = promptText)
            val aiMsg = ChatMessage(isUser = false, text = "...")
            messages = messages + userMsg + aiMsg
            val aiMessageIndex = messages.size - 1
            activeAiMessageIndex = aiMessageIndex

            isGenerating = true
            isGeneratingSuggestions = false

            currentGenerationJob = scope.launch(Dispatchers.IO) {
                try {
                    var engine = ModelManager.activeEngine
                    if (engine == null) {
                        withContext(Dispatchers.Main) {
                            modelStatusText = "Loading model..."
                        }
                        val modelOp = ModelOperation(context)
                        val modelFile = if (!selectedModelPath.isNullOrBlank()) File(selectedModelPath!!) else null
                        modelOp.loadModel(modelFile)
                        engine = ModelManager.activeEngine
                        withContext(Dispatchers.Main) {
                            modelStatusText = if (engine != null) "Model loaded successfully" else "No model loaded"
                        }
                    }

                    if (engine == null) {
                        withContext(Dispatchers.Main) {
                            messages = messages.mapIndexed { idx, m ->
                                if (idx == aiMessageIndex) m.copy(text = "Error: No AI model loaded. Please select a model in settings.") else m
                            }
                            isGeneratingSuggestions = false
                            isGenerating = false
                        }
                        return@launch
                    }

                    // Smart conversation history memory window (preserves multi-turn context safely within model limits)
                    val recentHistory = messages.subList(0, aiMessageIndex).takeLast(6)
                    var historyText = recentHistory.joinToString("\n") { msg ->
                        val snippet = if (msg.text.length > 800) msg.text.take(800) + "..." else msg.text
                        if (msg.isUser) "User: $snippet" else "Assistant: $snippet"
                    }
                    if (historyText.length > 2500) {
                        historyText = historyText.takeLast(2500)
                    }

                    // Input prompt limit for large documents/notes
                    val safePromptText = if (promptText.length > 6000) {
                        promptText.take(6000) + "\n\n[Note: Input truncated at 6,000 characters to fit model sequence limit]"
                    } else {
                        promptText
                    }

                    val contextPrompt = if (historyText.isNotBlank()) {
                        "Previous Conversation Context:\n$historyText\n\nUser: $safePromptText\nAssistant:"
                    } else {
                        "User: $safePromptText\nAssistant:"
                    }

                    val finalPrompt = if (guidelinePrompt.isNotBlank()) {
                        "$guidelinePrompt\n\n$contextPrompt"
                    } else {
                        contextPrompt
                    }

                    var accumulated = ""
                    engine.generateResponse(finalPrompt) { token, done ->
                        accumulated += token
                        scope.launch(Dispatchers.Main) {
                            messages = messages.mapIndexed { idx, m ->
                                if (idx == aiMessageIndex) m.copy(text = accumulated) else m
                            }
                            if (done) {
                                if (accumulated.isBlank()) {
                                    messages = messages.mapIndexed { idx, m ->
                                        if (idx == aiMessageIndex) m.copy(text = "The local AI model did not generate a response. The input prompt or history may exceed the model's memory/context window limit. Try clearing chat history or shortening your prompt.") else m
                                    }
                                    isGeneratingSuggestions = false
                                    isGenerating = false
                                } else if (!accumulated.startsWith("Error") && !accumulated.startsWith("خطأ")) {
                                    val trimmed = accumulated.trimEnd()
                                    val formattedText = if (trimmed.isNotEmpty() && !trimmed.endsWith(".") && !trimmed.endsWith("!") && !trimmed.endsWith("?") && !trimmed.endsWith(":") && !trimmed.endsWith("\"") && !trimmed.endsWith("*") && !trimmed.endsWith(")")) {
                                        "$trimmed..."
                                    } else {
                                        trimmed
                                    }
                                    messages = messages.mapIndexed { idx, m ->
                                        if (idx == aiMessageIndex) m.copy(text = formattedText) else m
                                    }

                                    // Generate topic-specific follow-up suggestions AFTER the main job is completed
                                    isGeneratingSuggestions = true
                                    scope.launch(Dispatchers.IO) {
                                        try {
                                            val followUpPrompt = SuggestionGenerator.buildFollowUpPrompt(formattedText)
                                            var sugAccumulated = ""
                                            engine.generateResponse(followUpPrompt) { sugToken, sugDone ->
                                                sugAccumulated += sugToken
                                                if (sugDone) {
                                                    val suggestions = SuggestionGenerator.parseFollowUpQuestions(sugAccumulated)
                                                    scope.launch(Dispatchers.Main) {
                                                        messages = messages.mapIndexed { idx, m ->
                                                            if (idx == aiMessageIndex) m.copy(suggestions = suggestions) else m
                                                        }
                                                        isGeneratingSuggestions = false
                                                        isGenerating = false
                                                    }
                                                }
                                            }
                                        } catch (e: Exception) {
                                            e.printStackTrace()
                                            scope.launch(Dispatchers.Main) {
                                                isGeneratingSuggestions = false
                                                isGenerating = false
                                            }
                                        }
                                    }
                                } else {
                                    isGeneratingSuggestions = false
                                    isGenerating = false
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    withContext(Dispatchers.Main) {
                        messages = messages.mapIndexed { idx, m ->
                            if (idx == aiMessageIndex) m.copy(text = "Error: ${e.localizedMessage}") else m
                        }
                        isGeneratingSuggestions = false
                        isGenerating = false
                    }
                }
            }
        }
    }

    // Process structured request or plain text prompt if received from other composables
    LaunchedEffect(incomingRequest, incomingPrompt) {
        val requestToProcess = incomingRequest ?: incomingPrompt?.let { HavenRequest.TextPrompt(it) }
        if (requestToProcess != null && requestToProcess.promptText.isNotBlank()) {
            onPromptConsumed()
            processPrompt(requestToProcess.promptText)
        }
    }

    // Scroll to bottom on new messages
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    BackHandler(enabled = pagerState.currentPage == 1) {
        scope.launch {
            pagerState.animateScrollToPage(0)
        }
    }

    // Model file picker launcher
    val modelPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }

            scope.launch(Dispatchers.IO) {
                try {
                    val fileName = ModelUtils.getFileNameFromUri(context, uri)
                    val existingAppFile = ModelUtils.checkExistingAppFile(context, fileName)

                    var wasCopied = false
                    var targetFile: File? = if (existingAppFile != null) {
                        existingAppFile
                    } else {
                        ModelUtils.findDirectModelFile(context, uri, fileName)
                    }

                    // If direct path doesn't exist or isn't readable, copy file to app files/storage
                    if (targetFile == null || !targetFile.exists() || !targetFile.canRead()) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Copying $fileName to app files...", Toast.LENGTH_SHORT).show()
                        }
                        val copied = ModelUtils.copyUriToAppFiles(context, uri, fileName)
                        if (copied != null && copied.exists() && copied.canRead()) {
                            targetFile = copied
                            wasCopied = true
                        }
                    }

                    if (targetFile == null || !targetFile.exists() || !targetFile.canRead()) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(
                                context,
                                "Failed to access or copy $fileName. Grant All Files Access or place model in Downloads/Mu Cephei/Models.",
                                Toast.LENGTH_LONG
                            ).show()
                            if (!ModelUtils.hasAllFilesAccess()) {
                                ModelUtils.requestAllFilesAccess(context)
                            }
                        }
                        return@launch
                    }

                    val modelOp = ModelOperation(context)
                    modelOp.loadModel(targetFile)

                    val isAppFileLocation = targetFile.parentFile?.absolutePath == context.filesDir.absolutePath ||
                            targetFile.parentFile?.absolutePath == ModelUtils.getAppExternalModelsDir(context)?.absolutePath
                    val info = LocalModelInfo(
                        name = fileName,
                        path = targetFile.absolutePath,
                        isAppFile = isAppFileLocation,
                        sizeBytes = targetFile.length()
                    )

                    ModelUtils.saveModelInfo(prefs, info, gson)

                    withContext(Dispatchers.Main) {
                        selectedModelPath = targetFile.absolutePath
                        prefs.edit().putString("model_path", targetFile.absolutePath).apply()
                        modelStatusText = "Loaded: ${targetFile.name}"
                        availableModels = ModelUtils.getAllAvailableModels(context, prefs, gson)
                        val toastMsg = if (existingAppFile != null) {
                            "Loaded existing model from app files: $fileName"
                        } else if (wasCopied) {
                            "Model copied to app files & loaded: $fileName"
                        } else {
                            "Loaded directly from path without copying: $fileName"
                        }
                        Toast.makeText(context, toastMsg, Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Failed to load model: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    // Settings Dialog
    if (showSettingsDialog) {
        AlertDialog(
            onDismissRequest = { showSettingsDialog = false },
            title = {
                Text(
                    text = "AI Assistant Settings",
                    style = HandwritingTypography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 500.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // 1. Model File Selection
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "AI Model File Selection",
                            style = HandwritingTypography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        val currentFileName = if (!selectedModelPath.isNullOrBlank()) File(selectedModelPath!!).name else "No model file selected"
                        Text(
                            text = "Currently Selected: $currentFileName",
                            style = HandwritingTypography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                        )
                        Button(
                            onClick = { modelPickerLauncher.launch(arrayOf("*/*")) },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Text("Choose Model File")
                        }

                        // Storage Permission Banner if All Files Access is not granted
                        if (!ModelUtils.hasAllFilesAccess()) {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
                            ) {
                                Column(
                                    modifier = Modifier.padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "⚠️ Storage Permission Needed",
                                        style = HandwritingTypography.titleSmall,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                    Text(
                                        text = "To scan and read model files directly from Downloads/Mu Cephei/Models or external storage without copying, All Files Access permission is required.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Button(
                                        onClick = { ModelUtils.requestAllFilesAccess(context) },
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                                    ) {
                                        Text("Grant All Files Access")
                                    }
                                }
                            }
                        }


                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    // Requirement 3 & 4 & 5: Saved & Detected Models
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Saved & Detected Models",
                                style = HandwritingTypography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            IconButton(
                                onClick = {
                                    availableModels = ModelUtils.getAllAvailableModels(context, prefs, gson)
                                    Toast.makeText(context, "Refreshed models list", Toast.LENGTH_SHORT).show()
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Refresh Models",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        Text(
                            text = "Models in Downloads/Mu Cephei/Models, app files, or previously used. Tap to switch.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )

                        if (availableModels.isEmpty()) {
                            Text(
                                text = "No models detected. Place .gguf / .bin / .task / .pte files in 'Downloads/Mu Cephei/Models' or choose a file above.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                availableModels.forEach { modelItem ->
                                    val isCurrent = selectedModelPath != null &&
                                            (selectedModelPath == modelItem.path || File(selectedModelPath!!).name == modelItem.name)

                                    val isAppFileLocation = modelItem.isAppFile ||
                                            modelItem.path.startsWith(context.filesDir.absolutePath)

                                    Surface(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                scope.launch(Dispatchers.IO) {
                                                    try {
                                                        // Requirement 2: Check app files first
                                                        val appFile = ModelUtils.checkExistingAppFile(context, modelItem.name)
                                                        val targetFile = appFile ?: File(modelItem.path)

                                                        if (!targetFile.exists() || targetFile.length() == 0L) {
                                                            withContext(Dispatchers.Main) {
                                                                Toast.makeText(context, "Model file not found: ${modelItem.name}", Toast.LENGTH_SHORT).show()
                                                                ModelUtils.removeSavedModelInfo(prefs, modelItem.path, gson)
                                                                availableModels = ModelUtils.getAllAvailableModels(context, prefs, gson)
                                                            }
                                                            return@launch
                                                        }

                                                        val modelOp = ModelOperation(context)
                                                        modelOp.loadModel(targetFile)

                                                        val updatedInfo = LocalModelInfo(
                                                            name = modelItem.name,
                                                            path = targetFile.absolutePath,
                                                            isAppFile = targetFile.parentFile?.absolutePath == context.filesDir.absolutePath,
                                                            sizeBytes = targetFile.length()
                                                        )
                                                        ModelUtils.saveModelInfo(prefs, updatedInfo, gson)

                                                        withContext(Dispatchers.Main) {
                                                            selectedModelPath = targetFile.absolutePath
                                                            prefs.edit().putString("model_path", targetFile.absolutePath).apply()
                                                            modelStatusText = "Loaded: ${targetFile.name}"
                                                            availableModels = ModelUtils.getAllAvailableModels(context, prefs, gson)
                                                            Toast.makeText(context, "Loaded model: ${targetFile.name}", Toast.LENGTH_SHORT).show()
                                                        }
                                                    } catch (e: Exception) {
                                                        e.printStackTrace()
                                                        withContext(Dispatchers.Main) {
                                                            Toast.makeText(context, "Failed to load model: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                                                        }
                                                    }
                                                }
                                            },
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (isCurrent) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                        border = if (isCurrent) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 10.dp, vertical = 8.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    Text(
                                                        text = modelItem.name,
                                                        style = HandwritingTypography.bodyMedium,
                                                        color = MaterialTheme.colorScheme.onSurface
                                                    )
                                                    if (isCurrent) {
                                                        Text(
                                                            text = "✓ Active",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = MaterialTheme.colorScheme.primary
                                                        )
                                                    }
                                                }
                                                val locTag = if (isAppFileLocation) "App Files" else "Downloads / External"
                                                val sizeStr = ModelUtils.formatFileSize(modelItem.sizeBytes)
                                                Text(
                                                    text = "$locTag • $sizeStr",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                                )
                                            }

                                            // Requirement 4: Ability to delete specific models that were already copied to app files
                                            if (isAppFileLocation) {
                                                IconButton(
                                                    onClick = {
                                                        scope.launch(Dispatchers.IO) {
                                                            try {
                                                                val fileToDelete = File(modelItem.path)
                                                                if (fileToDelete.exists()) {
                                                                    fileToDelete.delete()
                                                                }
                                                                ModelUtils.removeSavedModelInfo(prefs, modelItem.path, gson)

                                                                val wasCurrent = selectedModelPath != null &&
                                                                        (selectedModelPath == modelItem.path || File(selectedModelPath!!).name == modelItem.name)

                                                                if (wasCurrent) {
                                                                    val modelOp = ModelOperation(context)
                                                                    modelOp.unloadModel()
                                                                }

                                                                withContext(Dispatchers.Main) {
                                                                    if (wasCurrent) {
                                                                        selectedModelPath = null
                                                                        prefs.edit().remove("model_path").apply()
                                                                        modelStatusText = "No model loaded"
                                                                    }
                                                                    availableModels = ModelUtils.getAllAvailableModels(context, prefs, gson)
                                                                    Toast.makeText(context, "Deleted ${modelItem.name} from app files", Toast.LENGTH_SHORT).show()
                                                                }
                                                            } catch (e: Exception) {
                                                                e.printStackTrace()
                                                                withContext(Dispatchers.Main) {
                                                                    Toast.makeText(context, "Failed to delete: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                                                                }
                                                            }
                                                        }
                                                    }
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Delete,
                                                        contentDescription = "Delete model from app files",
                                                        tint = MaterialTheme.colorScheme.error
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    // 2. Presets & Modes (Shown directly by default)
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Response & Memory Presets",
                            style = HandwritingTypography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Select an operation mode. 'Max' is default with everything maxed for highest quality.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )

                        val presets = listOf(
                            Triple("MAX", "Max", "Everything maxed: max tokens, max context, all cores & highest quality"),
                            Triple("AUTO", "Auto Mode", "Model determines resources & settings dynamically based on request"),
                            Triple("MEMORY_EFFICIENT", "Memory Efficient", "Optimized memory usage with smaller context and token limits"),
                            Triple("SPEED_OPTIMIZED", "Speed Optimized", "Balanced settings for fast response generation and lower latency"),
                            Triple("BATTERY_SAVER", "Battery Saver", "Conserves battery by using fewer CPU cores and moderate limits")
                        )

                        presets.forEach { (key, title, desc) ->
                            val isSelected = selectedPreset == key
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { applyPreset(key) },
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                border = if (isSelected) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = title,
                                            style = HandwritingTypography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        RadioButton(
                                            selected = isSelected,
                                            onClick = { applyPreset(key) }
                                        )
                                    }
                                    Text(
                                        text = desc,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                    )
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    // 3. Common Response Prompts & Tweaks (Grouped by Type)
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "Common Response Tweaks & Prompts",
                            style = HandwritingTypography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Choose response tweaks by type (select one from each type):",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )

                        @Composable
                        fun TweakGroupSection(
                            title: String,
                            tweaks: List<TweakOption>,
                            selectedPrompt: String,
                            onSelectionChanged: (String) -> Unit
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = title,
                                    style = HandwritingTypography.titleSmall,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    tweaks.forEach { option ->
                                        val isSelected = selectedPrompt == option.promptText
                                        OutlinedButton(
                                            onClick = {
                                                val next = if (isSelected) "" else option.promptText
                                                onSelectionChanged(next)
                                                val msg = if (!isSelected) "Applied: ${option.label}" else "Cleared ${title.lowercase()}"
                                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                            },
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = ButtonDefaults.outlinedButtonColors(
                                                containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else Color.Transparent
                                            ),
                                            border = BorderStroke(
                                                if (isSelected) 1.5.dp else 1.dp,
                                                if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                                            ),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 4.dp, vertical = 2.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = option.label,
                                                    style = HandwritingTypography.bodyMedium,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                                if (isSelected) {
                                                    Text(
                                                        text = "✓ Selected",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.primary
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        TweakGroupSection(
                            title = "Number of Words",
                            tweaks = wordCountTweaks,
                            selectedPrompt = selectedWordCountPrompt
                        ) { next ->
                            updateTweaks(next, selectedTonePrompt, selectedFormatPrompt, selectedAudiencePrompt, selectedFocusPrompt)
                        }

                        TweakGroupSection(
                            title = "Tone of Speech",
                            tweaks = toneTweaks,
                            selectedPrompt = selectedTonePrompt
                        ) { next ->
                            updateTweaks(selectedWordCountPrompt, next, selectedFormatPrompt, selectedAudiencePrompt, selectedFocusPrompt)
                        }

                        TweakGroupSection(
                            title = "Format & Structure",
                            tweaks = formatTweaks,
                            selectedPrompt = selectedFormatPrompt
                        ) { next ->
                            updateTweaks(selectedWordCountPrompt, selectedTonePrompt, next, selectedAudiencePrompt, selectedFocusPrompt)
                        }

                        TweakGroupSection(
                            title = "Target Audience",
                            tweaks = audienceTweaks,
                            selectedPrompt = selectedAudiencePrompt
                        ) { next ->
                            updateTweaks(selectedWordCountPrompt, selectedTonePrompt, selectedFormatPrompt, next, selectedFocusPrompt)
                        }

                        TweakGroupSection(
                            title = "Analytical Focus",
                            tweaks = focusTweaks,
                            selectedPrompt = selectedFocusPrompt
                        ) { next ->
                            updateTweaks(selectedWordCountPrompt, selectedTonePrompt, selectedFormatPrompt, selectedAudiencePrompt, next)
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    // 4. Advanced Settings Toggle & Section
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showAdvancedSettings = !showAdvancedSettings
                                    prefs.edit().putBoolean("show_advanced", showAdvancedSettings).apply()
                                }
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Advanced Model Parameters",
                                style = HandwritingTypography.titleMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = if (showAdvancedSettings) "Hide [-]" else "View [+]",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                textAlign = TextAlign.End
                            )
                        }

                        if (showAdvancedSettings) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 4.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                // Processing Cores
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        text = "Processing Cores (${cpuCores.toInt()} / $maxCores available)",
                                        style = HandwritingTypography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Slider(
                                        value = cpuCores,
                                        onValueChange = {
                                            cpuCores = it
                                            prefs.edit().putInt("cpu_cores", it.toInt()).apply()
                                        },
                                        valueRange = 1f..maxCores.toFloat(),
                                        steps = (maxCores - 2).coerceAtLeast(0)
                                    )
                                }

                                // Creativity Level (Temperature)
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        text = "Creativity Level (Temperature): ${String.format(Locale.US, "%.2f", creativityLevel)}",
                                        style = HandwritingTypography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Slider(
                                        value = creativityLevel,
                                        onValueChange = {
                                            creativityLevel = it
                                            prefs.edit().putFloat("temperature", it).apply()
                                        },
                                        valueRange = 0.0f..1.0f
                                    )
                                }

                                // Top-P
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        text = "Top-P Sampling: ${String.format(Locale.US, "%.2f", topP)}",
                                        style = HandwritingTypography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Slider(
                                        value = topP,
                                        onValueChange = {
                                            topP = it
                                            prefs.edit().putFloat("top_p", it).apply()
                                        },
                                        valueRange = 0.1f..1.0f
                                    )
                                }

                                // Top-K
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        text = "Top-K Sampling: ${topK.toInt()}",
                                        style = HandwritingTypography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Slider(
                                        value = topK,
                                        onValueChange = {
                                            topK = it
                                            prefs.edit().putFloat("top_k", it).apply()
                                        },
                                        valueRange = 1f..100f
                                    )
                                }

                                // Repeat Penalty
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        text = "Repeat Penalty: ${String.format(Locale.US, "%.2f", repeatPenalty)}",
                                        style = HandwritingTypography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Slider(
                                        value = repeatPenalty,
                                        onValueChange = {
                                            repeatPenalty = it
                                            prefs.edit().putFloat("repeat_penalty", it).apply()
                                        },
                                        valueRange = 1.0f..2.0f
                                    )
                                }

                                // Maximum Answer Length
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        text = "Maximum Answer Length: ${maxAnswerLength.toInt()} tokens",
                                        style = HandwritingTypography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Slider(
                                        value = maxAnswerLength,
                                        onValueChange = {
                                            maxAnswerLength = it
                                            prefs.edit().putFloat("max_tokens", it).apply()
                                        },
                                        valueRange = 64f..16384f
                                    )
                                }

                                // Context Length
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        text = "Context Window Length: ${contextLength.toInt()} tokens",
                                        style = HandwritingTypography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Slider(
                                        value = contextLength,
                                        onValueChange = {
                                            contextLength = it
                                            prefs.edit().putFloat("context_length", it).apply()
                                        },
                                        valueRange = 1024f..16384f
                                    )
                                }

                                // Guideline / Custom Instructions Prompt
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        text = "Custom Guideline / Prompt",
                                        style = HandwritingTypography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    OutlinedTextField(
                                        value = guidelinePrompt,
                                        onValueChange = { newPrompt ->
                                            guidelinePrompt = newPrompt
                                            prefs.edit().putString("guideline_prompt", newPrompt).apply()
                                            val matchedWordCount = wordCountTweaks.find { newPrompt.contains(it.promptText) || newPrompt.contains(it.keyword, ignoreCase = true) }?.promptText ?: ""
                                            val matchedTone = toneTweaks.find { newPrompt.contains(it.promptText) || newPrompt.contains(it.keyword, ignoreCase = true) }?.promptText ?: ""
                                            val matchedFormat = formatTweaks.find { newPrompt.contains(it.promptText) || newPrompt.contains(it.keyword, ignoreCase = true) }?.promptText ?: ""
                                            val matchedAudience = audienceTweaks.find { newPrompt.contains(it.promptText) || newPrompt.contains(it.keyword, ignoreCase = true) }?.promptText ?: ""
                                            val matchedFocus = focusTweaks.find { newPrompt.contains(it.promptText) || newPrompt.contains(it.keyword, ignoreCase = true) }?.promptText ?: ""

                                            selectedWordCountPrompt = matchedWordCount
                                            selectedTonePrompt = matchedTone
                                            selectedFormatPrompt = matchedFormat
                                            selectedAudiencePrompt = matchedAudience
                                            selectedFocusPrompt = matchedFocus

                                            prefs.edit()
                                                .putString("tweak_word_count", matchedWordCount)
                                                .putString("tweak_tone", matchedTone)
                                                .putString("tweak_format", matchedFormat)
                                                .putString("tweak_audience", matchedAudience)
                                                .putString("tweak_focus", matchedFocus)
                                                .apply()
                                        },
                                        placeholder = { Text("e.g., Respond in 10 words or less") },
                                        modifier = Modifier.fillMaxWidth(),
                                        textStyle = HandwritingTypography.bodyMedium,
                                        maxLines = 3
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSettingsDialog = false }) {
                    Text("Done", style = HandwritingTypography.titleMedium)
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
    ) {
        // Top Header Row with Clear Chat & Settings buttons
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Haven",
                    style = HandwritingTypography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = modelStatusText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }

            // Actions Row: Clear Chat & Settings
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        messages = listOf(
                            ChatMessage(
                                isUser = false,
                                text = "Welcome to AI Haven. Converse with your local model or configure settings via the top-right button."
                            )
                        )
                        prefs.edit().remove("chat_messages_json").apply()
                        Toast.makeText(context, "Conversation history cleared", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Clear Chat",
                        tint = MaterialTheme.colorScheme.error
                    )
                }

                IconButton(
                    onClick = { showSettingsDialog = true }
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Settings",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
            modifier = Modifier.padding(horizontal = 14.dp)
        )

        // Message Export / Send Dialog
        if (messageToExport != null) {
            AlertDialog(
                onDismissRequest = { messageToExport = null },
                title = {
                    Text(
                        text = "Send Text to Other Screens",
                        style = HandwritingTypography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 110.dp)
                                .border(
                                    1.dp,
                                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                    RoundedCornerShape(8.dp)
                                ),
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .verticalScroll(rememberScrollState())
                                    .padding(8.dp)
                            ) {
                                MarkdownText(
                                    text = messageToExport!!,
                                    style = HandwritingTypography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                                )
                            }
                        }

                        HorizontalDivider()

                        Button(
                            onClick = {
                                val text = messageToExport!!
                                messageToExport = null
                                scope.launch(Dispatchers.IO) {
                                    val note = Note(title = "AI Haven Output", bodyText = text)
                                    repository.saveNote(note)
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(context, "Saved as new note in Capsule", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Save as New Note in Capsule")
                        }

                        Button(
                            onClick = {
                                val text = messageToExport!!
                                messageToExport = null
                                scope.launch(Dispatchers.IO) {
                                    val latestNote = repository.allNotes.firstOrNull()?.maxByOrNull { it.updatedAt }
                                    if (latestNote != null) {
                                        val updated = latestNote.copy(
                                            bodyText = "${latestNote.bodyText}\n\n$text",
                                            updatedAt = System.currentTimeMillis()
                                        )
                                        repository.saveNote(updated)
                                        withContext(Dispatchers.Main) {
                                            Toast.makeText(context, "Appended to latest note", Toast.LENGTH_SHORT).show()
                                        }
                                    } else {
                                        val newNote = Note(title = "AI Haven Output", bodyText = text)
                                        repository.saveNote(newNote)
                                        withContext(Dispatchers.Main) {
                                            Toast.makeText(context, "Created new note in Capsule", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Append to Latest Note")
                        }

                        Button(
                            onClick = {
                                val text = messageToExport!!
                                messageToExport = null
                                onSearchWithOasis(text)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Search with Oasis", color = MaterialTheme.colorScheme.onTertiaryContainer)
                        }

                        Button(
                            onClick = {
                                val text = messageToExport!!
                                messageToExport = null
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                val clip = android.content.ClipData.newPlainText("Haven Output", text)
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Copy to Clipboard", color = MaterialTheme.colorScheme.onSurface)
                        }

                        Button(
                            onClick = {
                                val text = messageToExport!!
                                messageToExport = null
                                val sendIntent = Intent().apply {
                                    action = Intent.ACTION_SEND
                                    putExtra(Intent.EXTRA_TEXT, text)
                                    type = "text/plain"
                                }
                                context.startActivity(Intent.createChooser(sendIntent, null))
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Share Text", color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { messageToExport = null }) {
                        Text("Close")
                    }
                }
            )
        }

        // Chat Messages List
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            items(messages, key = { it.id }) { msg ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = if (msg.isUser) Arrangement.End else Arrangement.Start
                ) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (msg.isUser) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                        modifier = Modifier
                            .widthIn(max = 350.dp)
                            .border(
                                1.dp,
                                if (msg.isUser) MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                                RoundedCornerShape(16.dp)
                            )
                            .clickable { messageToExport = msg.text }
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp)
                        ) {
                            MarkdownText(
                                text = msg.text,
                                style = HandwritingTypography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Tap for options",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }

            // Indicator while generating follow-up suggestions
            if (isGeneratingSuggestions) {
                item(key = "generating_suggestions_indicator") {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Generating follow-up question / actions for you",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }

            // Contextual Follow-up Suggestions (Generated directly by the Model)
            val lastMessage = messages.lastOrNull()
            if (lastMessage != null && !lastMessage.isUser && !isGenerating && lastMessage.text.isNotBlank() && lastMessage.suggestions.isNotEmpty()) {
                item(key = "follow_up_suggestions_${lastMessage.id}") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalAlignment = Alignment.Start
                    ) {
                        Text(
                            text = "Suggested follow-ups:",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            modifier = Modifier.padding(start = 2.dp, bottom = 2.dp)
                        )
                        lastMessage.suggestions.forEach { suggestion ->
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(
                                        1.dp,
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                                        RoundedCornerShape(16.dp)
                                    )
                                    .clickable { processPrompt(suggestion) }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = "✨",
                                        style = HandwritingTypography.bodySmall
                                    )
                                    MarkdownText(
                                        text = suggestion,
                                        style = HandwritingTypography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Bottom Input Bar
        Surface(
            color = Color.Transparent,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = inputPrompt,
                    onValueChange = { inputPrompt = it },
                    placeholder = { Text("Ask your local AI model...") },
                    modifier = Modifier.weight(1f),
                    textStyle = HandwritingTypography.bodyMedium,
                    shape = RoundedCornerShape(24.dp),
                    maxLines = 4,
                    enabled = !isGenerating
                )

                if (isGenerating) {
                    IconButton(
                        onClick = stopGeneration,
                        modifier = Modifier
                            .size(44.dp)
                            .background(MaterialTheme.colorScheme.errorContainer, CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Stop,
                            contentDescription = "Stop Generation",
                            tint = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                } else {
                    IconButton(
                        onClick = {
                            if (inputPrompt.isBlank() || isGenerating) return@IconButton
                            val promptText = inputPrompt.trim()
                            inputPrompt = ""
                            processPrompt(promptText)
                        },
                        enabled = inputPrompt.isNotBlank() && !isGenerating
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send Message",
                            tint = if (inputPrompt.isNotBlank() && !isGenerating) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                        )
                    }
                }
            }
        }
    }
}