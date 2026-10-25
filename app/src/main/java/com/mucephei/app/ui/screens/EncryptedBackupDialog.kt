package com.mucephei.app.ui.screens

import android.net.Uri
import android.os.Environment
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.mucephei.app.sync.EncryptedBackupEngine
import com.mucephei.app.sync.RestoreMode
import com.mucephei.app.theme.HandwritingTypography
import com.mucephei.app.theme.sketchyBorder
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

/**
 * Journal Backup & Restore Dialog:
 * - Saves backups to Downloads/Mu Cephei by default
 * - Optional AES-256 encryption toggle (with dynamic icon change: Lock vs Backup)
 * - Prompts user to choose what to restore (Full, Settings Only, Wallpaper Only) before file picking
 * - Contrast-aware button text/icon colors across all themes (including Dark Glass)
 */
@Composable
fun EncryptedBackupDialog(
    backupEngine: EncryptedBackupEngine,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var passwordInput by remember { mutableStateOf("") }
    var encryptEnabled by remember { mutableStateOf(false) }
    var restoreMode by remember { mutableStateOf(RestoreMode.ALL) }
    var showRestorePrompt by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf("") }

    val primaryBtnContent = if (MaterialTheme.colorScheme.primary.luminance() > 0.5f) Color.Black else Color.White
    val secondaryBtnContent = if (MaterialTheme.colorScheme.secondary.luminance() > 0.5f) Color.Black else Color.White

    // File picker launcher for restore
    val restoreFilePicker = rememberLauncherForActivityResult(
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

                    val success = backupEngine.restoreArchive(
                        archiveFile = tempFile,
                        password = if (encryptEnabled) passwordInput.toCharArray() else null,
                        encrypted = encryptEnabled,
                        restoreMode = restoreMode
                    )
                    val modeLabel = when (restoreMode) {
                        RestoreMode.SETTINGS_ONLY -> "settings"
                        RestoreMode.WALLPAPER_ONLY -> "wallpaper"
                        else -> "journal"
                    }
                    statusText = if (success) "Successfully restored $modeLabel! Restarting..." else "Restore failed! Check encryption key."
                    if (success) {
                        kotlinx.coroutines.delay(1000)
                        (context as? android.app.Activity)?.recreate()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    statusText = "Error reading backup file: ${e.localizedMessage}"
                }
            }
        }
    }

    if (showRestorePrompt) {
        AlertDialog(
            onDismissRequest = { showRestorePrompt = false },
            title = { Text("Choose What to Restore", style = HandwritingTypography.titleLarge) },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Select what you would like to restore from the backup archive:",
                        style = HandwritingTypography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = {
                            restoreMode = RestoreMode.ALL
                            showRestorePrompt = false
                            restoreFilePicker.launch("*/*")
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Full Restore (Notes, Settings & Wallpaper)")
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            restoreMode = RestoreMode.SETTINGS_ONLY
                            showRestorePrompt = false
                            restoreFilePicker.launch("*/*")
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Settings Only")
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            restoreMode = RestoreMode.WALLPAPER_ONLY
                            showRestorePrompt = false
                            restoreFilePicker.launch("*/*")
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Wallpaper Only")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showRestorePrompt = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .sketchyBorder(
                    color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.35f),
                    strokeWidth = 1.dp,
                    cornerRadius = 18.dp
                ),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(18.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (encryptEnabled) Icons.Default.Lock else Icons.Default.Backup,
                            contentDescription = "Backup Mode",
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (encryptEnabled) "Encrypted Backup" else "Standard Backup",
                            style = HandwritingTypography.headlineMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Encryption Toggle Switch
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
                        text = "Encrypt Archive with AES-256",
                        style = HandwritingTypography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Switch(
                        checked = encryptEnabled,
                        onCheckedChange = { encryptEnabled = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.primary,
                            checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                        )
                    )
                }

                if (encryptEnabled) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = passwordInput,
                        onValueChange = { passwordInput = it },
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

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Export Archive (Saves to Downloads/Mu Cephei)
                    Button(
                        onClick = {
                            if (encryptEnabled && passwordInput.length < 4) {
                                statusText = "Password must be at least 4 characters"
                                return@Button
                            }
                            scope.launch {
                                try {
                                    val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                                    val backupDir = File(downloadsDir, "Mu Cephei").apply { mkdirs() }
                                    val exportFile = File(backupDir, "mcb_${System.currentTimeMillis()}.mucephi")

                                    val success = backupEngine.exportArchive(
                                        destinationFile = exportFile,
                                        password = if (encryptEnabled) passwordInput.toCharArray() else null,
                                        encrypt = encryptEnabled
                                    )
                                    statusText = if (success) "Saved to Downloads/Mu Cephei/${exportFile.name}" else "Export failed!"
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                    statusText = "Export error: ${e.localizedMessage}"
                                }
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = primaryBtnContent
                        )
                    ) {
                        Icon(imageVector = Icons.Default.Upload, contentDescription = "Export", tint = primaryBtnContent)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Export", style = HandwritingTypography.titleMedium, color = primaryBtnContent, maxLines = 1)
                    }

                    // Restore Archive (Prompts prompt dialog first)
                    Button(
                        onClick = {
                            if (encryptEnabled && passwordInput.length < 4) {
                                statusText = "Password must be at least 4 characters for encrypted backup"
                                return@Button
                            }
                            showRestorePrompt = true
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
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

                if (statusText.isNotBlank()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = statusText,
                        style = HandwritingTypography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

private fun Color.luminance(): Float {
    val r = red
    val g = green
    val b = blue
    return 0.2126f * r + 0.7152f * g + 0.0722f * b
}
