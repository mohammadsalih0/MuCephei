@file:Suppress("DEPRECATION", "ClickableViewAccessibility")
package com.mucephei.app.ui.screens

import android.app.Activity
import android.content.Context
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebViewDatabase
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import com.mucephei.app.util.preferredHighestRefreshRate
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.mucephei.app.data.model.MediaItem
import com.mucephei.app.data.model.MediaItemType
import com.mucephei.app.data.model.MediaMetadata
import com.mucephei.app.data.model.Note
import com.mucephei.app.data.repository.NoteRepository
import com.mucephei.app.ui.components.ImageSelectionToolbar
import com.mucephei.app.ui.components.TextSelectionToolbar
import com.mucephei.app.ui.screens.oasis.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.URL

/**
 * Oasis Screen: Feature-rich Web Browser adhering to all user specifications:
 * - Multiple tab support with speed dial default (10 academic entries)
 * - Address bar click selects entire address by default, backspace delete, and clear "x"
 * - Dynamic Search vs Go button and keyboard Enter navigation
 * - Scroll down expands vertically (top system bar disappears and browser claims space)
 * - Direct model file downloads (.task, .bin, .gguf, .litertlm, .pte) into Downloads/Mu Cephei/Models
 * - Comprehensive clearing of history, passwords, downloads, cache, and cookies
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

    var speedDialList by remember {
        val json = prefs.getString("speed_dial_json", null)
        val type = object : TypeToken<List<SpeedDialItem>>() {}.type
        val restored: List<SpeedDialItem>? = if (!json.isNullOrBlank()) {
            try {
                gson.fromJson<List<SpeedDialItem>>(json, type)
            } catch (_: Exception) {
                null
            }
        } else null
        mutableStateOf(restored ?: academicSpeedDialEntries)
    }

    LaunchedEffect(historyList) {
        prefs.edit().putString("history_json", gson.toJson(historyList)).apply()
    }

    LaunchedEffect(bookmarksList) {
        prefs.edit().putString("bookmarks_json", gson.toJson(bookmarksList)).apply()
    }

    LaunchedEffect(speedDialList) {
        prefs.edit().putString("speed_dial_json", gson.toJson(speedDialList)).apply()
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
    var showAddSpeedDialDialog by remember { mutableStateOf(false) }
    var initialSpeedDialTitle by remember { mutableStateOf("") }
    var initialSpeedDialUrl by remember { mutableStateOf("") }
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

    // WebView & SwipeRefreshLayout cache map per tab ID
    val webViewsMap = remember { mutableMapOf<String, WebView>() }
    val swipeRefreshLayoutsMap: MutableMap<String, androidx.swiperefreshlayout.widget.SwipeRefreshLayout> = remember { mutableMapOf() }
    val primaryColorArgb = MaterialTheme.colorScheme.primary.toArgb()
    val surfaceColorArgb = MaterialTheme.colorScheme.surface.toArgb()

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

    // REMOVED: LaunchedEffect to load initialWebUrl or handle tab URL navigation immediately.
    // WebViews are now only created on-demand inside the box/navigation UI flow.

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

                val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
                    override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                        if (e1 != null) {
                            val dx = e2.x - e1.x
                            val dy = e2.y - e1.y
                            if (kotlin.math.abs(dx) > kotlin.math.abs(dy) && kotlin.math.abs(dx) > 100 && kotlin.math.abs(velocityX) > 200) {
                                if (dx > 0) {
                                    // Swipe right -> go back
                                    if (canGoBack()) {
                                        goBack()
                                        return true
                                    }
                                } else {
                                    // Swipe left -> go forward
                                    if (canGoForward()) {
                                        goForward()
                                        return true
                                    }
                                }
                            }
                        }
                        return false
                    }
                })

                var startTouchX = 0f
                var startTouchY = 0f
                var isHorizontalSwipe = false

                setOnTouchListener { v, event ->
                    gestureDetector.onTouchEvent(event)
                    val wv = v as WebView
                    when (event.action) {
                        MotionEvent.ACTION_DOWN -> {
                            startTouchX = event.x
                            startTouchY = event.y
                            isHorizontalSwipe = false
                        }
                        MotionEvent.ACTION_MOVE -> {
                            val dx = event.x - startTouchX
                            val dy = event.y - startTouchY
                            if (!canScrollVertically(-1) && dy > 0 && kotlin.math.abs(dy) > kotlin.math.abs(dx)) {
                                v.parent?.requestDisallowInterceptTouchEvent(false)
                            } else if (kotlin.math.abs(dx) > kotlin.math.abs(dy) && kotlin.math.abs(dx) > 30) {
                                isHorizontalSwipe = true
                                v.parent?.requestDisallowInterceptTouchEvent(true)
                            } else {
                                v.parent?.requestDisallowInterceptTouchEvent(true)
                            }
                        }
                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                            v.parent?.requestDisallowInterceptTouchEvent(false)
                            val dx = event.x - startTouchX
                            val dy = event.y - startTouchY
                            val threshold = 150f
                            if (isHorizontalSwipe && kotlin.math.abs(dx) > threshold && kotlin.math.abs(dx) > kotlin.math.abs(dy)) {
                                if (dx > 0) {
                                    // Swipe right -> go back
                                    if (wv.canGoBack()) {
                                        wv.goBack()
                                    }
                                } else {
                                    // Swipe left -> go forward
                                    if (wv.canGoForward()) {
                                        wv.goForward()
                                    }
                                }
                            }
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
                        swipeRefreshLayoutsMap[tab.id]?.isRefreshing = false
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
                        if (newProgress >= 100) {
                            swipeRefreshLayoutsMap[tab.id]?.isRefreshing = false
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

    fun getOrCreateSwipeRefreshLayout(tab: BrowserTab): androidx.swiperefreshlayout.widget.SwipeRefreshLayout {
        return swipeRefreshLayoutsMap.getOrPut(tab.id) {
            val wv = getOrCreateWebView(tab)
            androidx.swiperefreshlayout.widget.SwipeRefreshLayout(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                setColorSchemeColors(primaryColorArgb)
                setProgressBackgroundColorSchemeColor(surfaceColorArgb)
                setOnRefreshListener {
                    wv.reload()
                }
                (wv.parent as? ViewGroup)?.removeView(wv)
                addView(
                    wv,
                    ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                )
            }
        }
    }

    fun navigateTo(input: String) {
        val query = input.trim()
        val url = if (isUrlInput(query)) {
            if (query.startsWith("http://") || query.startsWith("https://")) query else "https://$query"
        } else {
            "https://www.google.com/search?q=${android.net.Uri.encode(query)}"
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
            // Only update if no active WebView exists, preserving state for lazy loading
            val wv = webViewsMap[activeTabId]
            if (wv == null && url != activeTab.url) {
                activeTab.url = url
                activeTab.title = "Web"
                currentUrl = url
                addressInputState = TextFieldValue(text = url, selection = TextRange.Zero)
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

    // Dialogs
    if (showAiActionsDialog) {
        AiActionsDialog(
            selectedText = selectedText,
            onDismiss = { showAiActionsDialog = false },
            onSaveAsNote = {
                showAiActionsDialog = false
                scope.launch(Dispatchers.IO) {
                    val note = Note(title = pageTitle, bodyText = selectedText)
                    repository.saveNote(note)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Saved as note to Capsule", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onSendToHaven = {
                showAiActionsDialog = false
                onNavigateToHaven(HavenRequest.TextPrompt(selectedText))
            },
            onSummarize = {
                showAiActionsDialog = false
                onNavigateToHaven(HavenRequest.Summarize(content = selectedText, title = pageTitle))
            },
            onDefine = {
                showAiActionsDialog = false
                onNavigateToHaven(HavenRequest.Define(term = selectedText))
            },
            onExplain = {
                showAiActionsDialog = false
                onNavigateToHaven(HavenRequest.Explain(content = selectedText))
            }
        )
    }

    if (showTabsDialog) {
        TabsSwitcherDialog(
            tabsList = tabsList,
            activeTabId = activeTabId,
            onDismiss = { showTabsDialog = false },
            onNewTab = {
                val newTab = BrowserTab(url = "", title = "Speed Dial")
                tabsList = tabsList + newTab
                activeTabId = newTab.id
                addressInputState = TextFieldValue("")
                showTabsDialog = false
            },
            onSelectTab = { tab ->
                activeTabId = tab.id
                currentUrl = tab.url
                addressInputState = TextFieldValue(text = tab.url, selection = TextRange.Zero)
                pageTitle = tab.title
                showTabsDialog = false
            },
            onCloseTab = { tab ->
                swipeRefreshLayoutsMap.remove(tab.id)?.removeAllViews()
                webViewsMap.remove(tab.id)?.destroy()
                val newTabs = tabsList.filter { it.id != tab.id }
                tabsList = newTabs
                if (activeTabId == tab.id) {
                    activeTabId = newTabs.first().id
                    currentUrl = newTabs.first().url
                    addressInputState = TextFieldValue(text = currentUrl, selection = TextRange.Zero)
                }
            }
        )
    }

    if (showClearDataDialog) {
        ClearBrowsingDataDialog(
            onDismiss = { showClearDataDialog = false },
            onConfirmClear = { clearHistory, clearCookiesCache, clearPasswords, clearDownloads ->
                showClearDataDialog = false
                if (clearHistory) {
                    historyList = emptyList()
                    prefs.edit().remove("history_json").apply()
                }
                if (clearCookiesCache) {
                    swipeRefreshLayoutsMap.values.forEach { it.isRefreshing = false }
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
            }
        )
    }

    if (showHistoryDialog) {
        HistoryDialog(
            historyList = historyList,
            onDismiss = { showHistoryDialog = false },
            onItemClick = { item ->
                showHistoryDialog = false
                activeTab.url = item.url
                currentUrl = item.url
                addressInputState = TextFieldValue(text = item.url, selection = TextRange.Zero)
                getOrCreateWebView(activeTab).loadUrl(item.url)
            }
        )
    }

    if (showBookmarksDialog) {
        BookmarksDialog(
            bookmarksList = bookmarksList,
            onDismiss = { showBookmarksDialog = false },
            onItemClick = { item ->
                showBookmarksDialog = false
                activeTab.url = item.url
                currentUrl = item.url
                addressInputState = TextFieldValue(text = item.url, selection = TextRange.Zero)
                getOrCreateWebView(activeTab).loadUrl(item.url)
            }
        )
    }

    if (showDownloadsDialog) {
        DownloadManagerDialog(
            downloadsList = downloadsList,
            autoShowDownloadsMode = autoShowDownloadsMode,
            onAutoShowModeChange = { mode ->
                autoShowDownloadsMode = mode
                prefs.edit().putString("auto_show_downloads", mode).apply()
            },
            onDismiss = { showDownloadsDialog = false },
            onWallpaperSet = { destinationPath ->
                val file = File(destinationPath)
                if (file.exists() && (destinationPath.endsWith(".png") || destinationPath.endsWith(".jpg"))) {
                    onWallpaperChanged(destinationPath, 0f)
                    Toast.makeText(context, "Set as app wallpaper", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Saved at $destinationPath", Toast.LENGTH_LONG).show()
                }
            }
        )
    }

    if (showSettingsDialog) {
        BrowserSettingsDialog(
            customDownloadDir = customDownloadDir,
            onDownloadDirChange = {
                customDownloadDir = it
                prefs.edit().putString("download_dir", it).apply()
            },
            customExtensionsStr = customExtensionsStr,
            onExtensionsChange = {
                customExtensionsStr = it
                prefs.edit().putString("custom_extensions", it).apply()
            },
            onOpenClearData = {
                showSettingsDialog = false
                showClearDataDialog = true
            },
            onDismiss = { showSettingsDialog = false }
        )
    }

    if (showToolbarSettingsDialog) {
        ToolbarSettingsDialog(
            disabledForever = toolbarDisabledForever,
            onDisabledForeverChange = {
                toolbarDisabledForever = it
                prefs.edit().putBoolean("toolbar_disabled_forever", it).apply()
            },
            orientation = toolbarOrientation,
            onOrientationChange = {
                toolbarOrientation = it
                prefs.edit().putString("toolbar_orientation", it).apply()
            },
            transparency = toolbarTransparency,
            onTransparencyChange = {
                toolbarTransparency = it
                prefs.edit().putFloat("toolbar_transparency", it).apply()
            },
            sizePreset = toolbarSize,
            onSizePresetChange = {
                toolbarSize = it
                prefs.edit().putString("toolbar_size", it).apply()
            },
            autoPopup = toolbarAutoPopup,
            onAutoPopupChange = {
                toolbarAutoPopup = it
                prefs.edit().putBoolean("toolbar_auto_popup", it).apply()
            },
            suppressOnPageLoad = toolbarSuppressOnPageLoad,
            onSuppressOnPageLoadChange = {
                toolbarSuppressOnPageLoad = it
                prefs.edit().putBoolean("toolbar_suppress_on_page_load", it).apply()
            },
            functionOrderStr = toolbarFunctionOrderStr,
            onFunctionOrderChange = {
                toolbarFunctionOrderStr = it
                prefs.edit().putString("toolbar_function_order", it).apply()
            },
            disabledFunctionsStr = toolbarDisabledFunctionsStr,
            onDisabledFunctionsChange = {
                toolbarDisabledFunctionsStr = it
                prefs.edit().putString("toolbar_disabled_functions", it).apply()
            },
            onDismiss = { showToolbarSettingsDialog = false }
        )
    }

    if (showAddSpeedDialDialog) {
        AddSpeedDialDialog(
            initialTitle = initialSpeedDialTitle,
            initialUrl = initialSpeedDialUrl,
            onDismiss = { showAddSpeedDialDialog = false },
            onConfirm = { title, url, subtitle ->
                showAddSpeedDialDialog = false
                val cleanUrl = if (isUrlInput(url)) {
                    if (url.startsWith("http://") || url.startsWith("https://")) url else "https://$url"
                } else {
                    url
                }
                val hostSubtitle = try {
                    android.net.Uri.parse(cleanUrl).host ?: cleanUrl
                } catch (_: Exception) {
                    cleanUrl
                }
                val newItem = SpeedDialItem(
                    title = title.ifBlank { cleanUrl },
                    url = cleanUrl,
                    subtitle = subtitle.ifBlank { hostSubtitle }
                )
                if (speedDialList.none { it.url == cleanUrl }) {
                    speedDialList = speedDialList + newItem
                    Toast.makeText(context, "Added to Speed Dial", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Speed dial entry already exists", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    Box(
        modifier = Modifier.fillMaxSize().preferredHighestRefreshRate()
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
                    OasisSpeedDialView(
                        entries = speedDialList,
                        onEntryClick = { entry ->
                            activeTab.url = entry.url
                            activeTab.title = entry.title
                            currentUrl = entry.url
                            addressInputState = TextFieldValue(text = entry.url, selection = TextRange.Zero)
                            val wv = getOrCreateWebView(activeTab)
                            wv.loadUrl(entry.url)
                        },
                        onAddEntryClick = {
                            initialSpeedDialTitle = ""
                            initialSpeedDialUrl = ""
                            showAddSpeedDialDialog = true
                        },
                        onDeleteEntryClick = { entryToDelete ->
                            speedDialList = speedDialList.filter { it != entryToDelete }
                            Toast.makeText(context, "Removed from Speed Dial", Toast.LENGTH_SHORT).show()
                        }
                    )
                } else {
                    key(activeTabId) {
                        val srl = getOrCreateSwipeRefreshLayout(activeTab)
                        AndroidView(
                            factory = {
                                (srl.parent as? ViewGroup)?.removeView(srl)
                                srl
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

            // Browser Bottom Navigation Bar
            OasisBottomBar(
                addressInputState = addressInputState,
                onAddressValueChange = { addressInputState = it },
                historyList = historyList,
                canGoBack = canGoBack,
                canGoForward = canGoForward,
                tabsCount = tabsList.size,
                pagerState = pagerState,
                onBackClick = {
                    val wv = webViewsMap[activeTabId]
                    if (wv != null && wv.canGoBack()) wv.goBack()
                },
                onForwardClick = {
                    val wv = webViewsMap[activeTabId]
                    if (wv != null && wv.canGoForward()) wv.goForward()
                },
                onNavigate = { navigateTo(it) },
                onTabsClick = { showTabsDialog = true },
                onNewTab = {
                    val newTab = BrowserTab(title = "New Tab", url = "")
                    tabsList = tabsList + newTab
                    activeTabId = newTab.id
                    // No Haven navigation here
                },
                onSpeedDialHomeClick = {
                    activeTab.url = ""
                    activeTab.title = "Speed Dial"
                    addressInputState = TextFieldValue("")
                },
                onAddSpeedDialClick = {
                    initialSpeedDialUrl = activeTab.url
                    initialSpeedDialTitle = if (activeTab.url.isNotBlank()) pageTitle else ""
                    showAddSpeedDialDialog = true
                },
                onBookmarkPageClick = {
                    bookmarksList = bookmarksList + BookmarkItem(activeTab.url, pageTitle)
                    Toast.makeText(context, "Page bookmarked", Toast.LENGTH_SHORT).show()
                },
                onBookmarksClick = { showBookmarksDialog = true },
                onHistoryClick = { showHistoryDialog = true },
                onDownloadManagerClick = { showDownloadsDialog = true },
                onBrowserSettingsClick = { showSettingsDialog = true },
                onToolbarSettingsClick = { showToolbarSettingsDialog = true }
            )
        }
    }
}
