package com.mucephei.app.ui.components

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.mucephei.app.data.model.MediaItem
import com.mucephei.app.theme.HandwritingTypography
import com.mucephei.app.theme.PaletteEngine
import com.mucephei.app.theme.sketchyBorder

/**
 * Full-screen Image Lightbox Dialog overlay triggered when tapping photo thumbnails in the feed.
 */
@Composable
fun ImageLightboxDialog(
    mediaItem: MediaItem,
    onDismiss: () -> Unit,
    onExtractPalette: (() -> Unit)? = null
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.92f))
                .clickable { onDismiss() },
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = mediaItem.uri,
                contentDescription = mediaItem.metadata.title ?: "Image Lightbox",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .sketchyBorder(color = Color(0xFFF46036), strokeWidth = 3.dp),
                contentScale = ContentScale.Fit
            )

            // Close button top right
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(24.dp)
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.6f))
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close Lightbox",
                    tint = Color.White
                )
            }

            // Extract Palette Trigger bottom center
            if (onExtractPalette != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 32.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .clickable { onExtractPalette() }
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = "Extract Theme Palette",
                        style = HandwritingTypography.titleMedium,
                        color = Color.White
                    )
                }
            }
        }
    }
}
