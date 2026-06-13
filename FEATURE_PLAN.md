# Horizon — Feature Implementation Plan
*Last updated: 2026-06-12*

## Scope
1. **Journal Edit / Annotate** — make journal entries editable
2. **Real RCS Capture** — replace the disabled `captureFromWindow` stub with working RCS capture
3. **MMS Attachment Handling** — populate `attachedFilePath`, store binaries, render in UI
4. **On-Device LLM** — "Local AI" provider that runs a model on-device, OpenAI-compatible

---

## Research Summary (verbatim from researcher-web)

### Feature 2 — RCS Capture
- **No public ContentProvider for RCS.** RCS messages are end-to-end-encrypted
  and stored in each messaging app's private database. Neither `content://sms`
  nor `content://mms` exposes them.
- **NotificationListenerService is the most stable path** for getting RCS
  text. It sees the message as a notification regardless of E2EE.
- **AccessibilityService is the fallback** for in-conversation RCS (older
  bubbles). Layouts change with every app version — fragile, requires
  constant maintenance.
- **Google Play policy concern**: AccessibilityService is for accessibility
  use cases. For a personal-use app sideloaded as debug, this is a non-issue.
  The current `MessageCaptureService` is *registered* and *config-flagged*
  but `captureFromWindow` is commented out. We just need to enable it.
- **Bundle ID for Google Messages**: `com.google.android.apps.messaging`.
  Already in `TARGET_PACKAGES`. Samsung: `com.samsung.android.messaging`.
  AOSP: `com.android.mms`. All already present.

### Feature 3 — MMS Attachments
- Canonical metadata URI: `content://mms` (Telephony.Mms.CONTENT_URI).
- Parts URI: `content://mms/{msg_id}/part`. Part types: `text/plain`,
  `image/jpeg`, `image/png`, `video/3gpp`, `audio/amr`, `application/smil`.
- **Use `ContentResolver.openInputStream()` for binaries** — never read
  `_data` column or `/data/data/...` (Scoped Storage + SELinux block it
  on modern Android).
- **READ_SMS is sufficient permission** for both `content://mms` reads
  and `content://mms/{id}/part` reads. No `READ_MEDIA_*` needed.
- **Copy bytes to app-private `filesDir/attachments/`** so the URI grant
  can't expire before the user reopens the message.
- **Klinker's `android-smsmms` library is archived** as of 2026 but its
  patterns still work for fresh implementations. We do it from scratch.

### Feature 4 — On-Device LLM
- **Google AI Edge Gallery's inference engine**: `LiteRT-LM` (successor to
  TFLite + MediaPipe LLM Inference). LiteRT-LM is the future; the
  published Maven artifact for it is still in flux as of 2026-Q2.
- **Working dependency today**: `com.google.mediapipe:tasks-genai:0.10.20`
  (MediaPipe LLM Inference). This is what AI Edge Gallery itself ships
  with. The user can migrate to LiteRT-LM later by swapping the dependency.
- **Model format**: `.task` (a bundle containing model + tokenizer +
  metadata). Google's "Gemma 3 1B IT int4" and "Phi-4 mini int4" are
  pre-bundled for the GPU delegate.
- **Storage strategy**: download on first use, not bundled (1B int4 ≈
  600-900MB, 3B int4 ≈ 1.8-2.5GB). Store in `filesDir/models/`.
- **OpenAI compatibility pattern**: run a tiny embedded Ktor HTTP server
  on `127.0.0.1:8088` exposing `/v1/chat/completions`. Map internal
  `LlmInference.generateResponse()` calls to OpenAI JSON. The existing
  `AiApi` Retrofit interface then works unchanged — `AiClientFactory`
  just constructs a Retrofit pointing at `http://127.0.0.1:8088/` instead
  of `https://openrouter.ai/api/`.
- **Reference projects**: `SmolChat-Android` (GGUF), `HostAI`
  (LiteRT-LM server), `google-ai-edge/gallery` (the official sample).

---

## Implementation Order

Dependencies chain this way (each step only depends on what's already in
the codebase, not on later steps):

```
Step 1 ──► Step 2 ──► Step 3 ──► Step 4
```

### Step 1 — Journal Edit/Annotate (smallest, lowest risk)
- **DB**: new migration 4→5 adds `userNotes TEXT`, `userEdited INTEGER`
  columns to `journal_entries`. Default empty/false.
- **DAO**: `setUserNotes(id, notes)`, `setUserEdited(id, true)`.
- **ViewModel**: `saveAnnotation(id, notes)` — sets the flag too.
- **UI**: `JournalDetailScreen` gets an Edit pencil in the top bar.
  Tap → AlertDialog with a multi-line `OutlinedTextField` for the
  notes, plus a Save button. The notes section shows below the
  AI body in the detail view (with a clear "Your notes" header so
  the user can tell AI content from their own annotations).
- **Regenerate preservation**: `regenerateEntry` should preserve the
  user's `userNotes` (only the AI body is being replaced).

### Step 2 — MMS Attachment Handling
- **DB**: a new `message_attachments` table (one-to-many with messages).
  Schema migration 5→6. Fields: `id`, `messageId` (FK to messages.id),
  `mimeType`, `localPath`, `originalName`, `sizeBytes`, `sortOrder`.
  We use a separate table because a single MMS can have multiple
  attachments (image + text + audio).
- **MmsReader**: new class `service/MmsReader.kt`. Queries
  `Telephony.Mms.CONTENT_URI`, joins to `content://mms/{id}/addr` for
  the address, queries `content://mms/{id}/part` for each part. Copies
  binary parts to `filesDir/attachments/{messageId}/{partId}.{ext}` via
  `ContentResolver.openInputStream()`. Text parts get concatenated into
  the body. Returns `MessageEntity` + list of `MmsAttachment` per
  message.
- **Integration**: `SmsMessageReader` already calls `Telephony.Sms.*`
  — add a parallel `readMmsInbox()` / `readMmsSent()` to it. The
  `MessageSyncManager` then calls both, inserts messages, and inserts
  attachments in the same batch.
- **DAO**: `MessageAttachmentDao` with `insertAll`, `getForMessage(id)`,
  `getForMessages(ids)`, `deleteForMessage(id)`.
- **UI**: `MessageDetailRow` (in Screens.kt) shows attachment thumbnails
  via Coil. Tapping opens a fullscreen viewer. Audio has a play button
  via `MediaPlayer`.

### Step 3 — Real RCS Capture
- **NotificationListener changes**:
  - Use `sbn.notification.extras.getParcelableArrayList(Notification.EXTRA_MESSAGING_PERSON)`
    and `EXTRA_MESSAGES` to extract `MessagingStyle` from Google Messages.
  - Each `Message` in the style has a `text` + `sender` + `timestamp`.
    We iterate and insert each as a separate `MessageEntity` (instead
    of the current "concat the notification text into one entity" hack).
  - For outgoing (type=2): check `Message.isRemoteInput` or whether the
    notification has a "Direct reply" action with the typed text. Not
    always available, but when it is, we capture it.
  - Set `rcs = true` for any notification from `com.google.android.apps.messaging`
    that has a `MessagingStyle`. (SMS notifications don't use MessagingStyle.)
  - Update `contactName` from the MessagingStyle `person.name` if present.
- **AccessibilityService changes**:
  - Enable `captureFromWindow` with a proper implementation.
  - In `accessibility_service_config.xml`, add `flagIncludeNotImportantViews`
    and bump `notificationTimeout` to 200ms (we want frequent sampling).
  - Strategy: on `TYPE_WINDOW_STATE_CHANGED` and
    `TYPE_WINDOW_CONTENT_CHANGED`, find the RecyclerView containing
    message bubbles (resource-id `com.google.android.apps.messaging:id/conversation_recycler_view`
    or fall back to any RecyclerView with TextView children). Walk
    each child, find the sender name (TextView above the bubble) and
    the bubble text (TextView with `maxLines=0` and large text). Skip
    nodes we already have (`messageId` is a content hash + position).
  - Add a debounced buffer — we only insert after 2s of stable state
    (no new events for the same thread). This avoids re-inserting the
    same messages when the user scrolls.
  - Log a warning if the message was already in the DB (insert OR
    ignore strategy makes this safe).
- **Manifest**: nothing new. AccessibilityService and NotificationListener
  are both already registered. We just add a `<uses-permission>` for
  `RECEIVE_MMS` and `READ_CELL_BROADCASTS` for completeness on
  Android 14+.
- **Testing**: install the APK, enable both services in Android
  Settings → Apps → Special access, send a real RCS message from
  another device or simulator, verify it lands in the DB.

### Step 4 — On-Device LLM
- **Dependency**: `com.google.mediapipe:tasks-genai:0.10.20`. This
  transitively pulls `com.google.mediapipe:tasks-core` and the GPU
  delegate.
- **Manifest**: a new foreground service `LocalLlmServerService` for
  the Ktor server. `INTERNET` permission is already declared.
- **Model download**: a `LocalModelManager` class. Uses `WorkManager` +
  `OkHttp` to download the `.task` file from a known URL (we'll use
  Google's `https://storage.googleapis.com/mediapipe-models/llm_inference/...`
  bucket — same source AI Edge Gallery uses). Progress is shown in
  the Settings screen via a `Flow<Long>` of bytes-downloaded.
- **Inference engine**: `LocalLlmEngine` wraps `LlmInference` from
  MediaPipe. Constructor takes the path to the downloaded `.task` file.
  Methods: `generate(prompt: String): String` (synchronous) and
  `generateStream(prompt: String): Flow<String>` (token-by-token).
- **OpenAI-compatible server**: `LocalLlmServer` (Ktor) binds
  `127.0.0.1:8088` and exposes:
  - `GET /v1/models` → JSON list of the locally-installed model
  - `POST /v1/chat/completions` → maps the OpenAI request body to
    `LlmInference.generateResponse()`. Returns either a normal
    `ChatCompletion` or an SSE stream depending on `stream: true`.
  - `GET /health` → `200 OK` for the AiClientFactory's connection check.
- **Provider enum**: add `LOCAL("Local On-Device", "http://127.0.0.1:8088/")`.
- **AiClientFactory**: when provider is `LOCAL`, build a Retrofit
  client pointing at the local server. The `Authorization: Bearer`
  header gets ignored (no API key needed).
- **resolveProvider/resolveApiKey**: LOCAL doesn't need an API key.
  `resolveApiKey` returns an empty string for LOCAL. `resolveProvider`
  checks if a local model ID starts with `local:` prefix and routes
  to LOCAL.
- **ModelRegistry additions**:
  - `LOCAL_GEMMA3_1B` ("gemma-3-1b-it-int4.task")
  - `LOCAL_GEMMA3_2B` ("gemma-3-2b-it-int4.task")
  - `LOCAL_PHI4_MINI` ("phi-4-mini-int4.task")
  - All have `apiProvider = AiProvider.LOCAL`, `free = true`.
- **Settings UI**: new "Local AI" section:
  - Toggle: "Enable local AI" (default off; starts/stops the service).
  - Card showing server status: Stopped / Running / Model loading.
  - Model picker dropdown.
  - Download button + progress bar when no model is downloaded.
  - "Test" button that runs a `curl`-like request through the chat UI.
- **Fallback policy**: if LOCAL is selected but the model isn't
  downloaded, the chat screen shows "Local model not downloaded.
  Open Settings → Local AI to download." If the server isn't running,
  "Local AI not running. Tap 'Start' in Settings."

---

## Build / Test Plan

After each feature, I run `./gradlew assembleDebug` to verify it
compiles. After all four features:

1. `./gradlew assembleDebug`
2. `adb install -r app-debug.apk`
3. Open the app, navigate through every screen, verify no crashes
4. Test journal edit (open an entry, tap edit, type, save, reopen)
5. Test MMS capture (send/receive an MMS, check DB has the binary)
6. Test RCS capture (send an RCS, check it lands)
7. Test local LLM (toggle on, download model, run a test query)
8. Capture logcat for any errors and fix
9. Spawn a `code-reviewer-minimax-m3` to review the diff

---

## Files Touched (estimate)

### Step 1 — ~5 files
- `data/entity/JournalEntryEntity.kt` (add fields)
- `data/HorizonDatabase.kt` (add migration 4→5)
- `data/dao/JournalEntryDao.kt` (add setters)
- `ai/JournalViewModel.kt` (add saveAnnotation)
- `ui/screens/Screens.kt` (JournalDetailScreen edit button + dialog)

### Step 2 — ~7 new files + 4 edits
- `data/entity/MessageAttachmentEntity.kt` (new)
- `data/dao/MessageAttachmentDao.kt` (new)
- `service/MmsReader.kt` (new)
- `service/SmsMessageReader.kt` (add MMS methods)
- `service/MessageSyncManager.kt` (call MMS reader)
- `data/HorizonDatabase.kt` (migration 5→6, register entity)
- `ui/screens/Screens.kt` (show attachments in MessageDetailRow)
- `service/AttachmentViewerActivity.kt` (new — fullscreen viewer)

### Step 3 — ~3 files
- `service/NotificationListener.kt` (use MessagingStyle)
- `service/MessageCaptureService.kt` (enable captureFromWindow)
- `res/xml/accessibility_service_config.xml` (more flags)

### Step 4 — ~8 new files + 6 edits
- `service/local/LocalLlmEngine.kt` (new — MediaPipe wrapper)
- `service/local/LocalModelManager.kt` (new — download manager)
- `service/local/LocalLlmServer.kt` (new — Ktor server)
- `service/local/LocalLlmServerService.kt` (new — foreground service)
- `ai/NousApiClient.kt` (add LOCAL to AiProvider, ModelRegistry)
- `di/LocalLlmModule.kt` (new — Hilt bindings)
- `settings/SettingsViewModel.kt` (add local AI state)
- `ui/screens/SettingsScreen.kt` (add Local AI section)
- `AndroidManifest.xml` (register foreground service)
- `build.gradle.kts` (add MediaPipe dependency)

---

## Risks & Mitigations

| Risk | Mitigation |
|------|-----------|
| MediaPipe dependency bloats APK by 50MB+ | OK for a debug build. Release can use App Bundle splits. |
| MediaPipe model download is 600MB-2GB | Use background WorkManager, allow pause/resume, show progress |
| AccessibilityService layout scraping breaks on Google Messages update | Log a version-stamp in logcat when scraping fails; fail open (just don't capture that message) |
| `content://mms` requires user to be default SMS app on some Android versions | Test on the actual phone. If it fails, surface a helpful error in the UI. |
| Local LLM is slow on older devices | Show a "Generating... (~30s on this device)" estimate based on device tier |
