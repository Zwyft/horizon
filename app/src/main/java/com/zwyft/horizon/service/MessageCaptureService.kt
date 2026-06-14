package com.zwyft.horizon.service

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.zwyft.horizon.data.HorizonDatabase
import com.zwyft.horizon.data.entity.MessageEntity
import kotlinx.coroutines.*
import kotlinx.coroutines.delay
import java.security.MessageDigest
import java.util.*

/**
 * AccessibilityService that captures RCS/SMS messages in real-time.
 *
 * Hooks into Google Messages (and other SMS apps) by listening for
 * TYPE_WINDOW_CONTENT_CHANGED and TYPE_WINDOW_STATE_CHANGED events,
 * then scraping the message text, sender, and timestamp from the node tree.
 *
 * Phase 3 improvements:
 *  - Enabled captureFromWindow for in-conversation RCS bubble scraping.
 *  - Added debounced buffer (2s stable state) to avoid re-inserting
 *    duplicates when the user scrolls or the layout re-renders.
 *  - Uses content-hash messageIds for idempotent inserts.
 *  - Logs a warning when a duplicate is skipped.
 */
class MessageCaptureService : AccessibilityService() {

    companion object {
        private const val TAG = "MessageCaptureService"
        private const val DEBOUNCE_MS = 2000L
        private const val MAX_MESSAGES_PER_WINDOW = 50

        private val TARGET_PACKAGES = setOf(
            "com.google.android.apps.messaging",
            "com.android.mms",
            "com.samsung.android.messaging"
        )

        // Common resource-id patterns for the conversation RecyclerView
        // and its message bubbles in Google Messages.
        private val CONVERSATION_VIEW_IDS = setOf(
            "com.google.android.apps.messaging:id/conversation_recycler_view",
            "com.google.android.apps.messaging:id/message_list",
            "com.google.android.apps.messaging:id/conversation_list"
        )
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var db: HorizonDatabase
    private var lastWindowEventTime = 0L
    private val pendingMessages = mutableSetOf<Long>() // content hashes
    private var debounceJob: Job? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        db = HorizonDatabase.getInstance(applicationContext)
        Log.i(TAG, "MessageCaptureService connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg !in TARGET_PACKAGES) return

        when (event.eventType) {
            AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED -> {
                captureFromNotification(event)
            }
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                // Phase 3: enabled window scraping with debounce
                captureFromWindow(rootInActiveWindow)
            }
        }
    }

    // ── Notification-based capture (NotificationListener is the primary
    //     path for this; we keep this as a backup for accessibility-only
    //     users who haven't enabled the NotificationListenerService) ──

    private fun captureFromNotification(event: AccessibilityEvent) {
        val text = event.text?.joinToString(" ") ?: return
        serviceScope.launch {
            val msg = MessageEntity(
                messageId       = System.currentTimeMillis(),
                threadId        = 0L,
                address         = extractAddress(text),
                contactName     = null,
                body            = text,
                date            = Date(),
                type            = 1,
                read            = 0,
                seen            = 0,
                protocol        = 0,
                rcs             = true,
                monitored       = false,
                journalProcessed = false
            )
            db.messageDao().insert(msg)
        }
    }

    // ── Phase 3: Window scraping (enabled) ───────────────────

    /**
     * Scrape the current conversation window for message bubbles.
     *
     * Strategy:
     *  1. Find a RecyclerView with a known resource-id
     *     (conversation_recycler_view, message_list, etc).
     *  2. Walk each child node of the RecyclerView.
     *  3. For each child, look for a sender name (TextView above the bubble)
     *     and the bubble text (TextView with body text).
     *  4. Skip child nodes whose content-hash is already in [pendingMessages]
     *     (debounce: we saw this message in the last 2s window).
     *  5. Queue a debounced insert job — messages are only committed
     *     after [DEBOUNCE_MS] of no new window events for the same thread.
     *
     * Layout fragility: Google Messages updates its layout periodically.
     * This scraper uses common patterns (RecyclerView → child → TextViews)
     * and falls back gracefully — if no RecyclerView is found, we just log
     * and return without inserting anything. The NotificationListener is
     * the more reliable path; this is a best-effort supplement.
     */
    private fun captureFromWindow(root: AccessibilityNodeInfo?) {
        if (root == null) return

        val now = System.currentTimeMillis()
        lastWindowEventTime = now

        // Cancel any pending debounce job — we're re-scraping
        debounceJob?.cancel()

        // Find the RecyclerView containing message bubbles
        val recycler = findConversationRecyclerView(root, 0, isRoot = true) ?: return
        val recyclerIsChild = recycler !== root

        val newMessages = mutableListOf<MessageEntity>()
        val childCount = recycler.childCount
        if (childCount == 0) {
            if (recyclerIsChild) recycler.recycle()
            return
        }

        for (i in 0 until minOf(childCount, MAX_MESSAGES_PER_WINDOW)) {
            val child = recycler.getChild(i) ?: continue
            val result = scrapeMessageBubble(child)
            child.recycle()  // Recycle the bubble node after scraping
            if (result != null) {
                val contentHash = computeContentHash(result.first, result.second)
                if (!pendingMessages.add(contentHash)) continue

                val msg = MessageEntity(
                    messageId       = contentHash,
                    threadId        = 0L,
                    address         = result.first,
                    contactName     = if (result.first != "unknown") result.first else null,
                    body            = result.second,
                    date            = Date(),
                    type            = 1,
                    read            = 1,
                    seen            = 1,
                    protocol        = 0,
                    rcs             = true,
                    monitored       = false,
                    journalProcessed = false
                )
                newMessages.add(msg)
            }
        }
        // Recycle the RecyclerView node if it was obtained via getChild()
        if (recyclerIsChild) recycler.recycle()

        if (newMessages.isEmpty()) {
            if (recyclerIsChild) recycler.recycle()
            return
        }

        // Debounce: wait DEBOUNCE_MS before inserting.
        // If another window event arrives before then, this job is canceled
        // and replaced, which is correct — we only want to insert after
        // the user has stopped scrolling / the layout is stable.
        debounceJob = serviceScope.launch {
            delay(DEBOUNCE_MS)
            var inserted = 0
            var skipped = 0
            for (msg in newMessages) {
                // Re-check that this hash wasn't inserted by a notification
                // listener while we were waiting (very unlikely edge case).
                val existing = db.messageDao().getByMessageId(msg.messageId)
                if (existing != null) {
                    skipped++
                    continue
                }
                val result = db.messageDao().insert(msg)
                if (result > 0) inserted++
            }
            if (inserted > 0 || skipped > 0) {
                Log.i(TAG, "Window scrape: inserted=$inserted skipped=$skipped")
            }
            pendingMessages.clear()
        }
    }

    /**
     * Walk the node tree looking for a RecyclerView whose resource-id
     * matches one of the known conversation view IDs. Recursive with a
     * depth guard to avoid stack overflows on very deep trees.
     */
    private fun findConversationRecyclerView(node: AccessibilityNodeInfo, depth: Int, isRoot: Boolean = false): AccessibilityNodeInfo? {
        if (depth > 30) return null
        // Only return the node directly if it's the root (system-managed, no recycle needed)
        // OR if it was already obtained from a child (caller will recycle)
        if (isRoot && node.className?.toString()?.contains("RecyclerView") == true) {
            val resId = node.viewIdResourceName
            if (resId != null && CONVERSATION_VIEW_IDS.any { it in resId }) {
                return node  // root node — caller knows not to recycle
            }
        }
        // For non-root nodes, we want to find RecyclerView deeper in the tree
        if (!isRoot && node.className?.toString()?.contains("RecyclerView") == true) {
            val resId = node.viewIdResourceName
            if (resId != null && CONVERSATION_VIEW_IDS.any { it in resId }) {
                return node  // child node — caller MUST recycle
            }
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findConversationRecyclerView(child, depth + 1, isRoot = false)
            if (found != null) {
                child.recycle()
                return found
            }
            child.recycle()
        }
        return null
    }

    /**
     * Scrape a single message bubble node for (senderName, bodyText).
     *
     * Heuristic:
     *  - The sender name is typically in a small TextView above the bubble.
     *  - The message body is in a larger TextView (maxLines=0, longer text).
     *  - TextViews are the only node type we care about; skip images, buttons, etc.
     *
     * Returns a Pair(senderName, bodyText) or null if no body text found.
     */
    private fun scrapeMessageBubble(node: AccessibilityNodeInfo): Pair<String, String>? {
        val textViews = mutableListOf<String>()
        collectTextViews(node, textViews)

        if (textViews.isEmpty()) return null

        // The body text is usually the longest TextView content.
        // The sender name is the next longest that isn't a timestamp.
        val body = textViews.maxByOrNull { it.length }?.takeIf { it.isNotBlank() } ?: return null
        val sender = textViews
            .filter { it != body && it.isNotBlank() }
            .maxByOrNull { it.length }
            ?: "unknown"

        return sender to body
    }

    /**
     * Collect text from all TextViews in the node subtree.
     * Recycles child nodes after use to prevent AccessibilityNodeInfo leaks.
     */
    private fun collectTextViews(node: AccessibilityNodeInfo, out: MutableList<String>) {
        if (node.className?.toString()?.endsWith("TextView") == true) {
            node.text?.toString()?.let { if (it.isNotBlank()) out.add(it) }
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                collectTextViews(child, out)
                child.recycle()
            }
        }
    }

    /**
     * Compute a stable content hash for deduplication.
     * Uses sender + body text (not position, which moves when scrolling).
     */
    private fun computeContentHash(sender: String, body: String): Long {
        val digest = MessageDigest.getInstance("SHA-256")
        val input = "$sender|$body"
        val hash = digest.digest(input.toByteArray(Charsets.UTF_8))
        // Take first 8 bytes as a Long, keep it positive
        var result = 0L
        for (i in 0 until 8) {
            result = (result shl 8) or (hash[i].toLong() and 0xFF)
        }
        return result and 0x7FFFFFFFFFFFFFFFL
    }

    private fun extractAddress(text: String): String {
        val phoneRegex = Regex("""\b\d{10,}\b""")
        return phoneRegex.find(text)?.value ?: "unknown"
    }

    override fun onInterrupt() {
        Log.w(TAG, "Service interrupted")
    }

    override fun onDestroy() {
        debounceJob?.cancel()
        pendingMessages.clear()
        serviceScope.cancel()
        super.onDestroy()
    }
}
