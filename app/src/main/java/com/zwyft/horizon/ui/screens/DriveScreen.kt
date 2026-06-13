package com.zwyft.horizon.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.zwyft.horizon.cloud.DriveViewModel
import com.zwyft.horizon.cloud.DriveFileInfo
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * Screen for Google Drive backup & restore operations.
 *
 * Shows sign-in status, allows triggering uploads, lists available
 * backups with download/delete per backup, and auto-backup toggle.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriveScreen(
    navController: androidx.navigation.NavController,
    viewModel: DriveViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val dateFmt = remember { SimpleDateFormat("MMM d, yyyy h:mm a", Locale.getDefault()) }

    val signInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        viewModel.signIn(result.data)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Google Drive Backup") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ── Account card ──
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        if (uiState.signedIn) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Signed in", style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        "Backups stored in your private Drive app data folder",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                OutlinedButton(onClick = { viewModel.signOut() }) {
                                    Text("Sign Out")
                                }
                            }
                        } else {
                            Text("Not signed in", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Sign in with Google to back up your database to the cloud. "
                                    + "Backups are stored in Drive's app data folder (visible only to this app).",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(12.dp))
                            Button(
                                onClick = {
                                    val intent = viewModel.getSignInIntent()
                                    if (intent != null) {
                                        signInLauncher.launch(intent)
                                    } else {
                                        scope.launch {
                                            snackbarHostState.showSnackbar("Google Play Services not available")
                                        }
                                    }
                                }
                            ) {
                                Icon(Icons.Filled.AccountCircle, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Sign in with Google")
                            }
                        }
                    }
                }
            }

            // ── Upload section ──
            if (uiState.signedIn) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Backup", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Upload your current database to Google Drive. Existing backups are not affected.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = { viewModel.uploadBackup() },
                                    enabled = !uiState.uploading
                                ) {
                                    if (uiState.uploading) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            strokeWidth = 2.dp,
                                            color = MaterialTheme.colorScheme.onPrimary
                                        )
                                        Spacer(Modifier.width(8.dp))
                                    }
                                    Icon(Icons.Filled.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(if (uiState.uploading) "Uploading…" else "Upload Backup")
                                }
                            }
                        }
                    }
                }

                // ── Backups list ──
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Available Backups", style = MaterialTheme.typography.titleMedium)
                        TextButton(
                            onClick = { viewModel.refreshBackups() },
                            enabled = !uiState.loadingBackups
                        ) {
                            if (uiState.loadingBackups) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(Modifier.width(4.dp))
                            }
                            Text(if (uiState.loadingBackups) "Refreshing…" else "Refresh")
                        }
                    }
                }

                if (uiState.backups.isEmpty() && !uiState.loadingBackups) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            )
                        ) {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        Icons.Filled.CloudOff,
                                        contentDescription = null,
                                        modifier = Modifier.size(40.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        "No backups yet. Tap Upload Backup to create one.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                items(uiState.backups) { file ->
                    BackupCard(
                        file = file,
                        dateFmt = dateFmt,
                        restoring = uiState.restoring,
                        onRestore = { viewModel.restoreBackup(file.id) },
                        onDelete = { viewModel.deleteBackup(file.id) }
                    )
                }

                // ── Restore success banner ──
                if (uiState.lastRestoreSucceeded == true) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Filled.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "Restore succeeded. The app will use the restored database on next launch.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }
                }

                // ── Last backup info ──
                uiState.lastBackupId?.let { id ->
                    item {
                        Text(
                            "Last upload ID: $id",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // ── Error banner ──
            uiState.error?.let { err ->
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Filled.Error,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                err,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BackupCard(
    file: DriveFileInfo,
    dateFmt: SimpleDateFormat,
    restoring: Boolean,
    onRestore: () -> Unit,
    onDelete: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Filled.Cloud,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    file.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    dateFmt.format(Date(file.modifiedTime)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                OutlinedButton(
                    onClick = onRestore,
                    enabled = !restoring
                ) {
                    if (restoring) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Restore")
                    }
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = "Delete backup",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}
