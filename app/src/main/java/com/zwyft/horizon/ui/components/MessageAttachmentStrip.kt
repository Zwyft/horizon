package com.zwyft.horizon.ui.components

import android.media.MediaPlayer
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.zwyft.horizon.data.entity.MessageAttachmentEntity
import java.io.File

/**
 * Inline strip of MMS attachment thumbnails shown beneath the message
 * body. Uses Coil to render images; audio shows a play button; unknown
 * types show a generic file icon. Tapping any attachment opens the
 * fullscreen viewer (via the [onOpen] callback, wired by the parent).
 */
@Composable
fun MessageAttachmentStrip(
    attachments: List<MessageAttachmentEntity>,
    onOpen: (MessageAttachmentEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    if (attachments.isEmpty()) return

    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        contentPadding = PaddingValues(vertical = 4.dp)
    ) {
        items(attachments) { a ->
            AttachmentChip(attachment = a, onClick = { onOpen(a) })
        }
    }
}

@Composable
private fun AttachmentChip(
    attachment: MessageAttachmentEntity,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val file = remember(attachment.localPath) { File(attachment.localPath) }
    val exists = remember(attachment.localPath) { file.exists() && file.length() > 0 }
    val isImage = attachment.mimeType.startsWith("image/")
    val isVideo = attachment.mimeType.startsWith("video/")
    val isAudio = attachment.mimeType.startsWith("audio/")

    var audioPlaying by remember { mutableStateOf(false) }
    val mediaPlayer = remember { MediaPlayer() }

    DisposableEffect(attachment.localPath) {
        onDispose {
            try { if (mediaPlayer.isPlaying) mediaPlayer.stop() } catch (_: Throwable) {}
            mediaPlayer.release()
        }
    }

    Box(
        modifier = Modifier
            .size(width = 96.dp, height = 96.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable {
                if (isAudio && exists) {
                    toggleAudio(mediaPlayer, file, audioPlaying) { audioPlaying = it }
                } else {
                    onClick()
                }
            },
        contentAlignment = Alignment.Center
    ) {
        when {
            !exists -> {
                // File missing (user cleared data, or interrupted sync)
                Icon(
                    Icons.Filled.BrokenImage,
                    contentDescription = "Missing attachment",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            isImage -> {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(file)
                        .crossfade(true)
                        .build(),
                    contentDescription = attachment.originalName ?: "Image",
                    modifier = Modifier.fillMaxSize()
                )
            }
            isVideo -> {
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f))) {
                    Icon(
                        Icons.Filled.PlayCircle,
                        contentDescription = "Play video",
                        tint = Color.White,
                        modifier = Modifier.size(36.dp).align(Alignment.Center)
                    )
                }
            }
            isAudio -> {
                Icon(
                    if (audioPlaying) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                    contentDescription = if (audioPlaying) "Stop audio" else "Play audio",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(36.dp)
                )
            }
            else -> {
                Icon(
                    Icons.Filled.InsertDriveFile,
                    contentDescription = "Attachment",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(36.dp)
                )
            }
        }
        // Size badge
        if (exists) {
            Surface(
                color = Color.Black.copy(alpha = 0.5f),
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(2.dp)
            ) {
                Text(
                    text = formatSize(attachment.sizeBytes),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                )
            }
        }
    }
}

private fun formatSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    else -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
}

private fun toggleAudio(
    mp: MediaPlayer,
    file: File,
    currentlyPlaying: Boolean,
    onStateChange: (Boolean) -> Unit
) {
    try {
        if (currentlyPlaying) {
            if (mp.isPlaying) mp.stop()
            onStateChange(false)
        } else {
            mp.reset()
            mp.setDataSource(file.absolutePath)
            mp.setOnCompletionListener { onStateChange(false) }
            mp.prepare()
            mp.start()
            onStateChange(true)
        }
    } catch (e: Exception) {
        onStateChange(false)
    }
}
