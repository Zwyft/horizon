package com.horizon.coparentinglog

import android.Manifest
import android.app.Activity
import android.app.role.RoleManager
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ContactPage
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.DateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.horizon.coparentinglog.core.ArchiveSnapshot
import com.horizon.coparentinglog.core.AppSettings
import com.horizon.coparentinglog.core.ContactIdentity
import com.horizon.coparentinglog.core.JournalEntryRecord
import com.horizon.coparentinglog.core.JournalGenerationRequest
import com.horizon.coparentinglog.data.ArchiveRepository
import com.horizon.coparentinglog.data.JournalComposer
import com.horizon.coparentinglog.data.OpenRouterClient
import com.horizon.coparentinglog.data.SettingsLabels
import com.horizon.coparentinglog.data.SettingsRepository
import com.horizon.coparentinglog.data.TelephonyMessageSource
import com.horizon.coparentinglog.sync.SyncCoordinator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.io.OutputStreamWriter

class MainActivity : ComponentActivity() {
    private val viewModel by viewModels<MainViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CoParentingTheme {
                AppRoot(viewModel)
            }
        }
    }
}

class MainViewModel(application: android.app.Application) : AndroidViewModel(application) {
    private val settingsRepository = SettingsRepository(application)
    private val archiveRepository = ArchiveRepository(application)
    private val telephonySource = TelephonyMessageSource(application)
    private val journalComposer = JournalComposer(OpenRouterClient())

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val settings = settingsRepository.read()
            val archive = archiveRepository.read()
            _uiState.value = _uiState.value.copy(
                settings = settings,
                archive = archive,
            )
            if (settings.contactsPermissionGranted) {
                refreshContacts()
            }
        }
    }

    fun markDefaultSmsGranted(granted: Boolean) {
        updateSettings { it.copy(defaultSmsGranted = granted) }
    }

    fun setContactsPermission(granted: Boolean) {
        updateSettings { it.copy(contactsPermissionGranted = granted) }
        refreshContacts()
    }

    fun setMessagesPermission(granted: Boolean) {
        updateSettings { it.copy(messagesPermissionGranted = granted) }
    }

    fun completeOnboarding() {
        updateSettings { it.copy(onboardingComplete = true) }
    }

    fun saveOpenRouterKey(key: String) {
        updateSettings { it.copy(openRouterApiKey = key.trim()) }
    }

    fun saveModels(analysis: String, journal: String) {
        updateSettings { it.copy(analysisModel = analysis.trim(), journalModel = journal.trim()) }
    }

    fun saveRegion(region: String) {
        updateSettings { it.copy(defaultRegionIso = region.uppercase().ifBlank { "US" }) }
    }

    fun testOpenRouterKey() {
        viewModelScope.launch {
            val settings = settingsRepository.read()
            val key = settings.openRouterApiKey
            if (key.isBlank()) {
                _uiState.value = _uiState.value.copy(status = "Enter an OpenRouter key before testing.")
                return@launch
            }
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    OpenRouterClient().generateMarkdown(
                        apiKey = key,
                        model = settings.analysisModel,
                        prompt = "Reply with the single word OK if you can read this.",
                    )
                }
            }
            _uiState.value = _uiState.value.copy(
                status = result.fold(
                    onSuccess = { "OpenRouter key works with ${settings.analysisModel}." },
                    onFailure = { "OpenRouter test failed: ${it.message.orEmpty()}" },
                ),
            )
        }
    }

    fun toggleManagedRcs(enabled: Boolean) {
        updateSettings { it.copy(managedRcsEnabled = enabled) }
    }

    fun syncNow() {
        viewModelScope.launch {
            val settings = settingsRepository.read()
            if (!settings.defaultSmsGranted || !settings.messagesPermissionGranted) {
                _uiState.value = _uiState.value.copy(status = "Grant the SMS role and message permissions first.")
                return@launch
            }
            _uiState.value = _uiState.value.copy(status = "Syncing message archive...")
            val archive = withContext(Dispatchers.IO) { SyncCoordinator(getApplication()).syncArchive() }
            _uiState.value = _uiState.value.copy(
                archive = archive,
                status = "Archive synced ${archive.messages.size} messages.",
            )
        }
    }

    fun refreshContacts() {
        viewModelScope.launch {
            val settings = settingsRepository.read()
            if (!settings.contactsPermissionGranted) return@launch
            val contacts = runCatching { telephonySource.loadContacts() }.getOrElse { emptyList() }
            _uiState.value = _uiState.value.copy(contacts = contacts)
        }
    }

    fun setRange(start: LocalDate, end: LocalDate) {
        _uiState.value = _uiState.value.copy(selectedStartDate = start, selectedEndDate = end)
    }

    fun selectContact(contact: ContactIdentity?) {
        _uiState.value = _uiState.value.copy(selectedContact = contact)
    }

    fun generateJournal() {
        viewModelScope.launch {
            val current = _uiState.value
            val settings = current.settings
            if (settings.openRouterApiKey.isBlank()) {
                _uiState.value = current.copy(status = "Add your OpenRouter key first.")
                return@launch
            }
            val newEntries = withContext(Dispatchers.IO) {
                journalComposer.buildJournalEntries(
                    apiKey = settings.openRouterApiKey,
                    analysisModel = settings.analysisModel,
                    journalModel = settings.journalModel,
                    archiveSnapshot = current.archive,
                    request = JournalGenerationRequest(
                        startDate = current.selectedStartDate,
                        endDate = current.selectedEndDate,
                        focusContact = current.selectedContact,
                    ),
                    defaultRegionIso = settings.defaultRegionIso,
                )
            }
            val merged = current.archive.copy(journalEntries = current.archive.journalEntries + newEntries)
            withContext(Dispatchers.IO) { archiveRepository.write(merged) }
            _uiState.value = current.copy(
                archive = merged,
                status = "Generated ${newEntries.size} journal entr${if (newEntries.size == 1) "y" else "ies"}.",
            )
        }
    }

    fun exportJournal(record: JournalEntryRecord) {
        _uiState.value = _uiState.value.copy(
            pendingExportJournal = record,
            status = "Choose a filename to export ${record.date}.",
        )
    }

    fun writeExport(uri: Uri) {
        val record = _uiState.value.pendingExportJournal ?: return
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                getApplication<android.app.Application>().contentResolver.openOutputStream(uri)?.use { stream ->
                    OutputStreamWriter(stream).use { writer ->
                        writer.write(record.markdown)
                    }
                }
            }
            _uiState.value = _uiState.value.copy(
                pendingExportJournal = null,
                status = "Exported ${record.date}.",
            )
        }
    }

    private fun updateSettings(transform: (AppSettings) -> AppSettings) {
        settingsRepository.update(transform)
        _uiState.value = _uiState.value.copy(settings = settingsRepository.read())
    }
}

data class UiState(
    val settings: AppSettings = AppSettings(),
    val archive: ArchiveSnapshot = ArchiveSnapshot(),
    val contacts: List<ContactIdentity> = emptyList(),
    val selectedContact: ContactIdentity? = null,
    val selectedStartDate: LocalDate = LocalDate.now().minusDays(1),
    val selectedEndDate: LocalDate = LocalDate.now(),
    val pendingExportJournal: JournalEntryRecord? = null,
    val status: String = "Ready.",
)

@Composable
private fun AppRoot(viewModel: MainViewModel) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/markdown")) { uri ->
        if (uri != null) {
            viewModel.writeExport(uri)
        }
    }

    val smsRoleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        viewModel.markDefaultSmsGranted(result.resultCode == Activity.RESULT_OK)
    }
    val permissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        viewModel.setMessagesPermission(grants[Manifest.permission.READ_SMS] == true)
        viewModel.setContactsPermission(grants[Manifest.permission.READ_CONTACTS] == true)
    }
    val contactPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickContact()) { uri ->
        val contactId = uri?.lastPathSegment ?: return@rememberLauncherForActivityResult
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.LOOKUP_KEY,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
        )
        val selection = "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?"
        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            projection,
            selection,
            arrayOf(contactId),
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val lookup = cursor.getString(0) ?: return@use
                val name = cursor.getString(1) ?: "Contact"
                val number = cursor.getString(2) ?: return@use
                val normalized = com.horizon.coparentinglog.core.MessageNormalization.normalize(number) ?: number
                viewModel.selectContact(
                    ContactIdentity(
                        lookupKey = lookup,
                        displayName = name,
                        normalizedNumber = normalized,
                        rawNumber = number,
                    ),
                )
            }
        }
    }

    LaunchedEffect(state.status) {
        snackbarHostState.showSnackbar(state.status)
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF0B1320),
                            Color(0xFF152238),
                            Color(0xFF1D2A44),
                        ),
                    ),
                )
                .padding(padding),
        ) {
            AnimatedContent(
                targetState = state.settings.onboardingComplete,
                label = "root-state",
                transitionSpec = { fadeIn() togetherWith fadeOut() },
            ) { onboarded ->
                if (!onboarded) {
                    OnboardingScreen(
                        state = state,
                        onEnableSms = {
                            val roleManager = context.getSystemService(RoleManager::class.java)
                            if (roleManager.isRoleAvailable(RoleManager.ROLE_SMS) && !roleManager.isRoleHeld(RoleManager.ROLE_SMS)) {
                                smsRoleLauncher.launch(roleManager.createRequestRoleIntent(RoleManager.ROLE_SMS))
                            } else {
                                viewModel.markDefaultSmsGranted(true)
                            }
                        },
                        onRequestPermissions = {
                            permissionsLauncher.launch(
                                arrayOf(
                                    Manifest.permission.READ_SMS,
                                    Manifest.permission.RECEIVE_SMS,
                                    Manifest.permission.SEND_SMS,
                                    Manifest.permission.READ_CONTACTS,
                                ),
                            )
                        },
                        onSaveKey = viewModel::saveOpenRouterKey,
                        onSaveModels = viewModel::saveModels,
                        onSaveRegion = viewModel::saveRegion,
                        onTestKey = viewModel::testOpenRouterKey,
                        onFinish = viewModel::completeOnboarding,
                    )
                } else {
                    HomeScreen(
                        state = state,
                        onSync = viewModel::syncNow,
                        onGenerate = viewModel::generateJournal,
                        onPickContact = { contactPicker.launch(null) },
                        onSetRange = viewModel::setRange,
                        onToggleManagedRcs = viewModel::toggleManagedRcs,
                        onExportJournal = { record ->
                            viewModel.exportJournal(record)
                            exportLauncher.launch("journal-${record.date}.md")
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun OnboardingScreen(
    state: UiState,
    onEnableSms: () -> Unit,
    onRequestPermissions: () -> Unit,
    onSaveKey: (String) -> Unit,
    onSaveModels: (String, String) -> Unit,
    onSaveRegion: (String) -> Unit,
    onTestKey: () -> Unit,
    onFinish: () -> Unit,
) {
    var apiKey by remember(state.settings.openRouterApiKey) { mutableStateOf(state.settings.openRouterApiKey) }
    var analysis by remember(state.settings.analysisModel) { mutableStateOf(state.settings.analysisModel) }
    var journal by remember(state.settings.journalModel) { mutableStateOf(state.settings.journalModel) }
    var region by remember(state.settings.defaultRegionIso) { mutableStateOf(state.settings.defaultRegionIso) }
    val steps = listOf(
        "Learn the flow",
        "Take the SMS role",
        "Authorize access",
        "Add your AI key",
        "Start your archive",
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "Co-parenting Log",
            style = MaterialTheme.typography.headlineLarge,
            color = Color.White,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "A private, local archive that turns message history into clean daily journal entries.",
            color = Color(0xFFD4DCEC),
        )

        steps.forEachIndexed { index, step ->
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF121D2E).copy(alpha = 0.94f)),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                shape = RoundedCornerShape(20.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("0${index + 1}", color = Color(0xFF7FB4FF), fontWeight = FontWeight.Bold)
                        Text(step, color = Color.White, fontWeight = FontWeight.SemiBold)
                    }
                    Icon(Icons.Default.ArrowForward, contentDescription = null, tint = Color(0xFF7FB4FF))
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AssistChip(
                onClick = onEnableSms,
                label = { Text(if (state.settings.defaultSmsGranted) "SMS role granted" else "Grant SMS role") },
                leadingIcon = { Icon(Icons.Default.Mail, contentDescription = null) },
            )
            AssistChip(
                onClick = onRequestPermissions,
                label = { Text("Grant permissions") },
                leadingIcon = { Icon(Icons.Default.Upload, contentDescription = null) },
            )
        }

        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF101A29))) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = apiKey, onValueChange = { apiKey = it }, label = { Text("OpenRouter API key") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = analysis, onValueChange = { analysis = it }, label = { Text("Analysis model") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = journal, onValueChange = { journal = it }, label = { Text("Journal model") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = region, onValueChange = { region = it.uppercase().take(2) }, label = { Text("Default region") }, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = {
                        onSaveKey(apiKey)
                        onSaveModels(analysis, journal)
                        onSaveRegion(region)
                    }) {
                        Text("Save configuration")
                    }
                    OutlinedButton(onClick = {
                        onSaveKey(apiKey)
                        onSaveModels(analysis, journal)
                        onSaveRegion(region)
                        onTestKey()
                    }) { Text("Test key") }
                    OutlinedButton(onClick = onFinish, enabled = state.settings.defaultSmsGranted && apiKey.isNotBlank()) {
                        Text("Enter app")
                    }
                }
                Text(
                    text = SettingsLabels.ManagedRcsUnavailable,
                    color = Color(0xFFB7C5D8),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScreen(
    state: UiState,
    onSync: () -> Unit,
    onGenerate: () -> Unit,
    onPickContact: () -> Unit,
    onSetRange: (LocalDate, LocalDate) -> Unit,
    onToggleManagedRcs: (Boolean) -> Unit,
    onExportJournal: (JournalEntryRecord) -> Unit,
) {
    var selectedTab by remember { mutableStateOf(0) }
    val rangeState = rememberDateRangePickerState(
        initialSelectedStartDateMillis = state.selectedStartDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
        initialSelectedEndDateMillis = state.selectedEndDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
    )

    LaunchedEffect(rangeState.selectedStartDateMillis, rangeState.selectedEndDateMillis) {
        val start = rangeState.selectedStartDateMillis?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate() }
        val end = rangeState.selectedEndDateMillis?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate() }
        if (start != null && end != null) onSetRange(start, end)
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F1A29).copy(alpha = 0.92f)),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
        ) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Archive", color = Color(0xFF8BB8FF), fontWeight = FontWeight.Bold)
                Text("Sync message history, then write a journal from any date range.", color = Color.White, style = MaterialTheme.typography.headlineSmall)
                Text(state.status, color = Color(0xFFD5DFEF))
                state.selectedContact?.let {
                    Text("Focused contact: ${it.displayName} • ${it.normalizedNumber}", color = Color(0xFFB7C5D8))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = onSync) { Text("Sync messages") }
                    OutlinedButton(onClick = onPickContact) {
                        Icon(Icons.Default.ContactPage, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Pick contact")
                    }
                }
            }
        }

        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            listOf("Archive", "Journal", "Settings").forEachIndexed { index, label ->
                SegmentedButton(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    shape = SegmentedButtonDefaults.itemShape(index, 3),
                ) {
                    Text(label)
                }
            }
        }

        when (selectedTab) {
            0 -> ArchivePanel(state)
            1 -> JournalPanel(state, rangeState, onGenerate, onExportJournal)
            else -> SettingsPanel(state, onToggleManagedRcs)
        }
    }
}

@Composable
private fun ArchivePanel(state: UiState) {
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF101A29))) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Recent synced messages", color = Color.White, fontWeight = FontWeight.SemiBold)
            if (state.archive.messages.isEmpty()) {
                Text("No messages have been synced yet.", color = Color(0xFFB7C5D8))
            } else {
                state.archive.messages.takeLast(8).forEach { message ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("${message.transport} • ${message.direction}", color = Color(0xFF8BB8FF))
                        Text(message.body ?: message.subject ?: "(no body)", color = Color.White)
                        Text(message.normalizedAddress ?: message.rawAddress ?: message.threadKey, color = Color(0xFFB7C5D8), style = MaterialTheme.typography.bodySmall)
                        HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                    }
                }
            }
        }
    }
}

@Composable
private fun JournalPanel(
    state: UiState,
    rangeState: DateRangePickerState,
    onGenerate: () -> Unit,
    onExportJournal: (JournalEntryRecord) -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF101A29))) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            DateRangePicker(state = rangeState, showModeToggle = false)
            Button(onClick = onGenerate) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Generate journal entry")
            }
        }
    }

    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF101A29))) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Journal entries", color = Color.White, fontWeight = FontWeight.SemiBold)
            if (state.archive.journalEntries.isEmpty()) {
                Text("Generated entries will appear here.", color = Color(0xFFB7C5D8))
            } else {
                state.archive.journalEntries.takeLast(6).reversed().forEach { journal ->
                    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF0E1726))) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(journal.date, color = Color(0xFF8BB8FF), fontWeight = FontWeight.Bold)
                            Text(journal.title, color = Color.White, style = MaterialTheme.typography.titleMedium)
                            Text(journal.markdown.take(220), color = Color(0xFFD4DCEC))
                            OutlinedButton(onClick = { onExportJournal(journal) }) {
                                Text("Export")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsPanel(state: UiState, onToggleManagedRcs: (Boolean) -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF101A29))) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Settings", color = Color.White, fontWeight = FontWeight.SemiBold)
            FilterChip(
                selected = state.settings.managedRcsEnabled,
                onClick = { onToggleManagedRcs(!state.settings.managedRcsEnabled) },
                label = { Text("Managed RCS archival") },
            )
            Text("Only available on fully managed devices with Google Messages archival.", color = Color(0xFFB7C5D8))
            Text("Default region: ${state.settings.defaultRegionIso}", color = Color(0xFFD4DCEC))
            Text("Analysis model: ${state.settings.analysisModel}", color = Color(0xFFD4DCEC))
            Text("Journal model: ${state.settings.journalModel}", color = Color(0xFFD4DCEC))
        }
    }
}

@Composable
private fun CoParentingTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = androidx.compose.material3.darkColorScheme(
            primary = Color(0xFF8BB8FF),
            secondary = Color(0xFFB5E8FF),
            tertiary = Color(0xFFFFD59E),
            background = Color(0xFF0A1220),
            surface = Color(0xFF101A29),
            onPrimary = Color(0xFF08111E),
            onSecondary = Color(0xFF08111E),
            onTertiary = Color(0xFF08111E),
            onBackground = Color.White,
            onSurface = Color.White,
        ),
        content = content,
    )
}
