package com.zwyft.horizon.ui.screens

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.zwyft.horizon.cloud.DriveViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: androidx.navigation.NavController,
    driveViewModel: DriveViewModel = hiltViewModel()
) {
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
            // ── Google Drive ──────────────────────────────────
            Text("Cloud Sync (Google Drive)", style = MaterialTheme.typography.titleMedium)

            if (driveUi.signedIn) {
                Button(
                    onClick = { driveViewModel.signOut() },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) { Text("Sign Out of Google Drive") }

                Spacer(Modifier.height(8.dp))

                Button(
                    onClick = { driveViewModel.uploadBackup() },
                    enabled = !driveUi.uploading
                ) { Text(if (driveUi.uploading) "Uploading..." else "Backup Now") }

                Spacer(Modifier.height(8.dp))

                Button(onClick = { driveViewModel.listBackups() }) { Text("List Backups") }

                if (driveUi.backups.isNotEmpty()) {
                    Text("Available backups:", style = MaterialTheme.typography.bodyMedium)
                    driveUi.backups.take(3).forEach { backup ->
                        Text("• ${backup.name} (${backup.createdTime}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            } else {
                Button(onClick = {
                    val intent = driveViewModel.getSignInIntent()
                    signInLauncher.launch(intent)
                }) { Text("Sign in with Google Drive") }
            }

            // ── Encryption ───────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Enable Encryption (E2E)", style = MaterialTheme.typography.bodyLarge)
                Switch(checked = false /* read from settings */, onCheckedChange = { /* toggle */ })
            }

            // ── API Key ─────────────────────────────────────
            var apiKey by remember { mutableStateOf("") }
            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                label = { Text("NousResearch API Key") },
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                onClick = {
                    // Save to settings
                    scope.launch {
                        snackbarHostState.showSnackbar("API key saved")
                    }
                },
                enabled = apiKey.isNotBlank()
            ) { Text("Save API Key") }

            driveUi.error?.let { err ->
                Text(err, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
