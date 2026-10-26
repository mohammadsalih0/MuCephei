package com.mucephei.app.update

import java.io.File

data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val downloadUrl: String,
    val releaseUrl: String,
    val sha256: String?,
    val changelog: String,
    val forceUpdate: Boolean = false,
    val minSupportedVersionCode: Int = 0,
)

sealed class DownloadState {
    object Idle : DownloadState()
    data class Downloading(val progress: Float) : DownloadState()
    object Verifying : DownloadState()
    object CorruptHashMismatch : DownloadState()
    data class ReadyToInstall(val file: File) : DownloadState()
    data class Error(val message: String) : DownloadState()
}
