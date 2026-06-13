package com.zwyft.horizon.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.zwyft.horizon.data.entity.MessageAttachmentEntity
import com.zwyft.horizon.ui.theme.HorizonTheme
import dagger.hilt.android.AndroidEntryPoint
import java.io.File

/**
 * Fullscreen viewer for a single MMS attachment.
 *
 * Triggered from [com.zwyft.horizon.ui.components.MessageAttachmentStrip]
 * by passing a content Intent with the attachment's `id` and `messageId`
 * as extras. For now it loads the file from the path stored in the DB
 * row (a future enhancement is to use a ContentProvider URI).
 */
@AndroidEntryPoint
class AttachmentViewerActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val attachmentId = intent.getLongExtra(EXTRA_ATTACHMENT_ID, -1L)
        val messageId = intent.getLongExtra(EXTRA_MESSAGE_ID, -1L)
        val localPath = intent.getStringExtra(EXTRA_PATH) ?: ""
        val mimeType = intent.getStringExtra(EXTRA_MIME) ?: ""
        val title = intent.getStringExtra(EXTRA_TITLE)

        setContent {
            HorizonTheme {
                AttachmentViewerScreen(
                    title = title,
                    localPath = localPath,
                    mimeType = mimeType,
                    onClose = { finish() },
                    onShare = {
                        // Wire to system share intent. Using a simple
                        // FileProvider URI under the app's existing
                        // authority keeps the implementation short.
                        val uri = androidx.core.content.FileProvider.getUriForFile(
                            this,
                            "${packageName}.provider",
                            File(localPath)
                        )
                        val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                            type = mimeType
                            putExtra(android.content.Intent.EXTRA_STREAM, uri)
                            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        startActivity(android.content.Intent.createChooser(send, "Share attachment"))
                    }
                )
            }
        }
    }

    companion object {
        const val EXTRA_ATTACHMENT_ID = "extra_attachment_id"
        const val EXTRA_MESSAGE_ID = "extra_message_id"
        const val EXTRA_PATH = "extra_path"
        const val EXTRA_MIME = "extra_mime"
        const val EXTRA_TITLE = "extra_title"
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun AttachmentViewerScreen(
    title: String?,
    localPath: String,
    mimeType: String,
    onClose: () -> Unit,
    onShare: () -> Unit
) {
    val context = LocalContext.current
    val file = File(localPath)
    val exists = file.exists() && file.length() > 0
    val isImage = mimeType.startsWith("image/")
    val isVideo = mimeType.startsWith("video/")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title ?: "Attachment") },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Close")
                    }
                },
                actions = {
                    IconButton(onClick = onShare, enabled = exists) {
                        Icon(Icons.Filled.Share, contentDescription = "Share")
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            when {
                !exists -> {
                    Text(
                        "Attachment missing or empty",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
                isImage -> {
                    AsyncImage(
                        model = file,
                        contentDescription = title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                }
                isVideo -> {
                    Text(
                        "Video preview not yet supported in-app. Use Share to open in your video player.",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(32.dp)
                    )
                }
                else -> {
                    Text(
                        "$mimeType — ${file.length() / 1024} KB\nUse Share to open in another app.",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}
