package com.mucephei.app.ui.screens

import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import com.mucephei.app.data.model.NoteTheme
import com.mucephei.app.data.model.ThemePreset
import com.mucephei.app.data.repository.NoteRepository
import com.mucephei.app.sync.P2pSyncEngine
import kotlinx.coroutines.launch

/**
 * Main Pager Container managing 3-page horizontal navigation:
 * - Page 0: Capsule (Notes Feed & main controls)
 * - Page 1: Haven (Clean empty page to the right of Capsule)
 * - Page 2: Oasis (Clean empty page reachable by swiping right from Haven)
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MainPagerContainer(
    repository: NoteRepository,
    p2pEngine: P2pSyncEngine,
    initialWebUrl: String? = null,
    currentPreset: ThemePreset = ThemePreset.SUNSET_WARMTH,
    currentWallpaperUri: String? = null,
    currentBlurRadius: Float = 10f,
    currentWallpaperAlpha: Float = 0.72f,
    currentVectorOpacity: Float = 0.05f,
    isHideVectors: Boolean = false,
    isAutoNightMode: Boolean = false,
    fontScale: Float = 1.0f,
    isGridView: Boolean = false,
    isGlassifyEnabled: Boolean = true,
    onNoteSelected: (String) -> Unit,
    onCreateNewNote: () -> Unit,
    onOpenBackupDialog: () -> Unit,
    onCustomThemeExtracted: (NoteTheme) -> Unit,
    onPresetSelected: (ThemePreset) -> Unit = {},
    onWallpaperChanged: (String?, Float) -> Unit = { _, _ -> },
    onWallpaperAlphaChanged: (Float) -> Unit = {},
    onVectorOpacityChanged: (Float) -> Unit = {},
    onHideVectorsToggled: (Boolean) -> Unit = {},
    onAutoNightModeToggled: (Boolean) -> Unit = {},
    onFontScaleChanged: (Float) -> Unit = {},
    onGridViewToggled: (Boolean) -> Unit = {},
    onGlassifyToggled: (Boolean) -> Unit = {},
    externalHavenRequest: HavenRequest? = null,
    onExternalRequestConsumed: () -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    var oasisInitialUrl by remember { mutableStateOf<String?>(initialWebUrl) }
    val pagerState = rememberPagerState(
        pageCount = { 3 },
        initialPage = if (initialWebUrl != null) 2 else 0
    )

    var isSwipeActive by remember { mutableStateOf(false) }
    var havenIncomingRequest by remember { mutableStateOf<HavenRequest?>(null) }
    var havenIncomingPrompt by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(externalHavenRequest) {
        if (externalHavenRequest != null) {
            havenIncomingRequest = externalHavenRequest
            onExternalRequestConsumed()
            pagerState.animateScrollToPage(1)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { _ ->
                        // Allow swiping from literally everywhere across the screen for Capsule (0) and Haven (1).
                        // Oasis (2) handles its own swiping at the bottom toolbar.
                        isSwipeActive = pagerState.currentPage != 2
                    },
                    onHorizontalDrag = { change, dragAmount ->
                        if (isSwipeActive && !pagerState.isScrollInProgress) {
                            change.consume()
                            scope.launch {
                                // Maximum sensitivity allowed by the operating system (lowest threshold = 5f)
                                val threshold = 5f
                                val targetPage = when (pagerState.currentPage) {
                                    0 -> if (dragAmount < -threshold) 1 else if (dragAmount > threshold) 2 else 0
                                    1 -> if (dragAmount < -threshold) 2 else if (dragAmount > threshold) 0 else 1
                                    else -> pagerState.currentPage
                                }
                                if (targetPage != pagerState.currentPage) {
                                    pagerState.animateScrollToPage(targetPage)
                                }
                            }
                        }
                    },
                    onDragEnd = { isSwipeActive = false },
                    onDragCancel = { isSwipeActive = false }
                )
            }
    ) {
        HorizontalPager(
            state = pagerState,
            userScrollEnabled = false,
            beyondViewportPageCount = 2,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            when (page) {
                0 -> {
                    Capsule(
                        repository = repository,
                        p2pEngine = p2pEngine,
                        currentPreset = currentPreset,
                        currentWallpaperUri = currentWallpaperUri,
                        currentBlurRadius = currentBlurRadius,
                        currentWallpaperAlpha = currentWallpaperAlpha,
                        currentVectorOpacity = currentVectorOpacity,
                        isHideVectors = isHideVectors,
                        isAutoNightMode = isAutoNightMode,
                        fontScale = fontScale,
                        isGridView = isGridView,
                        isGlassifyEnabled = isGlassifyEnabled,
                        onNoteSelected = onNoteSelected,
                        onCreateNewNote = onCreateNewNote,
                        onOpenBackupDialog = onOpenBackupDialog,
                        onCustomThemeExtracted = onCustomThemeExtracted,
                        onPresetSelected = onPresetSelected,
                        onWallpaperChanged = onWallpaperChanged,
                        onWallpaperAlphaChanged = onWallpaperAlphaChanged,
                        onVectorOpacityChanged = onVectorOpacityChanged,
                        onHideVectorsToggled = onHideVectorsToggled,
                        onAutoNightModeToggled = onAutoNightModeToggled,
                        onFontScaleChanged = onFontScaleChanged,
                        onGridViewToggled = onGridViewToggled,
                        onGlassifyToggled = onGlassifyToggled,
                        onNavigateToHaven = { request ->
                            havenIncomingRequest = request
                            scope.launch {
                                pagerState.animateScrollToPage(1)
                            }
                        }
                    )
                }
                1 -> {
                    Haven(
                        pagerState = pagerState,
                        repository = repository,
                        incomingRequest = havenIncomingRequest,
                        incomingPrompt = havenIncomingPrompt,
                        onPromptConsumed = {
                            havenIncomingRequest = null
                            havenIncomingPrompt = null
                        },
                        onSearchWithOasis = { query ->
                            oasisInitialUrl = "https://www.google.com/search?q=${Uri.encode(query)}"
                            scope.launch {
                                pagerState.animateScrollToPage(2)
                            }
                        }
                    )
                }
                2 -> {
                    Oasis(
                        pagerState = pagerState,
                        repository = repository,
                        initialWebUrl = oasisInitialUrl,
                        onNavigateToHaven = { request ->
                            havenIncomingRequest = request
                            scope.launch {
                                pagerState.animateScrollToPage(1)
                            }
                        },
                        onWallpaperChanged = onWallpaperChanged
                    )
                }
            }
        }
    }
}
