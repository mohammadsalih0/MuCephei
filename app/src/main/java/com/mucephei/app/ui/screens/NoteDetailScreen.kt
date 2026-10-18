package com.mucephei.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Videocam
import com.mucephei.app.ui.components.ThemedActionButton
import com.mucephei.app.ui.components.ThemedActionTextButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.mucephei.app.data.model.MediaItem
import com.mucephei.app.data.model.MediaItemType
import com.mucephei.app.data.model.MediaMetadata
import com.mucephei.app.data.model.Note
import com.mucephei.app.data.repository.NoteRepository
import com.mucephei.app.sync.P2pSyncEngine
import com.mucephei.app.theme.HandwritingTypography
import com.mucephei.app.theme.PaletteEngine
import com.mucephei.app.theme.ThemePreferences
import com.mucephei.app.theme.sketchyBorder
import com.mucephei.app.ui.components.AudioMicroPlayer
import com.mucephei.app.ui.components.ImageLightboxDialog
import com.mucephei.app.ui.components.MediaItemOptionsSheet
import com.mucephei.app.ui.components.VideoPlayerDialog
import com.mucephei.app.ui.components.VoiceRecorderWidget
import com.mucephei.app.util.openDocumentOrMedia
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

/**
 * Serene Diary Note Editor:
 * - Click anywhere on screen to focus writing (body text field)
 * - Clean text field background without horizontal lines
 * - Bottom action toolbar for Media (Camera, Photo, Video, Voice recording, Documents, Sketch)
 * - Real-time P2P drawing co-op & clear canvas synchronization
 * - Private vs Shared note toggle switch
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun NoteDetailScreen(
    noteId: String?,
    repository: NoteRepository,
    p2pEngine: P2pSyncEngine,
    onBack: () -> Unit,
    onNavigateToHaven: ((HavenRequest) -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val bodyFocusRequester = remember { FocusRequester() }

    val themePrefs = remember { ThemePreferences(context) }
    val textTransparencyEnabled = themePrefs.textTransparencyEnabled
    val textTransparencyAlpha = themePrefs.textTransparencyAlpha

    val textFieldContainerColor = if (textTransparencyEnabled) {
        MaterialTheme.colorScheme.surface.copy(alpha = textTransparencyAlpha)
    } else {
        Color.Transparent
    }

    var currentNote by remember { mutableStateOf(Note(title = "", bodyText = "")) }
    var isDrawingMode by remember { mutableStateOf(false) }
    var isVoiceRecorderActive by remember { mutableStateOf(false) }

    var activeLightboxMedia by remember { mutableStateOf<MediaItem?>(null) }
    var activeVideoMedia by remember { mutableStateOf<MediaItem?>(null) }
    var selectedMediaForOptions by remember { mutableStateOf<MediaItem?>(null) }

    val drawingPaths = remember { mutableStateListOf<Pair<Path, Color>>() }
    var currentPath by remember { mutableStateOf<Path?>(null) }

    // Register P2P drawing callbacks for real-time co-op drawing
    DisposableEffect(p2pEngine, currentNote.id) {
        p2pEngine.onDrawingStrokeReceived = { targetNoteId, points, colorArgb ->
            if (targetNoteId == currentNote.id && points.isNotEmpty()) {
                val p = Path().apply {
                    moveTo(points.first().x, points.first().y)
                    for (i in 1 until points.size) {
                        lineTo(points[i].x, points[i].y)
                    }
                }
                drawingPaths.add(Pair(p, Color(colorArgb)))
            }
        }
        p2pEngine.onCanvasCleared = { targetNoteId ->
            if (targetNoteId == currentNote.id) {
                drawingPaths.clear()
            }
        }
        onDispose {
            p2pEngine.onDrawingStrokeReceived = null
            p2pEngine.onCanvasCleared = null
        }
    }

    // Camera capture state
    var cameraCaptureUri by remember { mutableStateOf<Uri?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && cameraCaptureUri != null) {
            scope.launch {
                val updated = repository.attachImage(currentNote, cameraCaptureUri!!)
                currentNote = updated
                p2pEngine.broadcastNoteDelta(updated)
            }
        }
    }



    // Rasterize and save freehand drawings into permanent MediaItem
    fun rasterizeAndSaveDrawingsNow() {
        if (drawingPaths.isNotEmpty()) {
            try {
                val width = 1080
                val height = 1440
                val bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
                val androidCanvas = android.graphics.Canvas(bitmap)
                androidCanvas.drawColor(android.graphics.Color.TRANSPARENT)

                drawingPaths.forEach { (composePath, color) ->
                    val paint = android.graphics.Paint().apply {
                        isAntiAlias = true
                        style = android.graphics.Paint.Style.STROKE
                        strokeWidth = 8f
                        strokeCap = android.graphics.Paint.Cap.ROUND
                        strokeJoin = android.graphics.Paint.Join.ROUND
                        this.color = color.toArgb()
                    }
                    androidCanvas.drawPath(composePath.asAndroidPath(), paint)
                }

                val dir = File(context.filesDir, "mucephei_drawings").apply { mkdirs() }
                val file = File(dir, "drawing_${System.currentTimeMillis()}.png")
                FileOutputStream(file).use { out ->
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
                }

                if (file.exists() && file.length() > 0) {
                    val mediaItem = MediaItem(
                        type = MediaItemType.DRAWING,
                        uri = file.absolutePath,
                        metadata = MediaMetadata(title = "Handwritten Sketch", fileName = file.name)
                    )
                    currentNote = currentNote.copy(media = currentNote.media + mediaItem)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            drawingPaths.clear()
        }
    }

    // Auto-save on system back button press
    androidx.activity.compose.BackHandler {
        rasterizeAndSaveDrawingsNow()
        scope.launch {
            repository.saveNote(currentNote)
            p2pEngine.broadcastNoteDelta(currentNote)
            onBack()
        }
    }

    // Auto-save when leaving or pausing screen
    DisposableEffect(currentNote, drawingPaths) {
        onDispose {
            rasterizeAndSaveDrawingsNow()
            if (currentNote.title.isNotBlank() || currentNote.bodyText.isNotBlank() || currentNote.media.isNotEmpty()) {
                scope.launch {
                    repository.saveNote(currentNote)
                }
            }
        }
    }

    // Load existing note once on entry
    LaunchedEffect(noteId) {
        if (!noteId.isNullOrBlank()) {
            val loaded = repository.getNoteById(noteId)
            if (loaded != null) {
                currentNote = loaded
            }
        }
    }

    // Photo picker launcher
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            scope.launch {
                val updated = repository.attachImageAndExtractTheme(currentNote, it)
                currentNote = updated
                p2pEngine.broadcastNoteDelta(updated)
            }
        }
    }



    // Video picker launcher
    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            scope.launch {
                val updated = repository.attachVideo(currentNote, it)
                currentNote = updated
                p2pEngine.broadcastNoteDelta(updated)
            }
        }
    }

    // Document picker launcher
    val docPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            scope.launch {
                val updated = repository.attachDocument(currentNote, it)
                currentNote = updated
                p2pEngine.broadcastNoteDelta(updated)
            }
        }
    }

    // Replacement picker launcher
    var replacingMediaItem by remember { mutableStateOf<MediaItem?>(null) }
    val replacementPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { newUri ->
            replacingMediaItem?.let { oldItem ->
                val updatedMedia = currentNote.media.map { item ->
                    if (item.id == oldItem.id) {
                        item.copy(uri = newUri.toString(), metadata = item.metadata.copy(fileName = newUri.lastPathSegment ?: "file"))
                    } else item
                }
                currentNote = currentNote.copy(media = updatedMedia)
                replacingMediaItem = null
            }
        }
    }

    // Mic permission launcher
    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            isVoiceRecorderActive = true
        }
    }

    fun requestMicPermissionAndRecord() {
        focusManager.clearFocus()
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            isVoiceRecorderActive = true
        } else {
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    val themeColors = currentNote.theme
    val surface = if (themeColors != null) PaletteEngine.hexToColor(themeColors.surfaceColorHex) else MaterialTheme.colorScheme.surface
    val accent = if (themeColors != null) PaletteEngine.hexToColor(themeColors.accentColorHex) else MaterialTheme.colorScheme.primary
    val textColor = if (themeColors != null) PaletteEngine.hexToColor(themeColors.textColorHex) else MaterialTheme.colorScheme.onSurface

    // Lightbox, Video, & Media Options Dialogs
    activeLightboxMedia?.let { media ->
        ImageLightboxDialog(mediaItem = media, onDismiss = { activeLightboxMedia = null })
    }
    activeVideoMedia?.let { video ->
        VideoPlayerDialog(mediaItem = video, onDismiss = { activeVideoMedia = null })
    }
    selectedMediaForOptions?.let { media ->
        MediaItemOptionsSheet(
            mediaItem = media,
            onDismiss = { selectedMediaForOptions = null },
            onReplace = {
                replacingMediaItem = media
                selectedMediaForOptions = null
                replacementPickerLauncher.launch("*/*")
            },
            onDelete = {
                val updatedMedia = currentNote.media.filter { it.id != media.id }
                currentNote = currentNote.copy(media = updatedMedia)
                selectedMediaForOptions = null
            },
            onRename = { newTitle ->
                val updatedMedia = currentNote.media.map { item ->
                    if (item.id == media.id) {
                        item.copy(metadata = item.metadata.copy(title = newTitle, fileName = newTitle))
                    } else item
                }
                currentNote = currentNote.copy(media = updatedMedia)
                selectedMediaForOptions = null
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (currentNote.title.isBlank()) "Journal Entry" else currentNote.title,
                        style = HandwritingTypography.titleLarge,
                        color = textColor,
                        maxLines = 1
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        rasterizeAndSaveDrawingsNow()
                        scope.launch {
                            repository.saveNote(currentNote)
                            p2pEngine.broadcastNoteDelta(currentNote)
                            onBack()
                        }
                    }) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = textColor
                        )
                    }
                },
                actions = {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(end = 12.dp)
                    ) {
                        // AI Analysis / Send to Haven Action Button
                        if (onNavigateToHaven != null) {
                            ThemedActionButton(
                                onClick = {
                                    onNavigateToHaven.invoke(
                                        HavenRequest.AnalyzeNote(
                                            title = currentNote.title.ifBlank { "Untitled Note" },
                                            bodyText = currentNote.bodyText
                                        )
                                    )
                                },
                                icon = Icons.Default.AutoAwesome,
                                contentDescription = "Ask AI Haven",
                                tint = accent,
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                            )
                        }

                        // Privacy Toggle Button (Private vs Shared)
                        ThemedActionButton(
                            onClick = {
                                currentNote = currentNote.copy(isPrivate = !currentNote.isPrivate)
                            },
                            icon = if (currentNote.isPrivate) Icons.Default.Lock else Icons.Default.Public,
                            contentDescription = if (currentNote.isPrivate) "Private Note" else "Shared Note",
                            tint = accent,
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                        )

                        // Status Toggle Button (Draft vs Ready)
                        ThemedActionButton(
                            onClick = {
                                currentNote = currentNote.copy(isFinished = !currentNote.isFinished)
                            },
                            icon = if (currentNote.isFinished) Icons.Default.Check else Icons.Default.Edit,
                            contentDescription = if (currentNote.isFinished) "Ready Note" else "Draft Note",
                            tint = accent,
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                        )

                        // Pin / Unpin Action Button
                        ThemedActionButton(
                            onClick = {
                                currentNote = currentNote.copy(isPinned = !currentNote.isPinned)
                            },
                            icon = Icons.Default.PushPin,
                            contentDescription = if (currentNote.isPinned) "Unpin Note" else "Pin Note",
                            tint = accent,
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                        )

                        // Delete Note Action Button
                        ThemedActionButton(
                            onClick = {
                                scope.launch {
                                    repository.softDeleteNote(currentNote.id)
                                    onBack()
                                }
                            },
                            icon = Icons.Default.Delete,
                            contentDescription = "Delete Note",
                            tint = Color.Red,
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        containerColor = Color.Transparent
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .padding(horizontal = 14.dp, vertical = 6.dp)
        ) {
            // Main Serene Clean Note Box (Click anywhere to write, no background lines)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(surface)
                    .sketchyBorder(color = accent.copy(alpha = 0.30f), strokeWidth = 1.dp)
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) {
                        bodyFocusRequester.requestFocus()
                        keyboardController?.show()
                    }
            ) {
                // Interactive Text & Media Content Column (No horizontal notebook lines)
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 18.dp, vertical = 14.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    // Handwritten Title Input
                    TextField(
                        value = currentNote.title,
                        onValueChange = { newTitle ->
                            currentNote = currentNote.copy(title = newTitle)
                        },
                        placeholder = {
                            Text(
                                text = "Entry Title...",
                                style = HandwritingTypography.displayMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.40f)
                            )
                        },
                        textStyle = HandwritingTypography.displayMedium.copy(
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        singleLine = true,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = textFieldContainerColor,
                            unfocusedContainerColor = textFieldContainerColor,
                            disabledContainerColor = textFieldContainerColor,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // Handwritten Body Input Field (Click anywhere to write & focus)
                    androidx.compose.foundation.text.selection.SelectionContainer {
                        TextField(
                            value = currentNote.bodyText,
                            onValueChange = { newBody ->
                                currentNote = currentNote.copy(bodyText = newBody)
                            },
                            placeholder = {
                                Text(
                                    text = "Write your quiet thoughts, memories, and stories here...",
                                    style = HandwritingTypography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
                                )
                            },
                            textStyle = HandwritingTypography.bodyLarge.copy(
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = textFieldContainerColor,
                                unfocusedContainerColor = textFieldContainerColor,
                                disabledContainerColor = textFieldContainerColor,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 200.dp)
                                .focusRequester(bodyFocusRequester)
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Media Items Display Section inside Note
                    if (currentNote.media.isNotEmpty()) {
                        Text(
                            text = "Attached Media & Memories (Tap or hold for options):",
                            style = HandwritingTypography.labelLarge,
                            color = accent
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        // Audio Media Items
                        val audioMedia = currentNote.media.filter { it.type == MediaItemType.AUDIO }
                        audioMedia.forEach { audioItem ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                AudioMicroPlayer(
                                    mediaItem = audioItem,
                                    accentColor = accent,
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(vertical = 4.dp)
                                )
                                IconButton(onClick = { selectedMediaForOptions = audioItem }) {
                                    Icon(imageVector = Icons.Default.MoreVert, contentDescription = "Options", tint = accent)
                                }
                            }
                        }

                        // Video Media Items
                        val videoMedia = currentNote.media.filter { it.type == MediaItemType.VIDEO }
                        videoMedia.forEach { videoItem ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(180.dp)
                                    .padding(vertical = 4.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color.Black.copy(alpha = 0.8f))
                                    .combinedClickable(
                                        onClick = { activeVideoMedia = videoItem },
                                        onLongClick = { selectedMediaForOptions = videoItem }
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (!videoItem.metadata.thumbnailUri.isNullOrBlank()) {
                                    AsyncImage(
                                        model = videoItem.metadata.thumbnailUri,
                                        contentDescription = "Video Thumbnail",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color.Black.copy(alpha = 0.35f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PlayCircle,
                                        contentDescription = "Play Video",
                                        tint = Color.White,
                                        modifier = Modifier.size(54.dp)
                                    )
                                }
                                IconButton(
                                    onClick = { selectedMediaForOptions = videoItem },
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .background(Color.Black.copy(alpha = 0.4f), CircleShape)
                                        .padding(2.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.MoreVert, contentDescription = "Options", tint = Color.White)
                                }
                            }
                        }

                        // Image Media Items
                        val imageMedia = currentNote.media.filter { it.type == MediaItemType.PHOTO || it.type == MediaItemType.DRAWING }
                        imageMedia.forEach { img ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(180.dp)
                                    .padding(vertical = 4.dp)
                                    .clip(RoundedCornerShape(12.dp))
                            ) {
                                AsyncImage(
                                    model = img.uri,
                                    contentDescription = "Image preview",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .combinedClickable(
                                            onClick = { activeLightboxMedia = img },
                                            onLongClick = { selectedMediaForOptions = img }
                                        )
                                )
                                IconButton(
                                    onClick = { selectedMediaForOptions = img },
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .background(Color.Black.copy(alpha = 0.4f), CircleShape)
                                        .padding(2.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.MoreVert, contentDescription = "Options", tint = Color.White)
                                }
                            }
                        }

                        // Document Media Items
                        val docMedia = currentNote.media.filter { it.type == MediaItemType.DOCUMENT }
                        docMedia.forEach { doc ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(accent.copy(alpha = 0.10f))
                                    .combinedClickable(
                                        onClick = { openDocumentOrMedia(context, doc) },
                                        onLongClick = { selectedMediaForOptions = doc }
                                    )
                                    .padding(horizontal = 12.dp, vertical = 5.dp)
                            ) {
                                Text(
                                    text = doc.metadata.fileName ?: doc.metadata.title ?: "Document File",
                                    style = HandwritingTypography.labelMedium,
                                    color = accent,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(onClick = { selectedMediaForOptions = doc }) {
                                    Icon(imageVector = Icons.Default.MoreVert, contentDescription = "Options", tint = accent)
                                }
                            }
                        }
                    }
                }

                // Freehand Drawing Overlay with real-time P2P co-op sync
                val currentStrokePoints = remember { mutableStateListOf<Offset>() }

                if (isDrawingMode) {
                    Canvas(
                        modifier = Modifier
                            .matchParentSize()
                            .pointerInput(Unit) {
                                detectDragGestures(
                                    onDragStart = { offset ->
                                        currentStrokePoints.clear()
                                        currentStrokePoints.add(offset)
                                        val p = Path().apply { moveTo(offset.x, offset.y) }
                                        currentPath = p
                                    },
                                    onDrag = { change, _ ->
                                        currentStrokePoints.add(change.position)
                                        currentPath?.let { p ->
                                            val updated = Path().apply {
                                                addPath(p)
                                                lineTo(change.position.x, change.position.y)
                                            }
                                            currentPath = updated
                                        }
                                        p2pEngine.broadcastCursorPosition(
                                            noteId = currentNote.id,
                                            x = change.position.x,
                                            y = change.position.y,
                                            isDrawing = true
                                        )
                                    },
                                    onDragEnd = {
                                        currentPath?.let { path ->
                                            drawingPaths.add(Pair(path, accent))
                                            if (currentStrokePoints.isNotEmpty()) {
                                                p2pEngine.broadcastDrawingStroke(currentNote.id, currentStrokePoints.toList(), accent.toArgb())
                                            }
                                        }
                                        currentPath = null
                                        currentStrokePoints.clear()
                                    }
                                )
                            }
                    ) {
                        drawingPaths.forEach { (path, color) ->
                            drawPath(path = path, color = color, style = Stroke(width = 3.dp.toPx()))
                        }
                        currentPath?.let { path ->
                            drawPath(path = path, color = accent, style = Stroke(width = 3.dp.toPx()))
                        }
                    }
                }
            }

            // Interactive Voice Recording Widget
            if (isVoiceRecorderActive) {
                Spacer(modifier = Modifier.height(8.dp))
                VoiceRecorderWidget(
                    onRecordingComplete = { audioItem ->
                        currentNote = currentNote.copy(media = currentNote.media + audioItem)
                        isVoiceRecorderActive = false
                    },
                    onCancel = { isVoiceRecorderActive = false }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // BOTTOM ACTION TOOLBAR
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(surface.copy(alpha = 0.85f))
                    .sketchyBorder(color = accent.copy(alpha = 0.25f), strokeWidth = 1.dp)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Camera Capture Button
                ThemedActionButton(
                    onClick = {
                        focusManager.clearFocus()
                        try {
                            val photoFile = File(context.cacheDir, "cam_${System.currentTimeMillis()}.jpg")
                            val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", photoFile)
                            cameraCaptureUri = uri
                            cameraLauncher.launch(uri)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    },
                    icon = Icons.Default.CameraAlt,
                    contentDescription = "Capture Photo",
                    tint = accent,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                )

                // Photo Attachment Button
                ThemedActionButton(
                    onClick = {
                        focusManager.clearFocus()
                        imagePickerLauncher.launch("image/*")
                    },
                    icon = Icons.Default.Image,
                    contentDescription = "Attach Image",
                    tint = accent,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                )

                // Video Attachment Button
                ThemedActionButton(
                    onClick = {
                        focusManager.clearFocus()
                        videoPickerLauncher.launch("video/*")
                    },
                    icon = Icons.Default.Videocam,
                    contentDescription = "Attach Video",
                    tint = accent,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                )

                // Attach Document Button
                ThemedActionButton(
                    onClick = {
                        focusManager.clearFocus()
                        docPickerLauncher.launch("*/*")
                    },
                    icon = Icons.Default.Description,
                    contentDescription = "Attach Document",
                    tint = accent,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                )

                // Voice Recording Button
                ThemedActionButton(
                    onClick = {
                        requestMicPermissionAndRecord()
                    },
                    icon = Icons.Default.Mic,
                    contentDescription = "Voice Memo",
                    tint = accent,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                )

                // Drawing Mode Toggle
                ThemedActionButton(
                    onClick = {
                        focusManager.clearFocus()
                        isDrawingMode = !isDrawingMode
                    },
                    icon = Icons.Default.Brush,
                    contentDescription = "Drawing Mode",
                    tint = if (isDrawingMode) Color(0xFFE76F51) else accent,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                )

                // Clear Canvas Button (when drawing mode is active)
                if (isDrawingMode) {
                    ThemedActionButton(
                        onClick = {
                            drawingPaths.clear()
                            p2pEngine.broadcastClearCanvas(currentNote.id)
                        },
                        icon = Icons.Default.Clear,
                        contentDescription = "Clear Canvas",
                        tint = Color.Red,
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}
