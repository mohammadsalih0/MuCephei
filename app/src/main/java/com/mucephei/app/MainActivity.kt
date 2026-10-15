package com.mucephei.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import coil.compose.AsyncImage
import com.google.gson.Gson
import com.mucephei.app.data.model.MediaItem
import com.mucephei.app.data.model.MediaItemType
import com.mucephei.app.data.model.MediaMetadata
import com.mucephei.app.data.model.Note
import com.mucephei.app.data.model.NoteTheme
import com.mucephei.app.data.model.ThemePreset
import com.mucephei.app.sync.ConnectionManager
import com.mucephei.app.sync.ConnectionProfile
import com.mucephei.app.sync.OpenVpnEngine
import com.mucephei.app.theme.HandwritingTypography
import com.mucephei.app.theme.PaletteEngine
import com.mucephei.app.theme.ThemePreferences
import com.mucephei.app.ui.screens.EncryptedBackupDialog
import com.mucephei.app.ui.screens.Capsule
import com.mucephei.app.ui.screens.HavenRequest
import com.mucephei.app.ui.screens.MainPagerContainer
import com.mucephei.app.ui.screens.NoteDetailScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.Calendar

class MainActivity : ComponentActivity() {

    var sharedNoteId by mutableStateOf<String?>(null)
        private set

    var sharedWebUrl by mutableStateOf<String?>(null)
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Request maximum refresh rate (up to 144Hz) asynchronously after view attachment for buttery smooth animations without blocking onCreate
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            window.decorView.post {
                window.decorView.display?.let { display ->
                    val highestMode = display.supportedModes.maxByOrNull { it.refreshRate }
                    if (highestMode != null && highestMode.refreshRate >= 110f) {
                        window.attributes = window.attributes.apply {
                            preferredDisplayModeId = highestMode.modeId
                        }
                    }
                }
            }
        }

        handleIncomingIntent(intent)

        val themePrefs = ThemePreferences(this)
        val app = application as MuCepheiApplication

        setContent {
            val isSystemDark = isSystemInDarkTheme()
            val currentHour = remember { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }
            val isNightTime = remember(currentHour) { currentHour >= 19 || currentHour < 7 }

            var selectedPreset by remember { mutableStateOf(themePrefs.selectedPreset) }
            var customTheme by remember { mutableStateOf(themePrefs.customTheme) }
            val wallpaperPath by themePrefs.wallpaperFlow.collectAsState()
            var blurRadius by remember { mutableFloatStateOf(themePrefs.blurRadius) }
            var wallpaperAlpha by remember { mutableFloatStateOf(themePrefs.wallpaperAlpha) }
            var vectorOpacity by remember { mutableFloatStateOf(themePrefs.vectorOpacity) }
            var hideVectors by remember { mutableStateOf(themePrefs.hideVectors) }
            var isAutoNightMode by remember { mutableStateOf(themePrefs.autoNightModeEnabled) }
            var fontScale by remember { mutableFloatStateOf(themePrefs.fontScale) }
            var isGridView by remember { mutableStateOf(themePrefs.isGridView) }
            var glassifyEnabled by remember { mutableStateOf(themePrefs.glassifyEnabled) }

            // Dynamic Auto-Night Mode switch
            val activePreset = remember(selectedPreset, isAutoNightMode, isSystemDark, isNightTime) {
                if (isAutoNightMode && (isSystemDark || isNightTime) && selectedPreset == ThemePreset.SUNSET_WARMTH) {
                    ThemePreset.DEFAULT_DARK
                } else {
                    selectedPreset
                }
            }

            val colorScheme = remember(customTheme, activePreset, isSystemDark, wallpaperPath, glassifyEnabled) {
                PaletteEngine.buildColorScheme(
                    theme = customTheme,
                    preset = activePreset,
                    isSystemDark = isSystemDark,
                    hasWallpaper = !wallpaperPath.isNullOrBlank(),
                    glassifyEnabled = glassifyEnabled
                )
            }

            // Sync System Status & Navigation Bar colors with active Theme ColorScheme & Wallpaper
            SideEffect {
                val isDarkBg = colorScheme.background.toArgb().let { argb ->
                    (0.299 * ((argb shr 16) and 0xFF) + 0.587 * ((argb shr 8) and 0xFF) + 0.114 * (argb and 0xFF)) < 128
                }

                val systemBarColor = if (wallpaperPath != null) {
                    android.graphics.Color.TRANSPARENT
                } else {
                    colorScheme.background.toArgb()
                }

                enableEdgeToEdge(
                    statusBarStyle = if (isDarkBg) {
                        SystemBarStyle.dark(systemBarColor)
                    } else {
                        SystemBarStyle.light(systemBarColor, systemBarColor)
                    },
                    navigationBarStyle = if (isDarkBg) {
                        SystemBarStyle.dark(systemBarColor)
                    } else {
                        SystemBarStyle.light(systemBarColor, systemBarColor)
                    }
                )
            }

            // Apply font density scale multiplier
            val fontDensity = LocalDensity.current
            val customDensity = remember(fontDensity, fontScale) {
                Density(
                    density = fontDensity.density,
                    fontScale = fontDensity.fontScale * fontScale
                )
            }

            // Detect LTR vs RTL language orientation automatically
            val isRtl = android.text.TextUtils.getLayoutDirectionFromLocale(java.util.Locale.getDefault()) == android.view.View.LAYOUT_DIRECTION_RTL
            val appLayoutDirection = if (isRtl) LayoutDirection.Rtl else LayoutDirection.Ltr

            CompositionLocalProvider(
                LocalDensity provides customDensity,
                LocalLayoutDirection provides appLayoutDirection
            ) {
                MaterialTheme(
                    colorScheme = colorScheme,
                    typography = HandwritingTypography
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        // 1. Base theme background color fill (skipped if custom wallpaper is active to eliminate GPU overdraw)
                        if (wallpaperPath == null) {
                            Surface(
                                modifier = Modifier.fillMaxSize(),
                                color = MaterialTheme.colorScheme.background
                            ) {}
                        }

                        // 2. Custom background wallpaper layer (visible everywhere behind content)
                        wallpaperPath?.let { uri ->
                            AsyncImage(
                                model = uri,
                                contentDescription = "Custom background",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .then(
                                        if (blurRadius > 0f) {
                                            Modifier.blur(blurRadius.dp)
                                        } else {
                                            Modifier
                                        }
                                    )
                            )
                        }

                        // 3. Theme Vector Background artwork on top of wallpaper/background (Auto-hidden when custom wallpaper is set or hidden by user)
                        val showVectors = wallpaperPath == null && !hideVectors
                        com.mucephei.app.theme.ThemeVectorBackground(
                            preset = activePreset,
                            accentColor = MaterialTheme.colorScheme.primary,
                            vectorOpacity = vectorOpacity,
                            visible = showVectors
                        )

                        // 4. Main App Navigation Surface
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            color = if (wallpaperPath != null) {
                                MaterialTheme.colorScheme.background.copy(alpha = wallpaperAlpha)
                            } else {
                                Color.Transparent
                            }
                        ) {
                            MuCephiAppNavigation(
                                app = app,
                                initialSharedNoteId = sharedNoteId,
                                initialWebUrl = sharedWebUrl,
                                onSharedNoteConsumed = { sharedNoteId = null },
                                currentPreset = selectedPreset,
                                currentWallpaperUri = wallpaperPath,
                                currentBlurRadius = blurRadius,
                                currentWallpaperAlpha = wallpaperAlpha,
                                currentVectorOpacity = vectorOpacity,
                                isHideVectors = hideVectors,
                                isAutoNightMode = isAutoNightMode,
                                fontScale = fontScale,
                                isGridView = isGridView,
                                isGlassifyEnabled = glassifyEnabled,
                                onCustomThemeExtracted = { extracted ->
                                    customTheme = extracted
                                    selectedPreset = extracted.selectedPreset
                                    // wallpaperPath updated via flow
                                    themePrefs.customTheme = extracted
                                    themePrefs.selectedPreset = extracted.selectedPreset
                                    themePrefs.wallpaperPath = extracted.customImageUri
                                    if (extracted.customImageUri != null) {
                                        hideVectors = true
                                        themePrefs.hideVectors = true
                                    }
                                },
                                onPresetSelected = { preset ->
                                    selectedPreset = preset
                                    customTheme = null
                                    themePrefs.selectedPreset = preset
                                    themePrefs.customTheme = null
                                },
                                onWallpaperChanged = { path, radius ->
                                    val isNewWallpaper = wallpaperPath == null && path != null
                                    val newRadius = if (isNewWallpaper) 0f else radius
                                    val newAlpha = if (isNewWallpaper) 0f else wallpaperAlpha
                                    blurRadius = newRadius
                                    wallpaperAlpha = newAlpha
                                    themePrefs.wallpaperPath = path
                                    themePrefs.blurRadius = newRadius
                                    themePrefs.wallpaperAlpha = newAlpha
                                    if (path != null) {
                                        hideVectors = true
                                        themePrefs.hideVectors = true
                                    }
                                },
                                onWallpaperAlphaChanged = { alpha ->
                                    wallpaperAlpha = alpha
                                    themePrefs.wallpaperAlpha = alpha
                                },
                                onVectorOpacityChanged = { opacity ->
                                    vectorOpacity = opacity
                                    themePrefs.vectorOpacity = opacity
                                },
                                onHideVectorsToggled = { hide ->
                                    hideVectors = hide
                                    themePrefs.hideVectors = hide
                                },
                                onAutoNightModeToggled = { enabled ->
                                    isAutoNightMode = enabled
                                    themePrefs.autoNightModeEnabled = enabled
                                },
                                onFontScaleChanged = { scale ->
                                    fontScale = scale
                                    themePrefs.fontScale = scale
                                },
                                onGridViewToggled = { grid ->
                                    isGridView = grid
                                    themePrefs.isGridView = grid
                                },
                                onGlassifyToggled = { enabled ->
                                    glassifyEnabled = enabled
                                    themePrefs.glassifyEnabled = enabled
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent == null) return
        val app = application as MuCepheiApplication
        when (intent.action) {
            Intent.ACTION_VIEW -> {
                intent.data?.let { uri ->
                    sharedWebUrl = uri.toString()
                }
            }
            Intent.ACTION_SEND -> {
                val type = intent.type ?: ""
                if (type.startsWith("text/")) {
                    val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
                    val subject = intent.getStringExtra(Intent.EXTRA_SUBJECT)
                    val title = subject ?: ""
                    if (!sharedText.isNullOrBlank()) {
                        lifecycleScope.launch(Dispatchers.IO) {
                            val note = Note(title = title, bodyText = sharedText)
                            app.repository.saveNote(note)
                            withContext(Dispatchers.Main) {
                                sharedNoteId = note.id
                                Toast.makeText(this@MainActivity, "Shared text saved as note", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                } else {
                    val uri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                    if (uri != null) {
                        lifecycleScope.launch(Dispatchers.IO) {
                            try {
                                val mediaItem = copyUriToMediaDirectory(uri, type)
                                if (mediaItem != null) {
                                    val note = Note(
                                        title = "",
                                        bodyText = intent.getStringExtra(Intent.EXTRA_TEXT) ?: "",
                                        media = listOf(mediaItem)
                                    )
                                    app.repository.saveNote(note)
                                    withContext(Dispatchers.Main) {
                                        sharedNoteId = note.id
                                        Toast.makeText(this@MainActivity, "Shared file attached to note", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    }
                }
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                val uris = intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
                if (!uris.isNullOrEmpty()) {
                    val type = intent.type ?: "*/*"
                    lifecycleScope.launch(Dispatchers.IO) {
                        try {
                            val mediaItems = mutableListOf<MediaItem>()
                            uris.forEach { uri ->
                                copyUriToMediaDirectory(uri, type)?.let { mediaItems.add(it) }
                            }
                            if (mediaItems.isNotEmpty()) {
                                val note = Note(
                                    title = "",
                                    bodyText = intent.getStringExtra(Intent.EXTRA_TEXT) ?: "",
                                    media = mediaItems
                                )
                                app.repository.saveNote(note)
                                withContext(Dispatchers.Main) {
                                    sharedNoteId = note.id
                                    Toast.makeText(this@MainActivity, "Shared files attached to note", Toast.LENGTH_SHORT).show()
                                }
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
            }
        }
    }

    private fun copyUriToMediaDirectory(uri: Uri, mimeType: String): MediaItem? {
        val app = application as MuCepheiApplication
        val mediaDir = app.repository.mediaDirectory
        var originalName = "shared_${System.currentTimeMillis()}"
        try {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) {
                        originalName = cursor.getString(idx) ?: originalName
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        if (originalName.startsWith("shared_") && uri.lastPathSegment != null) {
            originalName = uri.lastPathSegment ?: originalName
        }

        val destFile = File(mediaDir, originalName)
        contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(destFile).use { output ->
                input.copyTo(output)
            }
        }

        if (!destFile.exists() || destFile.length() == 0L) return null

        val itemType = when {
            mimeType.startsWith("image/") -> MediaItemType.PHOTO
            mimeType.startsWith("video/") -> MediaItemType.VIDEO
            mimeType.startsWith("audio/") -> MediaItemType.AUDIO
            else -> MediaItemType.DOCUMENT
        }

        return MediaItem(
            type = itemType,
            uri = destFile.absolutePath,
            metadata = MediaMetadata(
                title = originalName,
                fileName = originalName,
                mimeType = mimeType,
                fileSize = destFile.length()
            )
        )
    }
}

@Composable
fun MuCephiAppNavigation(
    app: MuCepheiApplication,
    initialSharedNoteId: String?,
    initialWebUrl: String?,
    onSharedNoteConsumed: () -> Unit,
    currentPreset: ThemePreset,
    currentWallpaperUri: String?,
    currentBlurRadius: Float,
    currentWallpaperAlpha: Float,
    currentVectorOpacity: Float,
    isHideVectors: Boolean,
    isAutoNightMode: Boolean,
    fontScale: Float,
    isGridView: Boolean,
    isGlassifyEnabled: Boolean,
    onCustomThemeExtracted: (NoteTheme) -> Unit,
    onPresetSelected: (ThemePreset) -> Unit,
    onWallpaperChanged: (String?, Float) -> Unit,
    onWallpaperAlphaChanged: (Float) -> Unit,
    onVectorOpacityChanged: (Float) -> Unit,
    onHideVectorsToggled: (Boolean) -> Unit,
    onAutoNightModeToggled: (Boolean) -> Unit,
    onFontScaleChanged: (Float) -> Unit,
    onGridViewToggled: (Boolean) -> Unit,
    onGlassifyToggled: (Boolean) -> Unit
) {
    val navController = rememberNavController()
    var showBackupDialog by remember { mutableStateOf(false) }
    var globalHavenRequest by remember { mutableStateOf<HavenRequest?>(null) }

    LaunchedEffect(initialSharedNoteId) {
        initialSharedNoteId?.let { noteId ->
            navController.navigate("note_detail/$noteId")
            onSharedNoteConsumed()
        }
    }

    if (showBackupDialog) {
        EncryptedBackupDialog(
            backupEngine = app.backupEngine,
            onDismiss = { showBackupDialog = false }
        )
    }

    NavHost(
        navController = navController,
        startDestination = "main_feed"
    ) {
        composable("main_feed") {
            MainPagerContainer(
                repository = app.repository,
                p2pEngine = app.p2pSyncEngine,
                initialWebUrl = initialWebUrl,
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
                onNoteSelected = { noteId ->
                    navController.navigate("note_detail/$noteId")
                },
                onCreateNewNote = {
                    navController.navigate("note_detail/new")
                },
                onOpenBackupDialog = {
                    showBackupDialog = true
                },
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
                externalHavenRequest = globalHavenRequest,
                onExternalRequestConsumed = { globalHavenRequest = null }
            )
        }

        composable("note_detail/{noteId}") { backStackEntry ->
            val noteIdArg = backStackEntry.arguments?.getString("noteId")
            val targetId = if (noteIdArg == "new") null else noteIdArg

            NoteDetailScreen(
                noteId = targetId,
                repository = app.repository,
                p2pEngine = app.p2pSyncEngine,
                onBack = {
                    navController.popBackStack()
                },
                onNavigateToHaven = { request ->
                    globalHavenRequest = request
                    navController.popBackStack()
                }
            )
        }
    }
}
