package com.zwyft.horizon.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.zwyft.horizon.ai.AiProvider
import com.zwyft.horizon.ai.ModelRegistry
import com.zwyft.horizon.cloud.DriveViewModel
import com.zwyft.horizon.service.local.LocalModelManager
import com.zwyft.horizon.settings.SettingsViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: androidx.navigation.NavController,
    settingsViewModel: SettingsViewModel = hiltViewModel(),
    driveViewModel: DriveViewModel = hiltViewModel()
) {
    val snackbarHostState = remember { SnackbarHostState() }
    Scaffold(
        topBar = { TopAppBar(title = { Text("Settings") }) },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        SettingsContent(
            modifier = Modifier.padding(padding),
            settingsViewModel = settingsViewModel,
            driveViewModel = driveViewModel,
            snackbarHostState = snackbarHostState
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsContent(
    modifier: Modifier = Modifier,
    settingsViewModel: SettingsViewModel = hiltViewModel(),
    driveViewModel: DriveViewModel = hiltViewModel(),
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() }
) {
    val settingsUi by settingsViewModel.uiState.collectAsState()
    val driveUi by driveViewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()

    val signInLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result -> driveViewModel.signIn(result.data) }

    // Local mutable state for API key fields (so typing/pasting works)
    var nousKeyInput by remember { mutableStateOf(settingsUi.apiKey) }
    var openrouterKeyInput by remember { mutableStateOf(settingsUi.openrouterKey) }

    // Sync local state with ViewModel when saved keys load from DB
    LaunchedEffect(settingsUi.apiKey) { nousKeyInput = settingsUi.apiKey }
    LaunchedEffect(settingsUi.openrouterKey) { openrouterKeyInput = settingsUi.openrouterKey }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ── AI Provider ──────────────────────────────
        SettingsCard(title = "AI Provider", icon = Icons.Filled.SmartToy) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AiProvider.entries.forEach { provider ->
                    FilterChip(
                        selected = settingsUi.selectedProvider == provider,
                        onClick = { settingsViewModel.setSelectedProvider(provider) },
                        label = { Text(provider.displayName) },
                        leadingIcon = {
                            Icon(
                                when (provider) {
                                    AiProvider.NOUS -> Icons.Filled.Rocket
                                    AiProvider.OPENROUTER -> Icons.Filled.Hub
                                    AiProvider.LOCAL -> Icons.Filled.PhoneAndroid
                                },
                                contentDescription = null, modifier = Modifier.size(16.dp)
                            )
                        }
                    )
                }
            }

            if (settingsUi.selectedProvider == AiProvider.NOUS) {
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = nousKeyInput,
                        onValueChange = { nousKeyInput = it },
                        label = { Text("NousResearch API Key") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            settingsViewModel.saveApiKey(nousKeyInput)
                            scope.launch { snackbarHostState.showSnackbar("API key saved") }
                        },
                        enabled = nousKeyInput.isNotBlank()
                    ) { Text("Save") }
                }
            }

            if (settingsUi.selectedProvider == AiProvider.OPENROUTER) {
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = openrouterKeyInput,
                        onValueChange = { openrouterKeyInput = it },
                        label = { Text("OpenRouter API Key") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            settingsViewModel.saveOpenRouterKey(openrouterKeyInput)
                            scope.launch { snackbarHostState.showSnackbar("API key saved") }
                        },
                        enabled = openrouterKeyInput.isNotBlank()
                    ) { Text("Save") }
                }
            }
        }

        // ── Local AI ──────────────────────────────────
        SettingsCard(title = "Local AI", icon = Icons.Filled.PhoneAndroid) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                // Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Enable Local AI", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            if (settingsUi.localAiServerRunning) "Running on 127.0.0.1:8088" else "Stopped",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (settingsUi.localAiServerRunning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = settingsUi.localAiEnabled, onCheckedChange = { settingsViewModel.setLocalAiEnabled(it) })
                }

                Spacer(Modifier.height(8.dp))

                // Model picker
                var expanded by remember { mutableStateOf(false) }
                val label = LocalModelManager.KNOWN_MODELS[settingsUi.localAiModelId]?.label ?: settingsUi.localAiModelId
                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                    OutlinedTextField(
                        value = label, onValueChange = {}, readOnly = true, label = { Text("Model") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor()
                    )
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        ModelRegistry.LOCAL_MODELS.forEach { id ->
                            DropdownMenuItem(
                                text = { Text(LocalModelManager.KNOWN_MODELS[id]?.label ?: id) },
                                onClick = { settingsViewModel.setLocalAiModel(id); expanded = false }
                            )
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                // Download
                if (!settingsUi.localAiModelDownloaded) {
                    if (settingsUi.localAiDownloading) {
                        LinearProgressIndicator(
                            progress = {
                                if (settingsUi.localAiDownloadTotal > 0) settingsUi.localAiDownloadBytes.toFloat() / settingsUi.localAiDownloadTotal
                                else 0f
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            "${formatBytes(settingsUi.localAiDownloadBytes)} / ${formatBytes(settingsUi.localAiDownloadTotal)}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    } else {
                        Button(onClick = { settingsViewModel.downloadLocalModel() }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Filled.Download, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Download Model")
                        }
                    }
                } else {
                    Text("Model downloaded ✓", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                }

                settingsUi.localAiDownloadError?.let { err ->
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                        Text(err, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
            }
        }

        // ── Cloud Sync ─────────────────────────────────
        SettingsCard(title = "Cloud Sync", icon = Icons.Filled.CloudSync) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Google Drive Backup", style = MaterialTheme.typography.bodyLarge)
                    Switch(checked = settingsUi.cloudSyncEnabled, onCheckedChange = { settingsViewModel.setCloudSyncEnabled(it) })
                }
                if (settingsUi.cloudSyncEnabled) {
                    Spacer(Modifier.height(8.dp))
                    if (driveUi.signedIn) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { driveViewModel.uploadBackup() }, enabled = !driveUi.uploading) {
                                Text(if (driveUi.uploading) "Uploading..." else "Backup Now")
                            }
                            OutlinedButton(onClick = { driveViewModel.signOut() }) { Text("Sign Out") }
                        }
                    } else {
                        Button(onClick = { signInLauncher.launch(driveViewModel.getSignInIntent()) }) { Text("Sign in with Google") }
                    }
                }
            }
        }

        // ── Privacy ────────────────────────────────────
        SettingsCard(title = "Privacy & Security", icon = Icons.Filled.Security) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Database Encryption", style = MaterialTheme.typography.bodyLarge)
                    Text("Requires app restart", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = settingsUi.encryptionEnabled, onCheckedChange = { settingsViewModel.setEncryptionEnabled(it) })
            }
        }

        // ── Advanced ───────────────────────────────────
        SettingsCard(title = "Advanced", icon = Icons.Filled.Tune) {
            Button(
                onClick = { scope.launch { snackbarHostState.showSnackbar("Clear all data — not yet implemented") } },
                modifier = Modifier.padding(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer)
            ) { Text("Clear All Data") }
        }

        // Errors
        settingsUi.error?.let { Text("Error: $it", color = MaterialTheme.colorScheme.error) }
        driveUi.error?.let { Text("Drive: $it", color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
private fun SettingsCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            }
            content()
            Spacer(Modifier.height(12.dp))
        }
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
    else -> "%.1f GB".format(bytes.toDouble() / (1024 * 1024 * 1024))
}
