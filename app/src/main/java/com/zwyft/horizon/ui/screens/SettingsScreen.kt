package com.zwyft.horizon.ui.screens

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.zwyft.horizon.cloud.DriveViewModel
import com.zwyft.horizon.settings.SettingsViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: androidx.navigation.NavController,
    settingsViewModel: SettingsViewModel = hiltViewModel(),
    driveViewModel: DriveViewModel = hiltViewModel()
) {
    val settingsUi by settingsViewModel.uiState.collectAsState()
    val driveUi by driveViewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Google Sign-In launcher
    val signInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        driveViewModel.signIn(result.data)
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Settings") }) },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── AI Configuration ───────────────────────
            Text("AI Configuration", style = MaterialTheme.typography.titleMedium)

            OutlinedTextField(
                value = settingsUi.apiKey,
                onValueChange = { /* handled by save button */ },
                label = { Text("NousResearch API Key") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                trailingIcon = {
                    Button(
                        onClick = {
                            settingsViewModel.saveApiKey(settingsUi.apiKey)
                            scope.launch {
                                snackbarHostState.showSnackbar("API key saved")
                            }
                        },
                        enabled = settingsUi.apiKey.isNotBlank()
                    ) { Text("Save") }
                }
            )

            if (settingsUi.apiKeySaved) {
                Text("API key saved ✓", color = MaterialTheme.colorScheme.primary)
                LaunchedEffect(Unit) {
                    kotlinx.coroutines.delay(2000)
                    settingsViewModel.clearApiKeySaved()
                }
            }

            Divider()

            // ── Google Drive Cloud Sync ───────────────
            Text("Cloud Sync (Google Drive)", style = MaterialTheme.typography.titleMedium)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Enable Google Drive Sync", style = MaterialTheme.typography.bodyLarge)
                Switch(
                    checked = settingsUi.cloudSyncEnabled,
                    onCheckedChange = { enabled ->
                        settingsViewModel.setCloudSyncEnabled(enabled)
                        if (enabled && !driveUi.signedIn) {
                            val intent = driveViewModel.getSignInIntent()
                            signInLauncher.launch(intent)
                        }
                    }
                )
            }

            if (settingsUi.cloudSyncEnabled) {
                if (driveUi.signedIn) {
                    Button(
                        onClick = { driveViewModel.uploadBackup() },
                        enabled = !driveUi.uploading
                    ) { Text(if (driveUi.uploading) "Uploading..." else "Backup Now") }

                    Button(
                        onClick = { driveViewModel.signOut() },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                    ) { Text("Sign Out of Google Drive") }
                } else {
                    Button(
                        onClick = {
                            val intent = driveViewModel.getSignInIntent()
                            signInLauncher.launch(intent)
                        }
                    ) { Text("Sign in with Google Drive") }
                }
            }

            Divider()

            // ── Privacy & Security ────────────────────
            Text("Privacy & Security", style = MaterialTheme.typography.titleMedium)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Enable Encryption (E2E)", style = MaterialTheme.typography.bodyLarge)
                    Text("Encrypt local database (requires restart)", style = MaterialTheme.typography.bodySmall)
                }
                Switch(
                    checked = settingsUi.encryptionEnabled,
                    onCheckedChange = { enabled ->
                        settingsViewModel.setEncryptionEnabled(enabled)
                        scope.launch {
                            snackbarHostState.showSnackbar("Encryption ${if (enabled) "enabled" else "disabled"} — restart app to apply")
                        }
                    }
                )
            }

            Divider()

            // ── Advanced ──────────────────────────────
            Text("Advanced", style = MaterialTheme.typography.titleMedium)

            Button(
                onClick = {
                    // TODO: Clear all data
                    scope.launch {
                        snackbarHostState.showSnackbar("Clear all data — TODO")
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer)
            ) { Text("Clear All Data") }

            // Errors
            settingsUi.error?.let { err ->
                Text("Error: $err", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            driveUi.error?.let { err ->
                Text("Drive Error: $err", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
