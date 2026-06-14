package com.zwyft.horizon.ui.screens

import android.content.Intent
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zwyft.horizon.data.HorizonDatabase
import com.zwyft.horizon.data.entity.MessageAttachmentEntity
import com.zwyft.horizon.data.entity.MessageEntity
import com.zwyft.horizon.ui.AttachmentViewerActivity
import com.zwyft.horizon.ui.components.MessageAttachmentStrip
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessagesScreen(
    navController: androidx.navigation.NavController
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Messages") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        MessagesContent(modifier = Modifier.padding(padding))
    }
}

@Composable
fun MessagesContent(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val db = remember { HorizonDatabase.getInstance(context) }
    val messages by db.messageDao().observeMonitored().collectAsState(initial = emptyList())
    val dateFmt = remember { SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()) }
    val dayFmt = remember { SimpleDateFormat("EEEE, MMMM d", Locale.getDefault()) }

    var attachmentsMap by remember { mutableStateOf<Map<Long, List<MessageAttachmentEntity>>>(emptyMap()) }
    LaunchedEffect(messages) {
        val ids = messages.map { it.id }
        attachmentsMap = if (ids.isNotEmpty()) {
            db.messageAttachmentDao().getForMessages(ids).groupBy { it.messageId }
        } else emptyMap()
    }

    Box(modifier = modifier.fillMaxSize()) {
        if (messages.isEmpty()) {
            // ── Polished empty state ──
            Column(
                modifier = Modifier.fillMaxSize().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.size(80.dp)
                ) {
                    Icon(
                        Icons.Filled.Forum,
                        contentDescription = null,
                        modifier = Modifier.padding(20.dp),
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                    )
                }
                Spacer(Modifier.height(16.dp))
                Text("No messages yet", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Import messages or enable monitoring for contacts to see conversations here.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 32.dp)
                )
            }
        } else {
            // ── Group messages by day ──
            val grouped = remember(messages) {
                messages.groupBy {
                    val cal = Calendar.getInstance().apply { time = it.date }
                    Calendar.getInstance().apply {
                        set(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH))
                    }.time
                }.toSortedMap(reverseOrder())
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                grouped.forEach { (day, msgs) ->
                    item(key = "header-$day") {
                        // Day header
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.padding(vertical = 12.dp)
                        ) {
                            Text(
                                dayFmt.format(day),
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    items(msgs, key = { it.id }) { msg ->
                        ChatBubbleMessage(
                            message = msg,
                            dateFmt = dateFmt,
                            attachments = attachmentsMap[msg.id].orEmpty(),
                            isIncoming = msg.type == 1
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatBubbleMessage(
    message: MessageEntity,
    dateFmt: SimpleDateFormat,
    attachments: List<MessageAttachmentEntity>,
    isIncoming: Boolean
) {
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }

    val bubbleColor = if (isIncoming)
        MaterialTheme.colorScheme.surfaceVariant
    else
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = if (isIncoming) Arrangement.Start else Arrangement.End,
        verticalAlignment = Alignment.Top
    ) {
        if (isIncoming) {
            // Sender avatar for incoming
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.size(32.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        (message.contactName?.firstOrNull() ?: message.address.firstOrNull() ?: '?').uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
        }

        Card(
            modifier = Modifier.widthIn(max = 280.dp),
            shape = RoundedCornerShape(
                topStart = 16.dp, topEnd = 16.dp,
                bottomStart = if (isIncoming) 4.dp else 16.dp,
                bottomEnd = if (isIncoming) 16.dp else 4.dp
            ),
            colors = CardDefaults.cardColors(containerColor = bubbleColor),
            onClick = { expanded = !expanded }
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                // Sender name for incoming
                if (isIncoming) {
                    Text(
                        message.contactName ?: message.address,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(2.dp))
                }

                // Body
                val bodyText = message.body.orEmpty()
                if (bodyText.isNotBlank()) {
                    Text(
                        bodyText,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = if (expanded) Int.MAX_VALUE else 6,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Attachments
                if (attachments.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    MessageAttachmentStrip(
                        attachments = attachments,
                        onOpen = { attachment ->
                            val intent = Intent(context, AttachmentViewerActivity::class.java).apply {
                                putExtra(AttachmentViewerActivity.EXTRA_ATTACHMENT_ID, attachment.id)
                                putExtra(AttachmentViewerActivity.EXTRA_MESSAGE_ID, attachment.messageId)
                                putExtra(AttachmentViewerActivity.EXTRA_PATH, attachment.localPath)
                                putExtra(AttachmentViewerActivity.EXTRA_MIME, attachment.mimeType)
                                putExtra(AttachmentViewerActivity.EXTRA_TITLE, attachment.originalName ?: "Attachment")
                            }
                            context.startActivity(intent)
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Time + badges row
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (message.rcs == true) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.tertiaryContainer
                        ) {
                            Text("RCS", modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp))
                        }
                        Spacer(Modifier.width(4.dp))
                    }
                    if (message.protocol == 1) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Text("MMS", modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp))
                        }
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(
                        dateFmt.format(message.date),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        }

        if (!isIncoming) {
            Spacer(Modifier.width(8.dp))
        }
    }
}
