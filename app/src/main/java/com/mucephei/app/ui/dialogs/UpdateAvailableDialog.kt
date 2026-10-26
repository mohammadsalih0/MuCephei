package com.mucephei.app.ui.dialogs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mucephei.app.update.DownloadState
import com.mucephei.app.update.UpdateInfo
import com.mucephei.app.update.UpdateManager
import kotlinx.coroutines.launch

@Composable
fun UpdateAvailableDialog(
    updateInfo: UpdateInfo,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var downloadState by remember { mutableStateOf<DownloadState>(DownloadState.Idle) }

    AlertDialog(
        onDismissRequest = {
            if (downloadState !is DownloadState.Downloading && downloadState !is DownloadState.Verifying) {
                onDismiss()
            }
        },
        title = {
            Text(
                text = "Update Available (${updateInfo.versionName})",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "A new version of MuCephei is available.",
                    style = MaterialTheme.typography.bodyMedium
                )

                Text(
                    text = "Changelog:",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        text = updateInfo.changelog,
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                when (val state = downloadState) {
                    is DownloadState.Downloading -> {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            LinearProgressIndicator(
                                progress = { state.progress },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text(
                                text = "Downloading... ${(state.progress * 100).toInt()}%",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                    is DownloadState.Verifying -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Text(
                                text = "Verifying APK SHA-256 integrity...",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                    is DownloadState.CorruptHashMismatch -> {
                        Text(
                            text = "Warning: The downloaded update file failed SHA-256 integrity verification and was removed. It may be corrupt.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    is DownloadState.ReadyToInstall -> {
                        Text(
                            text = "Download verified successfully! Ready to install.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    is DownloadState.Error -> {
                        Text(
                            text = "Error: ${state.message}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    else -> {}
                }
            }
        },
        confirmButton = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
            ) {
                // Permanent Browser Fallback Button
                OutlinedButton(
                    onClick = { UpdateManager.openReleasePage(context, updateInfo.releaseUrl) }
                ) {
                    Text("Open in Browser")
                }

                when (val state = downloadState) {
                    is DownloadState.ReadyToInstall -> {
                        Button(
                            onClick = { UpdateManager.installApk(context, state.file) }
                        ) {
                            Text("Install Now")
                        }
                    }
                    is DownloadState.Downloading, is DownloadState.Verifying -> {
                        // Disabled during active download/verification
                    }
                    else -> {
                        Button(
                            onClick = {
                                scope.launch {
                                    val file = UpdateManager.downloadAndVerifyApk(context, updateInfo) { newState ->
                                        downloadState = newState
                                    }
                                    if (file != null) {
                                        UpdateManager.installApk(context, file)
                                    }
                                }
                            }
                        ) {
                            Text(if (downloadState is DownloadState.CorruptHashMismatch) "Retry Download" else "Update Now")
                        }
                    }
                }
            }
        },
        dismissButton = {
            if (downloadState !is DownloadState.Downloading && downloadState !is DownloadState.Verifying) {
                TextButton(onClick = onDismiss) {
                    Text("Close")
                }
            }
        }
    )
}
