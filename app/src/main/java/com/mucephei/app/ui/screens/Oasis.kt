@file:Suppress("DEPRECATION", "ClickableViewAccessibility")
package com.mucephei.app.ui.screens

import android.app.Activity
import android.content.Context
import android.net.Uri
import android.view.MotionEvent
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.URLUtil
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebViewDatabase
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.mucephei.app.data.model.MediaItem
import com.mucephei.app.data.model.MediaItemType
import com.mucephei.app.data.model.MediaMetadata
import com.mucephei.app.data.model.Note
import com.mucephei.app.data.repository.NoteRepository
import com.mucephei.app.theme.HandwritingTypography
import com.mucephei.app.ui.components.ImageSelectionToolbar
import com.mucephei.app.ui.components.TextSelectionToolbar
import com.mucephei.app.ui.components.ThemedActionButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.util.UUID

data class HistoryItem(
    val url: String,
    val title: String,
    val timestamp: Long = System.currentTimeMillis()
)

data class BookmarkItem(
    val url: String,
    val title: String
)

data class BrowserDownloadItem(
    val id: String = UUID.randomUUID().toString(),
    val url: String,
    val fileName: String,
    val mimeType: String,
    var progress: Int = 0,
    var isComplete: Boolean = false,
    val destinationPath: String
)

data class BrowserTab(
    val id: String = UUID.randomUUID().toString(),
    var url: String = "",
    var title: String = "Speed Dial",
    var scrollY: Int = 0
)

data class SpeedDialItem(
    val title: String,
    val url: String,
    val subtitle: String
)

val academicSpeedDialEntries = listOf(
    SpeedDialItem("Google Scholar", "https://scholar.google.com", "Academic search engine"),
    SpeedDialItem("arXiv", "https://arxiv.org", "Open-access preprints in physics, math, computer science"),
    SpeedDialItem("ResearchGate", "https://www.researchgate.net", "Professional network for scientists and researchers"),
    SpeedDialItem("JSTOR", "https://www.jstor.org", "Digital library of academic journals and books"),
    SpeedDialItem("PubMed", "https://pubmed.ncbi.nlm.nih.gov", "Biomedical and life sciences literature"),
    SpeedDialItem("IEEE Xplore", "https://ieeexplore.ieee.org", "Engineering and technology literature"),
    SpeedDialItem("ScienceDirect", "https://www.sciencedirect.com", "Peer-reviewed scientific, technical and medical research"),
    SpeedDialItem("Semantic Scholar", "https://www.semanticscholar.org", "AI-powered research tool for scientific literature"),
    SpeedDialItem("Wikipedia", "https://www.wikipedia.org", "Free online encyclopedia"),
    SpeedDialItem("MIT OpenCourseWare", "https://ocw.mit.edu", "Free lecture notes, exams, and videos from MIT")
)

/**
- Oasis Screen: Feature-rich Web Browser adhering to all user specifications:
- Multiple tab support with speed dial default (10 academic entries)
- Address bar click selects entire address by default, backspace delete, and clear "x"
- Dynamic Search vs Go button and keyboard Enter navigation
- Scroll down expands vertically (top system bar disappears and browser claims space)
- Direct model file downloads (.task, .bin, .gguf, .litertlm, .pte) into Downloads/Mu Cephei/Models
- Comprehensive clearing of history, passwords, downloads, cache, and cookies
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Oasis(
    pagerState: androidx.compose.foundation.pager.PagerState,
    repository: NoteRepository,
    initialWebUrl: String? = null,
    onNavigateToHaven: (HavenRequest) -> Unit = {},
    onWallpaperChanged: (String?, Float) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("oasis_browser_prefs", Context.MODE_PRIVATE) }
    val gson = remember { Gson() }

    val activity = context as? Activity
    val window = activity?.window
    val insetsController = remember(window) {
        window?.let {
            WindowCompat.getInsetsController(it, it.decorView).apply {
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }
    }

    // Tabs Management & Persistence
    var tabsList by remember {
        val savedJson = prefs.getString("open_tabs_json", null)
        val restoredTabs: List<BrowserTab>? = if (!savedJson.isNullOrBlank()) {
            try {
                val type = object : TypeToken<List<BrowserTab>>() {}.type
                gson.fromJson<List<BrowserTab>>(savedJson, type)
            } catch (_: Exception) {
                null
            }
        } else null

        val initialList = if (!restoredTabs.isNullOrEmpty()) {
            restoredTabs
        } else {
            val initialTab = BrowserTab(url = initialWebUrl ?: "", title = if (initialWebUrl != null) "Web" else "Speed Dial")
            listOf(initialTab)
        }
        mutableStateOf(initialList)
    }

    var activeTabId by remember {
        val savedActiveId = prefs.getString("active_tab_id", null)
        val initialActive = if (savedActiveId != null && tabsList.any { it.id == savedActiveId }) {
            savedActiveId
        } else {
            tabsList.first().id
        }
        mutableStateOf(initialActive)
    }
    val activeTab = tabsList.find { it.id == activeTabId } ?: tabsList.first()
    var currentUrl by remember { mutableStateOf(activeTab.url) }

    var addressInputState by remember(activeTabId, activeTab.url) {
        mutableStateOf(TextFieldValue(text = activeTab.url, selection = TextRange.Zero))
    }
    var pageTitle by remember { mutableStateOf(activeTab.title) }
    var isLoading by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }

    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }

    // Persistent History & Bookmarks via Gson
    var historyList by remember {
        val json = prefs.getString("history_json", null)
        val type = object : TypeToken<List<HistoryItem>>() {}.type
        mutableStateOf(if (json != null) gson.fromJson<List<HistoryItem>>(json, type) else emptyList())
    }

    var bookmarksList by remember {
        val json = prefs.getString("bookmarks_json", null)
        val type = object : TypeToken<List<BookmarkItem>>() {}.type
        mutableStateOf(if (json != null) gson.fromJson<List<BookmarkItem>>(json, type) else emptyList())
    }

    LaunchedEffect(historyList) {
        prefs.edit().putString("history_json", gson.toJson(historyList)).apply()
    }

    LaunchedEffect(bookmarksList) {
        prefs.edit().putString("bookmarks_json", gson.toJson(bookmarksList)).apply()
    }

    var downloadsList by remember { mutableStateOf(listOf<BrowserDownloadItem>()) }

    var customDownloadDir by remember {
        mutableStateOf(
            prefs.getString("download_dir", context.getExternalFilesDir(null)?.absolutePath ?: context.filesDir.absolutePath)!!
        )
    }
    var customExtensionsStr by remember { mutableStateOf(prefs.getString("custom_extensions", "task,bin,gguf,litertlm,pte,zip") ?: "task,bin,gguf,litertlm,pte,zip") }

    // Dialogs states
    var showHistoryDialog by remember { mutableStateOf(false) }
    var showBookmarksDialog by remember { mutableStateOf(false) }
    var showDownloadsDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showAiActionsDialog by remember { mutableStateOf(false) }
    var showTabsDialog by remember { mutableStateOf(false) }
    var showClearDataDialog by remember { mutableStateOf(false) }
    var showToolbarSettingsDialog by remember { mutableStateOf(false) }
    var autoShowDownloadsMode by remember { mutableStateOf(prefs.getString("auto_show_downloads", "ALWAYS") ?: "ALWAYS") }

    var toolbarDisabledForever by remember { mutableStateOf(prefs.getBoolean("toolbar_disabled_forever", false)) }
    var toolbarOrientation by remember { mutableStateOf(prefs.getString("toolbar_orientation", "HORIZONTAL") ?: "HORIZONTAL") }
    var toolbarTransparency by remember { mutableStateOf(prefs.getFloat("toolbar_transparency", 0.95f)) }
    var toolbarAutoPopup by remember { mutableStateOf(prefs.getBoolean("toolbar_auto_popup", true)) }
    var toolbarSuppressOnPageLoad by remember { mutableStateOf(prefs.getBoolean("toolbar_suppress_on_page_load", true)) }
    var toolbarSize by remember { mutableStateOf(prefs.getString("toolbar_size", "MEDIUM") ?: "MEDIUM") }
    var toolbarFunctionOrderStr by remember { mutableStateOf(prefs.getString("toolbar_function_order", "new_note,append_note,summarize,analyze,define,search_oasis") ?: "new_note,append_note,summarize,analyze,define,search_oasis") }
    var toolbarDisabledFunctionsStr by remember { mutableStateOf(prefs.getString("toolbar_disabled_functions", "") ?: "") }

    var selectedText by remember { mutableStateOf("") }
    var selectedTextToolbarVisible by remember { mutableStateOf(false) }
    var selectedImageUrl by remember { mutableStateOf("") }
    var imageToolbarVisible by remember { mutableStateOf(false) }
    var dismissedText by remember { mutableStateOf("") }
    var dismissedImageUrl by remember { mutableStateOf("") }

    // WebView cache map per tab ID
    val webViewsMap = remember { mutableMapOf<String, WebView>() }

    fun saveTabsToPrefs() {
        webViewsMap.forEach { (tabId, wv) ->
            tabsList.find { it.id == tabId }?.let {
                if (wv.scrollY > 0) {
                    it.scrollY = wv.scrollY
                }
            }
        }
        prefs.edit()
            .putString("open_tabs_json", gson.toJson(tabsList))
            .putString("active_tab_id", activeTabId)
            .apply()
    }

    LaunchedEffect(tabsList, activeTabId) {
        saveTabsToPrefs()
    }

    DisposableEffect(Unit) {
        onDispose {
            saveTabsToPrefs()
        }
    }

    LaunchedEffect(initialWebUrl) {
        if (!initialWebUrl.isNullOrBlank()) {
            val existing = tabsList.find { it.url == initialWebUrl }
            if (existing != null) {
                activeTabId = existing.id
            } else {
                val newTab = BrowserTab(url = initialWebUrl, title = "Web")
                tabsList = tabsList + newTab
                activeTabId = newTab.id
            }
        }
    }

    LaunchedEffect(activeTabId) {
        val tab = tabsList.find { it.id == activeTabId } ?: return@LaunchedEffect
        currentUrl = tab.url
        pageTitle = tab.title
        addressInputState = TextFieldValue(text = tab.url, selection = TextRange.Zero)
        val wv = webViewsMap[tab.id]
        if (wv != null) {
            canGoBack = wv.canGoBack()
            canGoForward = wv.canGoForward()
        } else {
            canGoBack = false
            canGoForward = false
        }
    }

    // Helper JS script string to extract explicit user text selection (no full page fallback)
    val extractTextJs = """
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

    fun getOrCreateWebView(tab: BrowserTab): WebView {
        return webViewsMap.getOrPut(tab.id) {
            WebView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)
                overScrollMode = WebView.OVER_SCROLL_IF_CONTENT_SCROLLS
                isVerticalScrollBarEnabled = true
                isHorizontalScrollBarEnabled = true

                var lastToggleTime = 0L
                var accumulativeScrollDelta = 0

                setOnScrollChangeListener { _, _, scrollY, _, oldScrollY ->
                    tab.scrollY = scrollY
                    val dy = scrollY - oldScrollY
                    val now = System.currentTimeMillis()

                    if (scrollY < 50) {
                        accumulativeScrollDelta = 0
                        if (now - lastToggleTime > 300) {
                            insetsController?.show(WindowInsetsCompat.Type.statusBars())
                            lastToggleTime = now
                        }
                    } else if (now - lastToggleTime > 350) {
                        if (dy > 0 && accumulativeScrollDelta >= 0) {
                            accumulativeScrollDelta += dy
                            if (accumulativeScrollDelta > 120) {
                                insetsController?.hide(WindowInsetsCompat.Type.statusBars())
                                lastToggleTime = now
                                accumulativeScrollDelta = 0
                            }
                        } else if (dy < 0 && accumulativeScrollDelta <= 0) {
                            accumulativeScrollDelta += dy
                            if (accumulativeScrollDelta < -80) {
                                insetsController?.show(WindowInsetsCompat.Type.statusBars())
                                lastToggleTime = now
                                accumulativeScrollDelta = 0
                            }
                        } else {
                            accumulativeScrollDelta = dy
                        }
                    }
                }

                setOnLongClickListener { _ ->
                    val hitResult = hitTestResult
                    if (hitResult.type == WebView.HitTestResult.IMAGE_TYPE || hitResult.type == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE) {
                        val url = hitResult.extra
                        if (!url.isNullOrBlank() && url != dismissedImageUrl) {
                            selectedImageUrl = url
                            imageToolbarVisible = true
                            true
                        } else {
                            false
                        }
                    } else {
                        false
                    }
                }

                setOnTouchListener { v, event ->
                    when (event.action) {
                        MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                            v.parent?.requestDisallowInterceptTouchEvent(true)
                        }
                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                            v.parent?.requestDisallowInterceptTouchEvent(false)
                            if (!toolbarDisabledForever && toolbarAutoPopup) {
                                evaluateJavascript(extractTextJs) { result: String? ->
                                    val clean = parseJsString(gson, result).trim()
                                    // Only show toolbar if the user selected at least one full word / meaningful text
                                    if (clean.length < 2 || !clean.any { it.isLetterOrDigit() }) {
                                        dismissedText = ""
                                        selectedTextToolbarVisible = false
                                    } else if (clean != dismissedText) {
                                        selectedText = clean
                                        selectedTextToolbarVisible = true
                                    }
                                }
                            }
                        }
                    }
                    false
                }

                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    databaseEnabled = true
                    setCacheMode(WebSettings.LOAD_DEFAULT)
                    loadWithOverviewMode = true
                    useWideViewPort = true
                    builtInZoomControls = true
                    displayZoomControls = false
                    allowFileAccess = true
                    allowContentAccess = true
                    setSupportZoom(true)
                    layoutAlgorithm = WebSettings.LayoutAlgorithm.TEXT_AUTOSIZING
                }

                val cookieManager = CookieManager.getInstance()
                cookieManager.setAcceptCookie(true)
                cookieManager.setAcceptThirdPartyCookies(this, true)

                webViewClient = object : WebViewClient() {
                    override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                        super.onPageStarted(view, url, favicon)
                        if (toolbarSuppressOnPageLoad) {
                            selectedTextToolbarVisible = false
                        }
                        url?.let {
                            tab.url = it
                            if (tab.id == activeTabId) {
                                currentUrl = it
                                addressInputState = addressInputState.copy(text = it)
                            }
                        }
                        isLoading = true
                        canGoBack = canGoBack()
                        canGoForward = canGoForward()
                    }

                    override fun onPageCommitVisible(view: WebView?, url: String?) {
                        super.onPageCommitVisible(view, url)
                        if (tab.scrollY > 0) {
                            view?.scrollTo(0, tab.scrollY)
                        }
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        if (tab.scrollY > 0) {
                            view?.scrollTo(0, tab.scrollY)
                            view?.postDelayed({ view.scrollTo(0, tab.scrollY) }, 100)
                            view?.postDelayed({ view.scrollTo(0, tab.scrollY) }, 300)
                        }
                        url?.let {
                            tab.url = it
                            val title = view?.title ?: it
                            tab.title = title
                            if (tab.id == activeTabId) {
                                currentUrl = it
                                pageTitle = title
                                addressInputState = addressInputState.copy(text = it)
                            }
                            historyList = listOf(HistoryItem(it, title)) + historyList
                        }
                        isLoading = false
                        canGoBack = canGoBack()
                        canGoForward = canGoForward()
                        saveTabsToPrefs()
                    }
                }

                webChromeClient = object : WebChromeClient() {
                    override fun onProgressChanged(view: WebView?, newProgress: Int) {
                        super.onProgressChanged(view, newProgress)
                        if (tab.id == activeTabId) {
                            progress = newProgress / 100f
                            isLoading = newProgress < 100
                        }
                    }

                    override fun onReceivedTitle(view: WebView?, title: String?) {
                        super.onReceivedTitle(view, title)
                        title?.let {
                            tab.title = it
                            if (tab.id == activeTabId) {
                                pageTitle = it
                            }
                        }
                    }
                }

                setDownloadListener { url, userAgent, contentDisposition, mimeType, contentLength ->
                    var fileName = resolveBestFileName(url, contentDisposition, null, null, mimeType)
                    var lowerName = fileName.lowercase()
                    var isModelFile = lowerName.endsWith(".task") || lowerName.endsWith(".bin") ||
                                      lowerName.endsWith(".gguf") || lowerName.endsWith(".litertlm") || lowerName.endsWith(".pte")

                    val destDir = if (isModelFile) {
                        val publicDownloads = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
                        File(publicDownloads, "Mu Cephei/Models")
                    } else {
                        File(customDownloadDir)
                    }
                    destDir.mkdirs()
                    val destFile = File(destDir, fileName)

                    val downloadItem = BrowserDownloadItem(
                        url = url,
                        fileName = fileName,
                        mimeType = mimeType,
                        destinationPath = destFile.absolutePath
                    )
                    downloadsList = listOf(downloadItem) + downloadsList

                    val shouldAutoShow = when (autoShowDownloadsMode) {
                        "ALWAYS" -> true
                        "MIN_SIZE" -> contentLength > 5 * 1024 * 1024L
                        "NEVER" -> false
                        else -> true
                    }
                    if (shouldAutoShow) {
                        showDownloadsDialog = true
                    }

                    scope.launch(Dispatchers.IO) {
                        try {
                            val connection = URL(url).openConnection() as java.net.HttpURLConnection
                            if (!userAgent.isNullOrBlank()) {
                                connection.setRequestProperty("User-Agent", userAgent)
                            }
                            connection.instanceFollowRedirects = true
                            connection.connect()

                            val headerCd = connection.getHeaderField("Content-Disposition")
                            val finalUrl = connection.url.toString()
                            val refinedFileName = resolveBestFileName(url, contentDisposition, headerCd, finalUrl, mimeType)

                            var targetFile = destFile
                            if (refinedFileName != fileName && refinedFileName.isNotBlank()) {
                                fileName = refinedFileName
                                lowerName = fileName.lowercase()
                                isModelFile = lowerName.endsWith(".task") || lowerName.endsWith(".bin") ||
                                              lowerName.endsWith(".gguf") || lowerName.endsWith(".litertlm") || lowerName.endsWith(".pte")
                                val targetDir = if (isModelFile) {
                                    val publicDownloads = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
                                    File(publicDownloads, "Mu Cephei/Models")
                                } else {
                                    File(customDownloadDir)
                                }
                                targetDir.mkdirs()
                                targetFile = File(targetDir, fileName)

                                downloadsList = downloadsList.map {
                                    if (it.id == downloadItem.id) it.copy(fileName = fileName, destinationPath = targetFile.absolutePath) else it
                                }
                            }

                            val inputStream = connection.inputStream
                            FileOutputStream(targetFile).use { output ->
                                val buffer = ByteArray(8192)
                                var bytesRead: Int
                                var totalRead = 0L
                                val totalSize = if (contentLength > 0) contentLength else connection.contentLengthLong.coerceAtLeast(1L)

                                if (autoShowDownloadsMode == "MIN_SIZE" && totalSize > 5 * 1024 * 1024L) {
                                    withContext(Dispatchers.Main) {
                                        showDownloadsDialog = true
                                    }
                                }
                                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                                    output.write(buffer, 0, bytesRead)
                                    totalRead += bytesRead
                                    val pct = ((totalRead * 100) / totalSize).coerceIn(0, 100).toInt()
                                    downloadsList = downloadsList.map {
                                        if (it.id == downloadItem.id) it.copy(progress = pct) else it
                                    }
                                }
                            }
                            downloadsList = downloadsList.map {
                                if (it.id == downloadItem.id) it.copy(isComplete = true, progress = 100) else it
                            }
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, "Downloaded: $fileName to ${targetFile.absolutePath}", Toast.LENGTH_LONG).show()
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, "Download failed: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }

                if (tab.url.isNotBlank()) {
                    loadUrl(tab.url)
                }
            }
        }
    }

    fun isUrlInput(input: String): Boolean {
        val trimmed = input.trim()
        return trimmed.startsWith("http://") || trimmed.startsWith("https://") ||
               (trimmed.contains(".") && !trimmed.contains(" ") && !trimmed.startsWith("localhost"))
    }

    fun navigateTo(input: String) {
        val query = input.trim()
        val url = if (isUrlInput(query)) {
            if (query.startsWith("http://") || query.startsWith("https://")) query else "https://$query"
        } else {
            "https://www.google.com/search?q=${Uri.encode(query)}"
        }
        activeTab.url = url
        currentUrl = url
        val wv = getOrCreateWebView(activeTab)
        wv.loadUrl(url)
    }

    val evaluateLiveText: ((String) -> Unit) -> Unit = { callback ->
        val wv = webViewsMap[activeTabId]
        if (wv != null) {
            wv.evaluateJavascript(extractTextJs) { result: String? ->
                val clean = parseJsString(gson, result)
                val finalText = if (clean.isNotBlank()) clean else selectedText.ifBlank { pageTitle }
                callback(finalText)
            }
        } else {
            callback(selectedText.ifBlank { pageTitle })
        }
    }

    LaunchedEffect(initialWebUrl) {
        initialWebUrl?.let { url ->
            if (url != activeTab.url) {
                activeTab.url = url
                activeTab.title = "Web"
                currentUrl = url
                addressInputState = TextFieldValue(text = url, selection = TextRange.Zero)
                val wv = getOrCreateWebView(activeTab)
                wv.loadUrl(url)
            }
        }
    }

    // System back button handling
    BackHandler(enabled = true) {
        scope.launch {
            val wv = webViewsMap[activeTabId]
            if (wv != null && wv.canGoBack()) {
                wv.goBack()
            } else if (pagerState.currentPage > 0) {
                pagerState.animateScrollToPage(pagerState.currentPage - 1)
            }
        }
    }

    // AI Actions Dialog
    if (showAiActionsDialog) {
        val scrollState = rememberScrollState()

        AlertDialog(
            onDismissRequest = { showAiActionsDialog = false },
            shape = RoundedCornerShape(24.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Text(
                        text = "Text & AI Actions",
                        style = HandwritingTypography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Selected Text:",
                        style = HandwritingTypography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 160.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                            .padding(12.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(scrollState)
                        ) {
                            Text(
                                text = selectedText,
                                style = HandwritingTypography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
                            )
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    Button(
                        onClick = {
                            showAiActionsDialog = false
                            scope.launch(Dispatchers.IO) {
                                val note = Note(title = pageTitle, bodyText = selectedText)
                                repository.saveNote(note)
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(context, "Saved as note to Capsule", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Bookmark, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Save as Note to Capsule")
                    }

                    Button(
                        onClick = {
                            showAiActionsDialog = false
                            onNavigateToHaven(HavenRequest.TextPrompt(selectedText))
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Send to AI Haven")
                    }

                    Button(
                        onClick = {
                            showAiActionsDialog = false
                            onNavigateToHaven(HavenRequest.Summarize(content = selectedText, title = pageTitle))
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Summarize with AI", color = MaterialTheme.colorScheme.onSurface)
                    }

                    Button(
                        onClick = {
                            showAiActionsDialog = false
                            onNavigateToHaven(HavenRequest.Define(term = selectedText))
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Define with AI", color = MaterialTheme.colorScheme.onSurface)
                    }

                    Button(
                        onClick = {
                            showAiActionsDialog = false
                            onNavigateToHaven(HavenRequest.Explain(content = selectedText))
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Explain with AI", color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAiActionsDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    // Tabs Switcher Dialog
    if (showTabsDialog) {
        AlertDialog(
            onDismissRequest = { showTabsDialog = false },
            shape = RoundedCornerShape(24.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Tab,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Text("Open Tabs (${tabsList.size})", style = HandwritingTypography.titleLarge)
                    }
                    IconButton(
                        onClick = {
                            val newTab = BrowserTab(url = "", title = "Speed Dial")
                            tabsList = tabsList + newTab
                            activeTabId = newTab.id
                            addressInputState = TextFieldValue("")
                            showTabsDialog = false
                        },
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(12.dp))
                            .size(36.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "New Tab", tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
            },
            text = {
                LazyColumn(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(tabsList) { tab ->
                        val isSelected = tab.id == activeTabId
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    activeTabId = tab.id
                                    currentUrl = tab.url
                                    addressInputState = TextFieldValue(text = tab.url, selection = TextRange.Zero)
                                    pageTitle = tab.title
                                    showTabsDialog = false
                                },
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                            ),
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = tab.title.ifBlank { "Speed Dial" },
                                        style = HandwritingTypography.titleMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = tab.url.ifBlank { "Speed Dial Home" },
                                        style = HandwritingTypography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                if (tabsList.size > 1) {
                                    IconButton(onClick = {
                                        webViewsMap.remove(tab.id)?.destroy()
                                        val newTabs = tabsList.filter { it.id != tab.id }
                                        tabsList = newTabs
                                        if (activeTabId == tab.id) {
                                            activeTabId = newTabs.first().id
                                            currentUrl = newTabs.first().url
                                            addressInputState = TextFieldValue(text = currentUrl, selection = TextRange.Zero)
                                        }
                                    }) {
                                        Icon(Icons.Default.Close, contentDescription = "Close Tab", tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showTabsDialog = false }) { Text("Close") } }
        )
    }

    // Clear Browsing Data Dialog
    if (showClearDataDialog) {
        var clearHistory by remember { mutableStateOf(true) }
        var clearCookiesCache by remember { mutableStateOf(true) }
        var clearPasswords by remember { mutableStateOf(true) }
        var clearDownloads by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showClearDataDialog = false },
            shape = RoundedCornerShape(24.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Text("Clear Browsing Data", style = HandwritingTypography.titleLarge)
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                            .clickable { clearHistory = !clearHistory }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Browsing History", style = HandwritingTypography.bodyMedium)
                        Checkbox(checked = clearHistory, onCheckedChange = { clearHistory = it })
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                            .clickable { clearCookiesCache = !clearCookiesCache }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Cache, Cookies & Site Data", style = HandwritingTypography.bodyMedium)
                        Checkbox(checked = clearCookiesCache, onCheckedChange = { clearCookiesCache = it })
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                            .clickable { clearPasswords = !clearPasswords }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Saved Passwords & Autofill", style = HandwritingTypography.bodyMedium)
                        Checkbox(checked = clearPasswords, onCheckedChange = { clearPasswords = it })
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                            .clickable { clearDownloads = !clearDownloads }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Download List Records", style = HandwritingTypography.bodyMedium)
                        Checkbox(checked = clearDownloads, onCheckedChange = { clearDownloads = it })
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showClearDataDialog = false
                        if (clearHistory) {
                            historyList = emptyList()
                            prefs.edit().remove("history_json").apply()
                        }
                        if (clearCookiesCache) {
                            webViewsMap.values.forEach { it.clearCache(true) }
                            CookieManager.getInstance().removeAllCookies(null)
                            WebStorage.getInstance().deleteAllData()
                        }
                        if (clearPasswords) {
                            WebViewDatabase.getInstance(context).clearHttpAuthUsernamePassword()
                        }
                        if (clearDownloads) {
                            downloadsList = emptyList()
                        }
                        Toast.makeText(context, "Selected browsing data cleared", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Clear Data", color = MaterialTheme.colorScheme.onError)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDataDialog = false }) { Text("Cancel") }
            }
        )
    }

    // History Dialog
    if (showHistoryDialog) {
        AlertDialog(
            onDismissRequest = { showHistoryDialog = false },
            shape = RoundedCornerShape(24.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.History,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Text("Browsing History", style = HandwritingTypography.titleLarge)
                }
            },
            text = {
                LazyColumn(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(historyList) { item ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showHistoryDialog = false
                                    activeTab.url = item.url
                                    currentUrl = item.url
                                    addressInputState = TextFieldValue(text = item.url, selection = TextRange.Zero)
                                    getOrCreateWebView(activeTab).loadUrl(item.url)
                                },
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(text = item.title, style = HandwritingTypography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(text = item.url, style = HandwritingTypography.bodySmall, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showHistoryDialog = false }) { Text("Close") } }
        )
    }

    // Bookmarks Dialog
    if (showBookmarksDialog) {
        AlertDialog(
            onDismissRequest = { showBookmarksDialog = false },
            shape = RoundedCornerShape(24.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Bookmark,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Text("Bookmarks", style = HandwritingTypography.titleLarge)
                }
            },
            text = {
                LazyColumn(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(bookmarksList) { item ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showBookmarksDialog = false
                                    activeTab.url = item.url
                                    currentUrl = item.url
                                    addressInputState = TextFieldValue(text = item.url, selection = TextRange.Zero)
                                    getOrCreateWebView(activeTab).loadUrl(item.url)
                                },
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(text = item.title, style = HandwritingTypography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(text = item.url, style = HandwritingTypography.bodySmall, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showBookmarksDialog = false }) { Text("Close") } }
        )
    }

    // Downloads Manager Dialog
    if (showDownloadsDialog) {
        AlertDialog(
            onDismissRequest = { showDownloadsDialog = false },
            shape = RoundedCornerShape(24.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Text("Download Manager", style = HandwritingTypography.titleLarge)
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Auto-Show Preference Controls inside Download Manager
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Auto-Show Download Manager on Start:",
                                style = HandwritingTypography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                val options = listOf("ALWAYS" to "Always", "MIN_SIZE" to "> 5MB Only", "NEVER" to "Never")
                                options.forEach { (mode, label) ->
                                    Button(
                                        onClick = {
                                            autoShowDownloadsMode = mode
                                            prefs.edit().putString("auto_show_downloads", mode).apply()
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (autoShowDownloadsMode == mode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                            contentColor = if (autoShowDownloadsMode == mode) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                        ),
                                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(label, style = HandwritingTypography.labelSmall, maxLines = 1, softWrap = false)
                                    }
                                }
                            }
                        }
                    }

                    if (downloadsList.isEmpty()) {
                        Text(
                            text = "No active or recent downloads.",
                            style = HandwritingTypography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            modifier = Modifier.padding(vertical = 12.dp)
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 320.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(downloadsList) { item ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
                                    shape = RoundedCornerShape(16.dp),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(14.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(text = item.fileName, style = HandwritingTypography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(text = "Status: ${if (item.isComplete) "Complete (${item.destinationPath})" else "${item.progress}%"}", style = HandwritingTypography.bodySmall)
                                        if (item.isComplete) {
                                            Button(
                                                onClick = {
                                                    val file = File(item.destinationPath)
                                                    if (file.exists() && (item.destinationPath.endsWith(".png") || item.destinationPath.endsWith(".jpg"))) {
                                                        onWallpaperChanged(item.destinationPath, 0f)
                                                        Toast.makeText(context, "Set as app wallpaper", Toast.LENGTH_SHORT).show()
                                                    } else {
                                                        Toast.makeText(context, "Saved at ${item.destinationPath}", Toast.LENGTH_LONG).show()
                                                    }
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                                                shape = RoundedCornerShape(10.dp),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Text("Set as Wallpaper / View")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showDownloadsDialog = false }) { Text("Close") } }
        )
    }

    // Browser Settings Dialog
    if (showSettingsDialog) {
        AlertDialog(
            onDismissRequest = { showSettingsDialog = false },
            shape = RoundedCornerShape(24.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Text("Browser Settings", style = HandwritingTypography.titleLarge)
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("Download Directory Path:", style = HandwritingTypography.labelMedium)
                    OutlinedTextField(
                        value = customDownloadDir,
                        onValueChange = {
                            customDownloadDir = it
                            prefs.edit().putString("download_dir", it).apply()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        textStyle = HandwritingTypography.bodySmall
                    )

                    Text("Managed Model Extensions (.task, .bin, .gguf, .litertlm, .pte):", style = HandwritingTypography.labelMedium)
                    OutlinedTextField(
                        value = customExtensionsStr,
                        onValueChange = {
                            customExtensionsStr = it
                            prefs.edit().putString("custom_extensions", it).apply()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        textStyle = HandwritingTypography.bodySmall,
                        placeholder = { Text("task,bin,gguf,litertlm,pte") }
                    )

                    Button(
                        onClick = {
                            showSettingsDialog = false
                            showClearDataDialog = true
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Clear Browsing Data...", color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showSettingsDialog = false }) { Text("Done") } }
        )
    }

    // Toolbar Settings & Behavior Dialog
    if (showToolbarSettingsDialog) {
        AlertDialog(
            onDismissRequest = { showToolbarSettingsDialog = false },
            shape = RoundedCornerShape(24.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Text("Toolbar Settings & Behavior", style = HandwritingTypography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
                }
            },
            text = {
                val scrollState = rememberScrollState()
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 440.dp)
                        .verticalScroll(scrollState),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // 1. Hide Forever
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Hide Toolbar Forever", style = HandwritingTypography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                            Text("Completely disable and hide the text selection toolbar.", style = HandwritingTypography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                        }
                        Switch(
                            checked = toolbarDisabledForever,
                            onCheckedChange = {
                                toolbarDisabledForever = it
                                prefs.edit().putBoolean("toolbar_disabled_forever", it).apply()
                            }
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    // 2. Orientation
                    Text("Toolbar Layout (Orientation)", style = HandwritingTypography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                toolbarOrientation = "HORIZONTAL"
                                prefs.edit().putString("toolbar_orientation", "HORIZONTAL").apply()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (toolbarOrientation == "HORIZONTAL") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = if (toolbarOrientation == "HORIZONTAL") MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Horizontal Scroll", style = HandwritingTypography.labelMedium, maxLines = 1, softWrap = false)
                        }
                        Button(
                            onClick = {
                                toolbarOrientation = "VERTICAL"
                                prefs.edit().putString("toolbar_orientation", "VERTICAL").apply()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (toolbarOrientation == "VERTICAL") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = if (toolbarOrientation == "VERTICAL") MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Vertical List", style = HandwritingTypography.labelMedium, maxLines = 1, softWrap = false)
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    // 3. Transparency
                    Text("Toolbar Transparency / Opacity", style = HandwritingTypography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val transOptions = listOf("Opaque (1.0)" to 1.0f, "Semi (0.85)" to 0.85f, "Glass (0.6)" to 0.6f)
                        transOptions.forEach { (label, value) ->
                            Button(
                                onClick = {
                                    toolbarTransparency = value
                                    prefs.edit().putFloat("toolbar_transparency", value).apply()
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (toolbarTransparency == value) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = if (toolbarTransparency == value) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                contentPadding = PaddingValues(horizontal = 2.dp, vertical = 8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(label, style = HandwritingTypography.labelSmall, maxLines = 1, softWrap = false)
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    // 4. Size Preset
                    Text("Toolbar Size", style = HandwritingTypography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("SMALL", "MEDIUM", "LARGE").forEach { size ->
                            Button(
                                onClick = {
                                    toolbarSize = size
                                    prefs.edit().putString("toolbar_size", size).apply()
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (toolbarSize == size) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = if (toolbarSize == size) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = size.lowercase().replaceFirstChar { it.uppercase() },
                                    style = HandwritingTypography.labelMedium,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    // 5. Behavior & Triggers
                    Text("Behavior & Triggers", style = HandwritingTypography.titleMedium, color = MaterialTheme.colorScheme.onSurface)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Auto-Popup on Text Selection", style = HandwritingTypography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                            Text("Automatically show toolbar when selecting text.", style = HandwritingTypography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                        }
                        Switch(
                            checked = toolbarAutoPopup,
                            onCheckedChange = {
                                toolbarAutoPopup = it
                                prefs.edit().putBoolean("toolbar_auto_popup", it).apply()
                            }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Suppress on Page Load", style = HandwritingTypography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                            Text("Prevent toolbar from popping up during page loads.", style = HandwritingTypography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                        }
                        Switch(
                            checked = toolbarSuppressOnPageLoad,
                            onCheckedChange = {
                                toolbarSuppressOnPageLoad = it
                                prefs.edit().putBoolean("toolbar_suppress_on_page_load", it).apply()
                            }
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    // 6. Quick Functions Order & Visibility
                    Text("Quick Functions (Reorder & Toggle)", style = HandwritingTypography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                    val allFuncKeys = listOf("new_note", "append_note", "summarize", "analyze", "define", "search_oasis")
                    val funcNames = mapOf(
                        "summarize" to "Haven: Summarize",
                        "analyze" to "Haven: Analyze",
                        "define" to "Haven: Define",
                        "new_note" to "New Note",
                        "append_note" to "Append to Note",
                        "search_oasis" to "Search Oasis"
                    )

                    var currentOrder by remember { mutableStateOf(toolbarFunctionOrderStr.split(",").map { it.trim() }.filter { it.isNotBlank() }) }
                    var disabledSet by remember { mutableStateOf(toolbarDisabledFunctionsStr.split(",").map { it.trim() }.filter { it.isNotBlank() }.toSet()) }

                    val activeList = (currentOrder + (allFuncKeys - currentOrder)).distinct()

                    activeList.forEachIndexed { index, key ->
                        val isEnabled = key !in disabledSet
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Checkbox(
                                    checked = isEnabled,
                                    onCheckedChange = { checked ->
                                        disabledSet = if (checked) disabledSet - key else disabledSet + key
                                        toolbarDisabledFunctionsStr = disabledSet.joinToString(",")
                                        prefs.edit().putString("toolbar_disabled_functions", toolbarDisabledFunctionsStr).apply()
                                    }
                                )
                                Text(funcNames[key] ?: key, style = HandwritingTypography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                IconButton(
                                    enabled = index > 0,
                                    onClick = {
                                        val mutable = activeList.toMutableList()
                                        val item = mutable.removeAt(index)
                                        mutable.add(index - 1, item)
                                        currentOrder = mutable
                                        toolbarFunctionOrderStr = currentOrder.joinToString(",")
                                        prefs.edit().putString("toolbar_function_order", toolbarFunctionOrderStr).apply()
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Default.ArrowUpward, contentDescription = "Move Up", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(16.dp))
                                }
                                IconButton(
                                    enabled = index < activeList.size - 1,
                                    onClick = {
                                        val mutable = activeList.toMutableList()
                                        val item = mutable.removeAt(index)
                                        mutable.add(index + 1, item)
                                        currentOrder = mutable
                                        toolbarFunctionOrderStr = currentOrder.joinToString(",")
                                        prefs.edit().putString("toolbar_function_order", toolbarFunctionOrderStr).apply()
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Default.ArrowDownward, contentDescription = "Move Down", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showToolbarSettingsDialog = false }) { Text("Done", style = HandwritingTypography.labelLarge) } }
        )
    }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
        ) {
            if (isLoading) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp),
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // WebView or Speed Dial Container
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (activeTab.url.isBlank()) {
                    // Speed Dial default page with 10 academic entries
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
                            shape = RoundedCornerShape(20.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.School,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Oasis Academic Speed Dial",
                                        style = HandwritingTypography.titleLarge,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "Curated scholarly repositories and research engines.",
                                        style = HandwritingTypography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
                                    )
                                }
                            }
                        }

                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 650.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            userScrollEnabled = false
                        ) {
                            items(academicSpeedDialEntries) { entry ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            activeTab.url = entry.url
                                            activeTab.title = entry.title
                                            currentUrl = entry.url
                                            addressInputState = TextFieldValue(text = entry.url, selection = TextRange.Zero)
                                            val wv = getOrCreateWebView(activeTab)
                                            wv.loadUrl(entry.url)
                                        },
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)),
                                    shape = RoundedCornerShape(16.dp),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(14.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                                modifier = Modifier.size(28.dp)
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Icon(
                                                        imageVector = Icons.Default.MenuBook,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }
                                            Text(
                                                text = entry.title,
                                                style = HandwritingTypography.titleMedium,
                                                color = MaterialTheme.colorScheme.primary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                        Text(
                                            text = entry.subtitle,
                                            style = HandwritingTypography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    key(activeTabId) {
                        val wv = getOrCreateWebView(activeTab)
                        AndroidView(
                            factory = {
                                (wv.parent as? ViewGroup)?.removeView(wv)
                                wv
                            },
                            modifier = Modifier.fillMaxSize()
                        ) { _ -> }
                    }
                }

                // Floating Text Selection Toolbar evaluating live JS text on click
                TextSelectionToolbar(
                    visible = selectedTextToolbarVisible,
                    selectedText = selectedText,
                    onDismiss = { selectedTextToolbarVisible = false; dismissedText = selectedText },
                    onSummarizeWithHaven = {
                        selectedTextToolbarVisible = false
                        evaluateLiveText { text ->
                            onNavigateToHaven(HavenRequest.Summarize(text, pageTitle))
                        }
                    },
                    onAnalyzeWithHaven = {
                        selectedTextToolbarVisible = false
                        evaluateLiveText { text ->
                            onNavigateToHaven(HavenRequest.AnalyzeNote(title = pageTitle, bodyText = text))
                        }
                    },
                    onDefineWithHaven = {
                        selectedTextToolbarVisible = false
                        evaluateLiveText { text ->
                            onNavigateToHaven(HavenRequest.Define(text))
                        }
                    },
                    onAddToNewNote = {
                        selectedTextToolbarVisible = false
                        evaluateLiveText { text ->
                            scope.launch(Dispatchers.IO) {
                                val newNote = Note(title = pageTitle, bodyText = text)
                                repository.saveNote(newNote)
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(context, "Added to new note in Capsule", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    },
                    onAppendToLastNote = {
                        selectedTextToolbarVisible = false
                        evaluateLiveText { text ->
                            scope.launch(Dispatchers.IO) {
                                val latestNote = repository.allNotes.firstOrNull()?.maxByOrNull { it.updatedAt }
                                if (latestNote != null) {
                                    val updated = latestNote.copy(
                                        bodyText = "${latestNote.bodyText}\n\n$text",
                                        updatedAt = System.currentTimeMillis()
                                    )
                                    repository.saveNote(updated)
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(context, "Appended to '${latestNote.title.ifBlank { "Note" }}'", Toast.LENGTH_SHORT).show()
                                    }
                                } else {
                                    val newNote = Note(title = pageTitle, bodyText = text)
                                    repository.saveNote(newNote)
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(context, "Created new note in Capsule", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        }
                    },
                    onSearchWithOasis = {
                        selectedTextToolbarVisible = false
                        evaluateLiveText { text ->
                            navigateTo(text)
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 12.dp),
                    disabledForever = toolbarDisabledForever,
                    orientation = toolbarOrientation,
                    transparency = toolbarTransparency,
                    sizePreset = toolbarSize,
                    functionOrder = toolbarFunctionOrderStr.split(",").map { it.trim() }.filter { it.isNotBlank() },
                    disabledFunctions = toolbarDisabledFunctionsStr.split(",").map { it.trim() }.filter { it.isNotBlank() }.toSet(),
                    noAutoPopupAtAll = !toolbarAutoPopup
                )

                // Image Long-Click Floating Toolbar
                ImageSelectionToolbar(
                    visible = imageToolbarVisible,
                    imageUrl = selectedImageUrl,
                    onDismiss = { imageToolbarVisible = false; dismissedImageUrl = selectedImageUrl },
                    onSendToCapsule = {
                        imageToolbarVisible = false
                        val imgUrl = selectedImageUrl
                        scope.launch(Dispatchers.IO) {
                            val savedFile = saveImageFromUrl(context, imgUrl, repository.mediaDirectory)
                            if (savedFile != null) {
                                val mediaItem = MediaItem(
                                    type = MediaItemType.PHOTO,
                                    uri = savedFile.absolutePath,
                                    metadata = MediaMetadata(
                                        title = savedFile.name,
                                        fileName = savedFile.name
                                    )
                                )
                                val newNote = Note(
                                    title = pageTitle,
                                    bodyText = "",
                                    media = listOf(mediaItem)
                                )
                                repository.saveNote(newNote)
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(context, "Image sent to Capsule note", Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(context, "Failed to download image", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    },
                    onSetAsWallpaper = {
                        imageToolbarVisible = false
                        val imgUrl = selectedImageUrl
                        scope.launch(Dispatchers.IO) {
                            val savedFile = saveImageFromUrl(context, imgUrl, repository.mediaDirectory)
                            if (savedFile != null) {
                                withContext(Dispatchers.Main) {
                                    onWallpaperChanged(savedFile.absolutePath, 0f)
                                    Toast.makeText(context, "Image set as wallpaper", Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(context, "Failed to set wallpaper", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 12.dp)
                )
            }

            // Browser Toolbar moved to the bottom with modern surface container & tonal elevation
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                tonalElevation = 6.dp,
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .pointerInput(Unit) {
                            detectHorizontalDragGestures(
                                onHorizontalDrag = { change, dragAmount ->
                                    if (!pagerState.isScrollInProgress) {
                                        change.consume()
                                        scope.launch {
                                            val targetPage = if (dragAmount < -15f) {
                                                0
                                            } else if (dragAmount > 15f) {
                                                1
                                            } else {
                                                pagerState.currentPage
                                            }
                                            if (targetPage != pagerState.currentPage) {
                                                pagerState.animateScrollToPage(targetPage)
                                            }
                                        }
                                    }
                                }
                            )
                        },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Back Button
                    ThemedActionButton(
                        onClick = {
                            val wv = webViewsMap[activeTabId]
                            if (wv != null && wv.canGoBack()) wv.goBack()
                        },
                        icon = Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        tint = if (canGoBack) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f)
                    )

                    // Forward Button
                    ThemedActionButton(
                        onClick = {
                            val wv = webViewsMap[activeTabId]
                            if (wv != null && wv.canGoForward()) wv.goForward()
                        },
                        icon = Icons.Default.ArrowForward,
                        contentDescription = "Forward",
                        tint = if (canGoForward) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f)
                    )

                    // Address Input Field with click-to-select, backspace delete, and clear "x"
                    val isUrl = isUrlInput(addressInputState.text)
                    OutlinedTextField(
                        value = addressInputState,
                        onValueChange = { addressInputState = it },
                        modifier = Modifier
                            .weight(1f)
                            .onFocusChanged { focusState ->
                                if (focusState.isFocused && addressInputState.text.isNotEmpty()) {
                                    addressInputState = addressInputState.copy(
                                        selection = TextRange(0, addressInputState.text.length)
                                    )
                                }
                            },
                        shape = RoundedCornerShape(24.dp),
                        textStyle = HandwritingTypography.bodySmall,
                        maxLines = 1,
                        placeholder = { Text("Search or URL") },
                        keyboardOptions = KeyboardOptions(
                            imeAction = if (isUrl) ImeAction.Go else ImeAction.Search
                        ),
                        keyboardActions = KeyboardActions(
                            onGo = { navigateTo(addressInputState.text) },
                            onSearch = { navigateTo(addressInputState.text) },
                            onDone = { navigateTo(addressInputState.text) }
                        ),
                        trailingIcon = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (addressInputState.text.isNotEmpty()) {
                                    IconButton(onClick = {
                                        addressInputState = TextFieldValue("")
                                    }) {
                                        Icon(Icons.Default.Close, contentDescription = "Clear Address", modifier = Modifier.size(18.dp))
                                    }
                                }
                                IconButton(onClick = {
                                    navigateTo(addressInputState.text)
                                }) {
                                    Icon(
                                        imageVector = if (isUrl) Icons.Default.ArrowForward else Icons.Default.Search,
                                        contentDescription = if (isUrl) "Go" else "Search"
                                    )
                                }
                            }
                        }
                    )

                    // Tabs Switcher Button
                    ThemedActionButton(
                        onClick = { showTabsDialog = true },
                        icon = Icons.Default.Tab,
                        contentDescription = "Tabs (${tabsList.size})",
                        tint = MaterialTheme.colorScheme.primary,
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f)
                    )

                    // Menu / More Options Dropdown
                    var showMenu by remember { mutableStateOf(false) }
                    Box {
                        ThemedActionButton(
                            onClick = { showMenu = true },
                            icon = Icons.Default.MoreVert,
                            contentDescription = "Menu",
                            tint = MaterialTheme.colorScheme.primary,
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f)
                        )
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("Speed Dial Home") },
                                onClick = {
                                    showMenu = false
                                    activeTab.url = ""
                                    activeTab.title = "Speed Dial"
                                    addressInputState = TextFieldValue("")
                                },
                                leadingIcon = { Icon(Icons.Default.Language, contentDescription = null) }
                            )
                            DropdownMenuItem(
                                text = { Text("Bookmark Page") },
                                onClick = {
                                    showMenu = false
                                    bookmarksList = bookmarksList + BookmarkItem(activeTab.url, pageTitle)
                                    Toast.makeText(context, "Page bookmarked", Toast.LENGTH_SHORT).show()
                                },
                                leadingIcon = { Icon(Icons.Default.Bookmark, contentDescription = null) }
                            )
                            DropdownMenuItem(
                                text = { Text("Bookmarks") },
                                onClick = { showMenu = false; showBookmarksDialog = true },
                                leadingIcon = { Icon(Icons.Default.Bookmark, contentDescription = null) }
                            )
                            DropdownMenuItem(
                                text = { Text("History") },
                                onClick = { showMenu = false; showHistoryDialog = true },
                                leadingIcon = { Icon(Icons.Default.History, contentDescription = null) }
                            )
                            DropdownMenuItem(
                                text = { Text("Download Manager") },
                                onClick = { showMenu = false; showDownloadsDialog = true },
                                leadingIcon = { Icon(Icons.Default.Download, contentDescription = null) }
                            )
                            DropdownMenuItem(
                                text = { Text("Browser & Download Settings") },
                                onClick = { showMenu = false; showSettingsDialog = true },
                                leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) }
                            )
                            DropdownMenuItem(
                                text = { Text("Toolbar Settings & Behavior") },
                                onClick = { showMenu = false; showToolbarSettingsDialog = true },
                                leadingIcon = { Icon(Icons.Default.Tune, contentDescription = null) }
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun parseContentDispositionFileName(header: String?): String? {
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

private fun extractFileNameFromUrlPath(urlStr: String?): String? {
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

private fun resolveBestFileName(
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

private fun parseJsString(gson: Gson, result: String?): String {
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

private suspend fun saveImageFromUrl(context: Context, imageUrl: String, targetDir: File): File? = withContext(Dispatchers.IO) {
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
