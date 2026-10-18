package com.mucephei.app.ui.screens

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Environment
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Opacity
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.mucephei.app.data.model.NoteTheme
import com.mucephei.app.data.model.ThemePreset
import com.mucephei.app.sync.EncryptedBackupEngine
import com.mucephei.app.theme.HandwritingTypography
import com.mucephei.app.theme.PaletteEngine
import com.mucephei.app.theme.sketchyBorder
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

private data class PresetThemeInfo(
    val preset: ThemePreset,
    val title: String,
    val previewPrimaryHex: String,
    val previewSurfaceHex: String,
    val textContrastColorHex: String
)

private val PRESET_THEMES = listOf(
    PresetThemeInfo(ThemePreset.SUNSET_WARMTH, "Sunset Warmth", "#D97724", "#FEF3EB", "#5C2B0B"),
    PresetThemeInfo(ThemePreset.AMOLED_BLACK, "AMOLED Black", "#FF7043", "#121212", "#FFFFFF"),
    PresetThemeInfo(ThemePreset.DEFAULT_DARK, "Midnight Journal", "#8E6FB9", "#2B2930", "#F3EDF7"),
    PresetThemeInfo(ThemePreset.DEFAULT_LIGHT, "Serene Cream", "#7C9885", "#FFFDF9", "#2C3531"),
    PresetThemeInfo(ThemePreset.LAVENDER_DREAM, "Lavender Dream", "#8E6FB9", "#F7F3FB", "#3F2E56"),
    PresetThemeInfo(ThemePreset.FOREST_MIST, "Forest Mist", "#4B7B61", "#F2F7F4", "#1E3327"),
    PresetThemeInfo(ThemePreset.OCEAN_BREEZE, "Ocean Breeze", "#3A86AD", "#F0F7FB", "#193B4D"),
    PresetThemeInfo(ThemePreset.LIGHT_GLASS, "Light Glass", "#000000", "#FFFFFF", "#000000"),
    PresetThemeInfo(ThemePreset.DARK_GLASS, "Dark Glass", "#FFFFFF", "#121212", "#FFFFFF"),
    PresetThemeInfo(ThemePreset.SHINING_STARS_GLASS, "Shining Stars", "#3B82F6", "#121212", "#FFFFFF")
)

/**
 * Settings Studio Dialog:
 * - Light & Dark Glass theme presets with contrast-aware button text
 * - "Glassify All Themes" global toggle switch
 * - Vector artwork transparency & auto-hide
 * - Journal Backup & Restore (Encrypted & Standard)
 */
@Composable
fun CustomThemeDialog(
    currentPreset: ThemePreset = ThemePreset.SUNSET_WARMTH,
    currentWallpaperUri: String? = null,
    currentBlurRadius: Float = 10f,
    currentWallpaperAlpha: Float = 0.72f,
    currentVectorOpacity: Float = 0.05f,
    isHideVectors: Boolean = false,
    isAutoNightMode: Boolean = false,
    currentFontScale: Float = 1.0f,
    isGlassifyEnabled: Boolean = true,
    onPresetSelected: (ThemePreset) -> Unit,
    onThemeExtracted: (NoteTheme) -> Unit,
    onWallpaperChanged: (String?, Float) -> Unit = { _, _ -> },
    onWallpaperAlphaChanged: (Float) -> Unit = {},
    onVectorOpacityChanged: (Float) -> Unit = {},
    onHideVectorsToggled: (Boolean) -> Unit = {},
    onAutoNightModeToggled: (Boolean) -> Unit = {},
    onFontScaleChanged: (Float) -> Unit = {},
    onGlassifyToggled: (Boolean) -> Unit = {},
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val app = context.applicationContext as com.mucephei.app.MuCepheiApplication
    val backupEngine = app.backupEngine

    var selectedPreset by remember { mutableStateOf(currentPreset) }
    var wallpaperUri by remember { mutableStateOf(currentWallpaperUri) }
    var blurRadius by remember { mutableFloatStateOf(currentBlurRadius) }
    var wallpaperAlpha by remember { mutableFloatStateOf(currentWallpaperAlpha) }
    var vectorOpacity by remember { mutableFloatStateOf(currentVectorOpacity) }
    var hideVectors by remember { mutableStateOf(isHideVectors) }
    var autoNightMode by remember { mutableStateOf(isAutoNightMode) }
    var fontScale by remember { mutableFloatStateOf(currentFontScale) }
    var glassifyEnabled by remember { mutableStateOf(isGlassifyEnabled) }

    var backupPassword by remember { mutableStateOf("") }
    var encryptBackup by remember { mutableStateOf(false) }
    var backupStatusText by remember { mutableStateOf("") }

    val primaryBtnContent = if (MaterialTheme.colorScheme.primary.luminance() > 0.5f) Color.Black else Color.White
    val secondaryBtnContent = if (MaterialTheme.colorScheme.secondary.luminance() > 0.5f) Color.Black else Color.White

    var showRestorePrompt by remember { mutableStateOf(false) }
    var restoreCapsuleEntries by remember { mutableStateOf(true) }
    var restoreCapsuleSettings by remember { mutableStateOf(true) }
    var restoreHavenSettings by remember { mutableStateOf(true) }
    var restoreHavenHistory by remember { mutableStateOf(true) }
    var restoreOasisSettings by remember { mutableStateOf(true) }
    var restoreOasisHistoryAndData by remember { mutableStateOf(true) }
    var restoreWallpapers by remember { mutableStateOf(true) }

    // Restore file picker
    val restoreLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { selectedUri ->
            scope.launch {
                try {
                    val tempFile = File(context.cacheDir, "restore_archive.mucephi")
                    context.contentResolver.openInputStream(selectedUri)?.use { input ->
                        FileOutputStream(tempFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    val options = com.mucephei.app.sync.RestoreOptions(
                        restoreCapsuleEntries = restoreCapsuleEntries,
                        restoreCapsuleSettings = restoreCapsuleSettings,
                        restoreHavenSettings = restoreHavenSettings,
                        restoreHavenHistory = restoreHavenHistory,
                        restoreOasisSettings = restoreOasisSettings,
                        restoreOasisHistoryAndData = restoreOasisHistoryAndData,
                        restoreWallpapers = restoreWallpapers
                    )
                    val success = backupEngine.restoreArchive(
                        archiveFile = tempFile,
                        password = if (encryptBackup) backupPassword.toCharArray() else null,
                        encrypted = encryptBackup,
                        restoreOptions = options
                    )
                    backupStatusText = if (success) "Successfully restored selected parts! Restarting..." else "Restore failed! Check encryption key."
                    if (success) {
                        kotlinx.coroutines.delay(1000)
                        (context as? android.app.Activity)?.recreate()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    backupStatusText = "Restore error: ${e.localizedMessage}"
                }
            }
        }
    }

    var pendingExtractedTheme by remember { mutableStateOf<NoteTheme?>(null) }
    var showExtractionPrompt by remember { mutableStateOf(false) }

    fun copyUriToLocalWallpaperFile(uri: Uri): String? {
        return try {
            val wallpaperDir = File(context.filesDir, "wallpapers").apply { mkdirs() }
            val file = File(wallpaperDir, "custom_bg.jpg")
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(file).use { output ->
                    input.copyTo(output)
                }
            }
            file.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            uri.toString()
        }
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val localPath = copyUriToLocalWallpaperFile(it) ?: it.toString()
            wallpaperUri = localPath
            hideVectors = true
            onHideVectorsToggled(true)
            onWallpaperChanged(localPath, blurRadius)

            scope.launch {
                try {
                    context.contentResolver.openInputStream(it)?.use { stream ->
                        val bitmap = BitmapFactory.decodeStream(stream)
                        if (bitmap != null) {
                            val extracted = PaletteEngine.extractNoteThemeFromBitmap(bitmap)
                            pendingExtractedTheme = extracted.copy(
                                customImageUri = localPath,
                                selectedPreset = currentPreset
                            )
                            showExtractionPrompt = true
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    // Color Extraction Confirmation Prompt Dialog
    if (showExtractionPrompt && pendingExtractedTheme != null) {
        val theme = pendingExtractedTheme!!
        AlertDialog(
            onDismissRequest = { showExtractionPrompt = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.Palette,
                    contentDescription = "Color Extraction",
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            title = {
                Text(
                    text = "Use Extracted Image Colors?",
                    style = HandwritingTypography.titleLarge
                )
            },
            text = {
                Column {
                    Text(
                        text = "We identified primary theme colors from your background image. Would you like to update the interface colors to match?",
                        style = HandwritingTypography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        theme.dominantColorsHex.forEach { hex ->
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(PaletteEngine.hexToColor(hex))
                                    .border(1.dp, Color.White, CircleShape)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onThemeExtracted(theme)
                        onWallpaperChanged(wallpaperUri, blurRadius)
                        showExtractionPrompt = false
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = primaryBtnContent
                    )
                ) {
                    Text("Apply Wallpaper & Colors", color = primaryBtnContent)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = {
                        onWallpaperChanged(wallpaperUri, blurRadius)
                        showExtractionPrompt = false
                    }
                ) {
                    Text("Wallpaper Only")
                }
            }
        )
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .sketchyBorder(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                    strokeWidth = 1.dp,
                    cornerRadius = 18.dp
                ),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(18.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.ColorLens,
                            contentDescription = "Settings",
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Settings & Studio",
                            style = HandwritingTypography.headlineMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Section 1: Auto Switch Toggle
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = "Auto Switch",
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Auto Switch Theme",
                                style = HandwritingTypography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Switch theme automatically at nighttime",
                                style = HandwritingTypography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }
                    Switch(
                        checked = autoNightMode,
                        onCheckedChange = { isChecked ->
                            autoNightMode = isChecked
                            onAutoNightModeToggled(isChecked)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.primary,
                            checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                        )
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Glassify All Themes Toggle Switch
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = "Glassify Themes",
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "Glassify All Themes",
                                    style = HandwritingTypography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Apply frosted glass transparency to all themes",
                                    style = HandwritingTypography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            }
                        }

                    Switch(
                        checked = glassifyEnabled,
                        onCheckedChange = { isChecked ->
                            glassifyEnabled = isChecked
                            onGlassifyToggled(isChecked)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.primary,
                            checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                        )
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Section 2: Preset Themes (including Light & Dark Glass)
                Text(
                    text = "Select Theme Preset:",
                    style = HandwritingTypography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PRESET_THEMES.chunked(2).forEach { rowPresets ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowPresets.forEach { item ->
                                val isSelected = selectedPreset == item.preset
                                Card(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable {
                                            selectedPreset = item.preset
                                            onPresetSelected(item.preset)
                                        },
                                    colors = CardDefaults.cardColors(
                                        containerColor = PaletteEngine.hexToColor(item.previewSurfaceHex)
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    border = if (isSelected) {
                                        androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                                    } else {
                                        androidx.compose.foundation.BorderStroke(1.dp, Color.Gray.copy(alpha = 0.2f))
                                    }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(20.dp)
                                                .clip(CircleShape)
                                                .background(PaletteEngine.hexToColor(item.previewPrimaryHex))
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = item.title,
                                            style = HandwritingTypography.labelMedium,
                                            color = PaletteEngine.hexToColor(item.textContrastColorHex),
                                            modifier = Modifier.weight(1f)
                                        )
                                        if (isSelected) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = "Selected",
                                                tint = PaletteEngine.hexToColor(item.previewPrimaryHex),
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Section 3: Font Size Scaling
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.FormatSize,
                        contentDescription = "Font Size",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Text & Reader Size:",
                        style = HandwritingTypography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        0.85f to "Small",
                        1.0f to "Medium",
                        1.15f to "Large"
                    ).forEach { (scale, label) ->
                        val isSelected = fontScale == scale
                        OutlinedButton(
                            onClick = {
                                fontScale = scale
                                onFontScaleChanged(scale)
                            },
                            colors = if (isSelected) {
                                ButtonDefaults.outlinedButtonColors(
                                    containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                    contentColor = primaryBtnContent
                                )
                            } else {
                                ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)
                            },
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 2.dp, vertical = 6.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(text = label, style = HandwritingTypography.labelSmall, maxLines = 1)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Section 4: Theme Vector Background Transparency & Hide Options
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.WaterDrop,
                        contentDescription = "Vectors",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Background Arts Customization:",
                        style = HandwritingTypography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Hide Vector Art",
                        style = HandwritingTypography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Switch(
                        checked = hideVectors,
                        onCheckedChange = { isChecked ->
                            hideVectors = isChecked
                            onHideVectorsToggled(isChecked)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.primary,
                            checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                        )
                    )
                }

                if (!hideVectors) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Vector Art Opacity (${(vectorOpacity * 100).toInt()}%):",
                        style = HandwritingTypography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Slider(
                        value = vectorOpacity,
                        onValueChange = {
                            vectorOpacity = it
                            onVectorOpacityChanged(it)
                        },
                        valueRange = 0.01f..1.00f,
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary
                        )
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Section 5: Custom Background Wallpaper & Blur
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Wallpaper,
                        contentDescription = "Wallpaper",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Custom Background Everywhere:",
                        style = HandwritingTypography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Wallpaper Image Selector Card
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
                        .clickable { imagePickerLauncher.launch("image/*") },
                    contentAlignment = Alignment.Center
                ) {
                    if (!wallpaperUri.isNullOrBlank()) {
                        AsyncImage(
                            model = wallpaperUri,
                            contentDescription = "Background wallpaper",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.Black.copy(alpha = 0.4f))
                                .padding(6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Tap to change wallpaper image",
                                style = HandwritingTypography.labelMedium,
                                color = Color.White
                            )
                        }
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.Image,
                                contentDescription = "Import Wallpaper",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Tap to import wallpaper image...",
                                style = HandwritingTypography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }
                }

                if (!wallpaperUri.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Wallpaper Blur Intensity (${blurRadius.toInt()}dp):",
                        style = HandwritingTypography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Slider(
                        value = blurRadius,
                        onValueChange = {
                            blurRadius = it
                            onWallpaperChanged(wallpaperUri, blurRadius)
                        },
                        valueRange = 0f..25f,
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary
                        )
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Opacity,
                            contentDescription = "Background Visibility",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Background Visibility Overlay (${(wallpaperAlpha * 100).toInt()}%):",
                            style = HandwritingTypography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Slider(
                        value = wallpaperAlpha,
                        onValueChange = {
                            wallpaperAlpha = it
                            onWallpaperAlphaChanged(it)
                        },
                        valueRange = 0.10f..1.00f,
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary
                        )
                    )

                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedButton(
                        onClick = {
                            wallpaperUri = null
                            onWallpaperChanged(null, 0f)
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Remove Wallpaper Image", style = HandwritingTypography.labelMedium)
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                Spacer(modifier = Modifier.height(14.dp))

                // Section 7: Swiping & Gesture Navigation Controls (Capsule, Haven, Oasis)
                val swipePrefs = remember { context.getSharedPreferences("app_swipe_settings", Context.MODE_PRIVATE) }
                var swipeModeFull by remember { mutableStateOf(swipePrefs.getBoolean("swipe_mode_full", true)) }
                var edgeWidthDp by remember { mutableFloatStateOf(swipePrefs.getFloat("edge_width_dp", 48f)) }
                var capsuleHavenSensitivity by remember { mutableFloatStateOf(swipePrefs.getFloat("capsule_haven_sensitivity", 30f)) }
                var oasisSensitivity by remember { mutableFloatStateOf(swipePrefs.getFloat("oasis_sensitivity", 15f)) }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Gestures",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Gesture & Swiping Controls:",
                        style = HandwritingTypography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Capsule & Haven Full-Screen Swiping",
                            style = HandwritingTypography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (swipeModeFull) "Swipe from anywhere on screen" else "Swipe from screen edges only",
                            style = HandwritingTypography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = swipeModeFull,
                        onCheckedChange = { 
                            swipeModeFull = it
                            swipePrefs.edit().putBoolean("swipe_mode_full", it).apply()
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.primary,
                            checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                        )
                    )
                }

                if (!swipeModeFull) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Edge Detection Area Width (${edgeWidthDp.toInt()}dp):",
                        style = HandwritingTypography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Slider(
                        value = edgeWidthDp,
                        onValueChange = { 
                            edgeWidthDp = it
                            swipePrefs.edit().putFloat("edge_width_dp", it).apply()
                        },
                        valueRange = 24f..150f,
                        steps = 10,
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary
                        )
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Capsule & Haven Sensitivity (Threshold: ${capsuleHavenSensitivity.toInt()}):",
                    style = HandwritingTypography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Lower = more sensitive, Higher = less sensitive",
                    style = HandwritingTypography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Slider(
                    value = capsuleHavenSensitivity,
                    onValueChange = { 
                        capsuleHavenSensitivity = it
                        swipePrefs.edit().putFloat("capsule_haven_sensitivity", it).apply()
                    },
                    valueRange = 10f..60f,
                    steps = 10,
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary
                    )
                )

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Oasis Toolbar Sensitivity (Threshold: ${oasisSensitivity.toInt()}):",
                    style = HandwritingTypography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Adjust bottom toolbar swipe threshold for Oasis browser",
                    style = HandwritingTypography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Slider(
                    value = oasisSensitivity,
                    onValueChange = { 
                        oasisSensitivity = it
                        swipePrefs.edit().putFloat("oasis_sensitivity", it).apply()
                    },
                    valueRange = 5f..40f,
                    steps = 7,
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary
                    )
                )

                Spacer(modifier = Modifier.height(18.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                Spacer(modifier = Modifier.height(14.dp))

                // Section 6: Journal Backup & Restore (Moved inside Settings)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (encryptBackup) Icons.Default.Lock else Icons.Default.Backup,
                        contentDescription = "Backup",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Journal Backup & Restore:",
                        style = HandwritingTypography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Encrypt / Decrypt Archive",
                        style = HandwritingTypography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Switch(
                        checked = encryptBackup,
                        onCheckedChange = { encryptBackup = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.primary,
                            checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                        )
                    )
                }

                if (encryptBackup) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = backupPassword,
                        onValueChange = { backupPassword = it },
                        label = { Text("AES-256 Secret Key", style = HandwritingTypography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)) },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                        )
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            if (encryptBackup && backupPassword.length < 4) {
                                backupStatusText = "Password must be at least 4 chars"
                                return@Button
                            }
                            scope.launch {
                                try {
                                    val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                                    val backupDir = File(downloadsDir, "Mu Cephei").apply { mkdirs() }
                                    val exportFile = File(backupDir, "mcb_${System.currentTimeMillis()}.mucephi")

                                    val success = backupEngine.exportArchive(
                                        destinationFile = exportFile,
                                        password = if (encryptBackup) backupPassword.toCharArray() else null,
                                        encrypt = encryptBackup
                                    )
                                    backupStatusText = if (success) "Saved to Downloads/Mu Cephei" else "Export failed!"
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                    backupStatusText = "Error: ${e.localizedMessage}"
                                }
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = primaryBtnContent
                        )
                    ) {
                        Icon(imageVector = Icons.Default.Upload, contentDescription = "Export", tint = primaryBtnContent)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Export", style = HandwritingTypography.titleMedium, color = primaryBtnContent, maxLines = 1)
                    }

                    Button(
                        onClick = {
                            if (encryptBackup && backupPassword.length < 4) {
                                backupStatusText = "Password required for encrypted backup"
                                return@Button
                            }
                            showRestorePrompt = true
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondary,
                            contentColor = secondaryBtnContent
                        )
                    ) {
                        Icon(imageVector = Icons.Default.Download, contentDescription = "Restore", tint = secondaryBtnContent)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Restore", style = HandwritingTypography.titleMedium, color = secondaryBtnContent, maxLines = 1)
                    }
                }

                if (backupStatusText.isNotBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = backupStatusText,
                        style = HandwritingTypography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = primaryBtnContent
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = "Done", style = HandwritingTypography.titleMedium, color = primaryBtnContent)
                }
            }
        }
    }

    if (showRestorePrompt) {
        AlertDialog(
            onDismissRequest = { showRestorePrompt = false },
            title = { Text("Granular Restore Options", style = HandwritingTypography.titleLarge) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "Check the specific parts of the archive you want to restore:",
                        style = HandwritingTypography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
                    )
                    Spacer(modifier = Modifier.height(4.dp))

                    RestoreCheckboxRow("Capsule Entries (Notes, Media, Audio, Drawings, VPN)", restoreCapsuleEntries) { restoreCapsuleEntries = it }
                    RestoreCheckboxRow("Capsule Settings & Themes", restoreCapsuleSettings) { restoreCapsuleSettings = it }
                    RestoreCheckboxRow("Haven AI Settings", restoreHavenSettings) { restoreHavenSettings = it }
                    RestoreCheckboxRow("Haven Chat History", restoreHavenHistory) { restoreHavenHistory = it }
                    RestoreCheckboxRow("Oasis Browser Settings", restoreOasisSettings) { restoreOasisSettings = it }
                    RestoreCheckboxRow("Oasis History, Speed Dials & Web Data", restoreOasisHistoryAndData) { restoreOasisHistoryAndData = it }
                    RestoreCheckboxRow("Custom Wallpapers", restoreWallpapers) { restoreWallpapers = it }

                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        TextButton(onClick = {
                            restoreCapsuleEntries = true
                            restoreCapsuleSettings = true
                            restoreHavenSettings = true
                            restoreHavenHistory = true
                            restoreOasisSettings = true
                            restoreOasisHistoryAndData = true
                            restoreWallpapers = true
                        }) {
                            Text("Select All", style = HandwritingTypography.labelLarge)
                        }
                        TextButton(onClick = {
                            restoreCapsuleEntries = false
                            restoreCapsuleSettings = false
                            restoreHavenSettings = false
                            restoreHavenHistory = false
                            restoreOasisSettings = false
                            restoreOasisHistoryAndData = false
                            restoreWallpapers = false
                        }) {
                            Text("Deselect All", style = HandwritingTypography.labelLarge)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showRestorePrompt = false
                        restoreLauncher.launch("*/*")
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("Select Backup File", color = primaryBtnContent)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRestorePrompt = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

private fun Color.luminance(): Float {
    val r = red
    val g = green
    val b = blue
    return 0.2126f * r + 0.7152f * g + 0.0722f * b
}
