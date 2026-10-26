package com.mucephei.app.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import com.mucephei.app.util.preferredHighestRefreshRate
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddReaction
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pin
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Sync
import com.mucephei.app.sync.P2pConnectionStatus
import com.mucephei.app.ui.components.LiveCollaborativeCanvasDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.mucephei.app.data.model.Note
import com.mucephei.app.data.model.NoteTheme
import com.mucephei.app.data.model.ReactionSticker
import com.mucephei.app.data.model.StickerType
import com.mucephei.app.data.model.ThemePreset
import com.mucephei.app.data.repository.NoteRepository
import com.mucephei.app.sync.P2pSyncEngine
import com.mucephei.app.theme.GarnetStarIcon
import com.mucephei.app.theme.HandwritingTypography
import com.mucephei.app.theme.sketchyBorder
import com.mucephei.app.ui.components.NoteFeedCard
import com.mucephei.app.ui.components.ThemedActionButton
import com.mucephei.app.ui.components.ThemedActionTextButton
import com.mucephei.app.ui.components.REACTION_OPTIONS
import com.mucephei.app.ui.components.VoiceRecorderWidget
import com.mucephei.app.ui.dialogs.AboutDialog
import kotlinx.coroutines.launch
import java.io.File

/**
 * Serene Capsule Screen (formerly MainFeedScreen):
 * - Top Bar with GarnetStarIcon emblem & Settings icon
 * - Quick Mic FAB & Camera FAB with stop/save toggle support and automatic permissions
 * - Reaction-specific filtering & category tabs
 * - Multi-select mode & Favorites (🌸) tab
 * - Modernized themed action buttons with proper spacing and alignment from right edge.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun Capsule(
    repository: NoteRepository,
    p2pEngine: P2pSyncEngine,
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
    onNavigateToHaven: ((HavenRequest) -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val syncState by p2pEngine.syncState.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilterTab by remember { mutableStateOf(0) } // 0: All, 1: Drafts, 2: Done, 3: Favorites (🌸), 4: Reaction Filter
    var selectedReactionFilter by remember { mutableStateOf(StickerType.HEART) }
    var showReactionDropdown by remember { mutableStateOf(false) }

    var isMultiSelectMode by remember { mutableStateOf(false) }
    var selectedNoteIds by remember { mutableStateOf(setOf<String>()) }
    var showSelectionMenu by remember { mutableStateOf(false) }

    androidx.activity.compose.BackHandler(enabled = isMultiSelectMode) {
        isMultiSelectMode = false
        selectedNoteIds = emptySet()
    }

    var showP2pDialog by remember { mutableStateOf(false) }
    var showCustomThemeDialog by remember { mutableStateOf(false) }
    var showLiveCanvasDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var isQuickVoiceRecording by remember { mutableStateOf(false) }
    var stopVoiceRecordingAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    // Camera & Video capture state & permission launchers
    var cameraCaptureUri by remember { mutableStateOf<Uri?>(null) }
    var videoCaptureUri by remember { mutableStateOf<Uri?>(null) }
    var showCameraMediaChooser by remember { mutableStateOf(false) }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && cameraCaptureUri != null) {
            scope.launch {
                val newNote = Note(title = "", bodyText = "")
                val saved = repository.attachImage(newNote, cameraCaptureUri!!)
                onNoteSelected(saved.id)
            }
        }
    }

    val videoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CaptureVideo()
    ) { success ->
        if (success && videoCaptureUri != null) {
            scope.launch {
                val newNote = Note(title = "", bodyText = "")
                val saved = repository.attachVideo(newNote, videoCaptureUri!!)
                onNoteSelected(saved.id)
            }
        }
    }

    fun launchPhotoCapture() {
        try {
            val photoFile = File(context.cacheDir, "cam_${System.currentTimeMillis()}.jpg")
            val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", photoFile)
            cameraCaptureUri = uri
            cameraLauncher.launch(uri)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun launchVideoCapture() {
        try {
            val videoFile = File(context.cacheDir, "vid_${System.currentTimeMillis()}.mp4")
            val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", videoFile)
            videoCaptureUri = uri
            videoLauncher.launch(uri)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            showCameraMediaChooser = true
        }
    }

    fun requestCameraAndCapture() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            showCameraMediaChooser = true
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // Audio mic permission launcher for quick voice note
    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            isQuickVoiceRecording = true
        }
    }

    fun requestMicAndRecord() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            isQuickVoiceRecording = true
        } else {
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    val allNotes by repository.allNotes.collectAsState(initial = emptyList())
    val activeNotes = remember(allNotes) { allNotes.filter { !it.deleted } }

    val filteredNotes = remember(activeNotes, searchQuery, selectedFilterTab, selectedReactionFilter) {
        val baseFiltered = activeNotes.filter { note ->
            searchQuery.isBlank() ||
                    note.title.contains(searchQuery, ignoreCase = true) ||
                    note.bodyText.contains(searchQuery, ignoreCase = true)
        }

        when (selectedFilterTab) {
            1 -> baseFiltered.filter { !it.isFinished } // Drafts
            2 -> baseFiltered.filter { it.isFinished }  // Done
            3 -> baseFiltered.filter { it.isFavorite }  // Favorites (🌸)
            4 -> baseFiltered.filter { note -> note.stickers.any { it.stickerType == selectedReactionFilter } } // Reaction filtered
            else -> baseFiltered
        }
    }

    if (showAboutDialog) {
        AboutDialog(onDismiss = { showAboutDialog = false })
    }

    if (showCameraMediaChooser) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showCameraMediaChooser = false },
            title = { Text("Capture Media", style = HandwritingTypography.titleLarge) },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    androidx.compose.material3.OutlinedButton(
                        onClick = {
                            showCameraMediaChooser = false
                            launchPhotoCapture()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Take Photo")
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    androidx.compose.material3.OutlinedButton(
                        onClick = {
                            showCameraMediaChooser = false
                            launchVideoCapture()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Record Video")
                    }
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { showCameraMediaChooser = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showP2pDialog) {
        P2pSyncDialog(
            p2pEngine = p2pEngine,
            onDismiss = { showP2pDialog = false }
        )
    }

    if (showCustomThemeDialog) {
        CustomThemeDialog(
            currentPreset = currentPreset,
            currentWallpaperUri = currentWallpaperUri,
            currentBlurRadius = currentBlurRadius,
            currentWallpaperAlpha = currentWallpaperAlpha,
            currentVectorOpacity = currentVectorOpacity,
            isHideVectors = isHideVectors,
            isAutoNightMode = isAutoNightMode,
            currentFontScale = fontScale,
            isGlassifyEnabled = isGlassifyEnabled,
            onThemeExtracted = { theme ->
                onCustomThemeExtracted(theme)
                showCustomThemeDialog = false
            },
            onPresetSelected = { preset ->
                onPresetSelected(preset)
                showCustomThemeDialog = false
            },
            onWallpaperChanged = onWallpaperChanged,
            onWallpaperAlphaChanged = onWallpaperAlphaChanged,
            onVectorOpacityChanged = onVectorOpacityChanged,
            onHideVectorsToggled = onHideVectorsToggled,
            onAutoNightModeToggled = onAutoNightModeToggled,
            onFontScaleChanged = onFontScaleChanged,
            onGlassifyToggled = onGlassifyToggled,
            onDismiss = { showCustomThemeDialog = false }
        )
    }

    if (showLiveCanvasDialog) {
        LiveCollaborativeCanvasDialog(
            p2pEngine = p2pEngine,
            onDismiss = { showLiveCanvasDialog = false }
        )
    }

    Scaffold(
        modifier = Modifier.fillMaxSize().preferredHighestRefreshRate(),
        topBar = {
            TopAppBar(
                title = {
                    if (isMultiSelectMode) {
                        Text(
                            text = "${selectedNoteIds.size} Selected",
                            style = HandwritingTypography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { showAboutDialog = true }
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            GarnetStarIcon(size = 32.dp, primaryColor = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "MuCephei",
                                style = HandwritingTypography.displayMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                },
                navigationIcon = {
                    if (isMultiSelectMode) {
                        Box(modifier = Modifier.padding(start = 8.dp)) {
                            ThemedActionButton(
                                onClick = {
                                    isMultiSelectMode = false
                                    selectedNoteIds = emptySet()
                                },
                                icon = Icons.Default.Clear,
                                contentDescription = "Close Selection",
                                tint = MaterialTheme.colorScheme.onSurface,
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                    }
                },
                actions = {
                    if (isMultiSelectMode) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(end = 12.dp)
                        ) {
                            // Quick Pin Action
                            ThemedActionButton(
                                onClick = {
                                    scope.launch {
                                        selectedNoteIds.forEach { id ->
                                            activeNotes.find { it.id == id }?.let { n ->
                                                repository.saveNote(n.copy(isPinned = !n.isPinned))
                                            }
                                        }
                                    }
                                },
                                icon = Icons.Default.PushPin,
                                contentDescription = "Pin / Unpin Selected",
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                            )

                            // Quick Delete Action
                            ThemedActionButton(
                                onClick = {
                                    scope.launch {
                                        selectedNoteIds.forEach { id ->
                                            repository.softDeleteNote(id)
                                        }
                                        isMultiSelectMode = false
                                        selectedNoteIds = emptySet()
                                    }
                                },
                                icon = Icons.Default.Delete,
                                contentDescription = "Delete Selected",
                                tint = Color.Red,
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                            )

                            // Three-Dots MoreVert Dropdown Menu
                            Box {
                                ThemedActionButton(
                                    onClick = { showSelectionMenu = true },
                                    icon = Icons.Default.MoreVert,
                                    contentDescription = "More Options",
                                    tint = MaterialTheme.colorScheme.primary,
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                                )
                                DropdownMenu(
                                    expanded = showSelectionMenu,
                                    onDismissRequest = { showSelectionMenu = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text(if (selectedNoteIds.size == activeNotes.size) "Deselect All" else "Select All") },
                                        onClick = {
                                            showSelectionMenu = false
                                            selectedNoteIds = if (selectedNoteIds.size == activeNotes.size) {
                                                emptySet()
                                            } else {
                                                activeNotes.map { it.id }.toSet()
                                            }
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Favorite / Unfavorite") },
                                        onClick = {
                                            showSelectionMenu = false
                                            scope.launch {
                                                selectedNoteIds.forEach { id ->
                                                    activeNotes.find { it.id == id }?.let { n ->
                                                        repository.saveNote(n.copy(isFavorite = !n.isFavorite))
                                                    }
                                                }
                                            }
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Toggle Public / Private") },
                                        onClick = {
                                            showSelectionMenu = false
                                            scope.launch {
                                                selectedNoteIds.forEach { id ->
                                                    activeNotes.find { it.id == id }?.let { n ->
                                                        repository.saveNote(n.copy(isPrivate = !n.isPrivate))
                                                    }
                                                }
                                            }
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Share Selected") },
                                        onClick = {
                                            showSelectionMenu = false
                                            val notesToShare = activeNotes.filter { selectedNoteIds.contains(it.id) }
                                            val combined = notesToShare.joinToString("\n\n---\n\n") { "${it.title}\n\n${it.bodyText}" }
                                            val sendIntent = Intent().apply {
                                                action = Intent.ACTION_SEND
                                                putExtra(Intent.EXTRA_TEXT, combined)
                                                type = "text/plain"
                                            }
                                            context.startActivity(Intent.createChooser(sendIntent, null))
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Copy Full Text") },
                                        onClick = {
                                            showSelectionMenu = false
                                            val notesToCopy = activeNotes.filter { selectedNoteIds.contains(it.id) }
                                            val combined = notesToCopy.joinToString("\n\n---\n\n") { "${it.title}\n\n${it.bodyText}" }
                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                            val clip = android.content.ClipData.newPlainText("Notes Content", combined)
                                            clipboard.setPrimaryClip(clip)
                                            Toast.makeText(context, "${notesToCopy.size} notes copied", Toast.LENGTH_SHORT).show()
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Analyze with AI Haven") },
                                        onClick = {
                                            showSelectionMenu = false
                                            val notesToAnalyze = activeNotes.filter { selectedNoteIds.contains(it.id) }
                                            val combined = notesToAnalyze.joinToString("\n\n---\n\n") { "${it.title}\n\n${it.bodyText}" }
                                            val request = HavenRequest.AnalyzeNote(
                                                title = "${notesToAnalyze.size} Selected Notes",
                                                bodyText = combined
                                            )
                                            onNavigateToHaven?.invoke(request)
                                        }
                                    )

                                }
                            }
                        }
                    } else {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(end = 12.dp)
                        ) {
                            // Live Collaborative Canvas Button (Appears only when P2P session is established)
                            if (syncState.status == P2pConnectionStatus.CONNECTED_COOP_ROOM) {
                                ThemedActionButton(
                                    onClick = { showLiveCanvasDialog = true },
                                    icon = Icons.Default.Brush,
                                    contentDescription = "Live Collaborative Canvas",
                                    tint = Color(0xFF2A9D8F),
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                                )
                            }

                            // Grid / List View Mode Toggle
                            ThemedActionButton(
                                onClick = { onGridViewToggled(!isGridView) },
                                icon = if (isGridView) Icons.AutoMirrored.Filled.ViewList else Icons.Default.GridView,
                                contentDescription = if (isGridView) "List View" else "Grid View",
                                tint = MaterialTheme.colorScheme.primary,
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                            )

                            // P2P Co-Op Sync Trigger
                            ThemedActionButton(
                                onClick = { showP2pDialog = true },
                                icon = Icons.Default.Sync,
                                contentDescription = "P2P Mesh",
                                tint = MaterialTheme.colorScheme.primary,
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                            )

                            // Settings & Theme Studio Launcher
                            ThemedActionButton(
                                onClick = { showCustomThemeDialog = true },
                                icon = Icons.Default.Settings,
                                contentDescription = "Settings",
                                tint = MaterialTheme.colorScheme.primary,
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        },
        containerColor = Color.Transparent
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 14.dp)
        ) {
            // Quick Voice Recording Widget Modal/Bar if active
            if (isQuickVoiceRecording) {
                Spacer(modifier = Modifier.height(4.dp))
                VoiceRecorderWidget(
                    onRecordingComplete = { audioItem ->
                        scope.launch {
                            val newNote = Note(
                                title = "",
                                bodyText = "",
                                media = listOf(audioItem)
                            )
                            repository.saveNote(newNote)
                            onNoteSelected(newNote.id)
                            isQuickVoiceRecording = false
                            stopVoiceRecordingAction = null
                        }
                    },
                    onCancel = {
                        isQuickVoiceRecording = false
                        stopVoiceRecordingAction = null
                    },
                    onStopReady = { stopAction ->
                        stopVoiceRecordingAction = stopAction
                    }
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Main Feed Area (Grid or List)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                if (filteredNotes.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = if (searchQuery.isNotBlank()) "No memories found for \"$searchQuery\".\nTap '+' below to create this note instantly." else "No memories found.\nTap '+' below to begin a quiet entry.",
                                style = HandwritingTypography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                            )
                        }
                    }
                } else if (isGridView) {
                    // Staggered Grid View Sorting Mode (fixes irregular spacing)
                    LazyVerticalStaggeredGrid(
                        columns = StaggeredGridCells.Fixed(2),
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalItemSpacing = 10.dp,
                        contentPadding = PaddingValues(bottom = 16.dp)
                    ) {
                        items(
                            items = filteredNotes,
                            key = { it.id }
                        ) { note ->
                            NoteFeedCard(
                                note = note,
                                isSelected = selectedNoteIds.contains(note.id),
                                isMultiSelectMode = isMultiSelectMode,
                                onClick = { onNoteSelected(note.id) },
                                onToggleSelect = {
                                    selectedNoteIds = if (selectedNoteIds.contains(note.id)) {
                                        selectedNoteIds - note.id
                                    } else {
                                        selectedNoteIds + note.id
                                    }
                                },
                                onReactNote = { stickerType, clickPosition ->
                                    scope.launch {
                                        val newReaction = ReactionSticker(
                                            stickerType = stickerType,
                                            position = clickPosition,
                                            placedBy = "User"
                                        )
                                        val updatedNote = note.copy(stickers = note.stickers + newReaction)
                                        repository.saveNote(updatedNote)
                                    }
                                },
                                onPinToggled = { pinned ->
                                    scope.launch {
                                        val updatedNote = note.copy(isPinned = pinned)
                                        repository.saveNote(updatedNote)
                                    }
                                },
                                onFavoriteToggled = { favorite ->
                                    scope.launch {
                                        val updatedNote = note.copy(isFavorite = favorite)
                                        repository.saveNote(updatedNote)
                                    }
                                },
                                onPrivacyToggled = { private ->
                                    scope.launch {
                                        val updatedNote = note.copy(isPrivate = private)
                                        repository.saveNote(updatedNote)
                                    }
                                },
                                onShare = {
                                    val sendIntent = Intent().apply {
                                        action = Intent.ACTION_SEND
                                        putExtra(Intent.EXTRA_TEXT, "${note.title}\n\n${note.bodyText}")
                                        type = "text/plain"
                                    }
                                    context.startActivity(Intent.createChooser(sendIntent, null))
                                },
                                onCopyContent = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                    val clip = android.content.ClipData.newPlainText("Note Content", "${note.title}\n\n${note.bodyText}")
                                    clipboard.setPrimaryClip(clip)
                                    Toast.makeText(context, "Note copied to clipboard", Toast.LENGTH_SHORT).show()
                                },
                                onSelectMultiple = {
                                    isMultiSelectMode = true
                                    selectedNoteIds = setOf(note.id)
                                },
                                onDelete = {
                                    scope.launch {
                                        repository.softDeleteNote(note.id)
                                    }
                                }
                            )
                        }
                    }
                } else {
                    // List View Sorting Mode
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(bottom = 16.dp)
                    ) {
                        items(
                            items = filteredNotes,
                            key = { it.id },
                            contentType = { "NoteCard" }
                        ) { note ->
                            NoteFeedCard(
                                note = note,
                                isSelected = selectedNoteIds.contains(note.id),
                                isMultiSelectMode = isMultiSelectMode,
                                onClick = { onNoteSelected(note.id) },
                                onToggleSelect = {
                                    selectedNoteIds = if (selectedNoteIds.contains(note.id)) {
                                        selectedNoteIds - note.id
                                    } else {
                                        selectedNoteIds + note.id
                                    }
                                },
                                onReactNote = { stickerType, clickPosition ->
                                    scope.launch {
                                        val newReaction = ReactionSticker(
                                            stickerType = stickerType,
                                            position = clickPosition,
                                            placedBy = "User"
                                        )
                                        val updatedNote = note.copy(stickers = note.stickers + newReaction)
                                        repository.saveNote(updatedNote)
                                    }
                                },
                                onPinToggled = { pinned ->
                                    scope.launch {
                                        val updatedNote = note.copy(isPinned = pinned)
                                        repository.saveNote(updatedNote)
                                    }
                                },
                                onFavoriteToggled = { favorite ->
                                    scope.launch {
                                        val updatedNote = note.copy(isFavorite = favorite)
                                        repository.saveNote(updatedNote)
                                    }
                                },
                                onPrivacyToggled = { private ->
                                    scope.launch {
                                        val updatedNote = note.copy(isPrivate = private)
                                        repository.saveNote(updatedNote)
                                    }
                                },
                                onShare = {
                                    val sendIntent = Intent().apply {
                                        action = Intent.ACTION_SEND
                                        putExtra(Intent.EXTRA_TEXT, "${note.title}\n\n${note.bodyText}")
                                        type = "text/plain"
                                    }
                                    context.startActivity(Intent.createChooser(sendIntent, null))
                                },
                                onCopyContent = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                    val clip = android.content.ClipData.newPlainText("Note Content", "${note.title}\n\n${note.bodyText}")
                                    clipboard.setPrimaryClip(clip)
                                    Toast.makeText(context, "Note copied to clipboard", Toast.LENGTH_SHORT).show()
                                },
                                onSelectMultiple = {
                                    isMultiSelectMode = true
                                    selectedNoteIds = setOf(note.id)
                                },
                                onDelete = {
                                    scope.launch {
                                        repository.softDeleteNote(note.id)
                                    }
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Bottom Dock: Categories & Reaction Filter & Search Bar
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding()
                    .padding(horizontal = 4.dp, vertical = 6.dp)
            ) {
                // Reaction Selector Popup
                if (showReactionDropdown) {
                    FlowRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        REACTION_OPTIONS.forEach { opt ->
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .clickable {
                                        selectedReactionFilter = opt.stickerType
                                        selectedFilterTab = 4
                                        showReactionDropdown = false
                                    }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(text = opt.emoji, fontSize = 22.sp)
                            }
                        }
                    }
                }

                // Note Categories Filter Tabs ("All", "Drafts", "Done", Favorites Icon, Reaction Filter)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val tabLabels = listOf("All", "Drafts", "Done")
                    tabLabels.forEachIndexed { index, label ->
                        val isSelected = selectedFilterTab == index
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(CircleShape)
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                                )
                                .clickable {
                                    selectedFilterTab = index
                                    showReactionDropdown = false
                                }
                                .padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                style = HandwritingTypography.labelMedium,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Favorites Tab (index 3)
                    val isFavSelected = selectedFilterTab == 3
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(CircleShape)
                            .background(
                                if (isFavSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                            )
                            .clickable {
                                selectedFilterTab = 3
                                showReactionDropdown = false
                            }
                            .padding(vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Favorite,
                            contentDescription = "Favorites",
                            tint = if (isFavSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    // Reaction Filter Tab (index 4)
                    val isReactionSelected = selectedFilterTab == 4
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(CircleShape)
                            .background(
                                if (isReactionSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                            )
                            .clickable {
                                selectedFilterTab = 4
                                showReactionDropdown = !showReactionDropdown
                            }
                            .padding(vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AddReaction,
                            contentDescription = "Reaction Filter",
                            tint = if (isReactionSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Bottom Dock Row: Search Bar + Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.weight(1f),
                        placeholder = {
                            Text(
                                text = "",
                                style = HandwritingTypography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Search",
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                            )
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(
                                        imageVector = Icons.Default.Clear,
                                        contentDescription = "Clear",
                                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                    )
                                }
                            }
                        },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        shape = RoundedCornerShape(24.dp),
                        singleLine = true
                    )

                    FloatingActionButton(
                        onClick = {
                            if (isQuickVoiceRecording) {
                                stopVoiceRecordingAction?.invoke()
                            } else {
                                requestMicAndRecord()
                            }
                        },
                        containerColor = if (isQuickVoiceRecording) Color(0xFFE76F51) else MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = if (isQuickVoiceRecording) Color.White else Color(0xFFE76F51),
                        shape = CircleShape,
                        modifier = Modifier
                            .size(46.dp)
                            .sketchyBorder(
                                color = Color(0xFFE76F51).copy(alpha = 0.3f),
                                strokeWidth = 1.dp,
                                cornerRadius = 23.dp
                            )
                    ) {
                        Icon(
                            imageVector = if (isQuickVoiceRecording) Icons.Default.Stop else Icons.Default.Mic,
                            contentDescription = if (isQuickVoiceRecording) "Stop Recording" else "Quick Voice Note",
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    FloatingActionButton(
                        onClick = { requestCameraAndCapture() },
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.primary,
                        shape = CircleShape,
                        modifier = Modifier
                            .size(46.dp)
                            .sketchyBorder(
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                                strokeWidth = 1.dp,
                                cornerRadius = 23.dp
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Default.CameraAlt,
                            contentDescription = "Camera Capture",
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    FloatingActionButton(
                        onClick = {
                            scope.launch {
                                if (searchQuery.isNotBlank() && filteredNotes.isEmpty()) {
                                    val newNote = Note(title = searchQuery.trim(), bodyText = "")
                                    repository.saveNote(newNote)
                                    onNoteSelected(newNote.id)
                                } else {
                                    onCreateNewNote()
                                }
                            }
                        },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        shape = CircleShape,
                        modifier = Modifier
                            .size(46.dp)
                            .sketchyBorder(
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                                strokeWidth = 1.dp,
                                cornerRadius = 23.dp
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "New Memory",
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }
    }
}
