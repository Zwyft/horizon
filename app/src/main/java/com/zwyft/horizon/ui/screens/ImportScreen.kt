package com.zwyft.horizon.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.zwyft.horizon.importing.ImportViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportScreen(
    navController: androidx.navigation.NavController,
    viewModel: ImportViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // File picker launcher
    val fileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            // Convert Uri to path (simplified — real app needs SAF copy)
            viewModel.onFileSelected(context, it)
        }
    }

    // Observe events
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is ImportViewModel.ImportEvent.Done -> {
                    scope.launch {
                        snackbarHostState.showSnackbar("Imported ${event.total} messages")
                    }
                }
                is ImportViewModel.ImportEvent.Error -> {
                    scope.launch {
                        snackbarHostState.showSnackbar("Error: ${event.message}")
                    }
                }
            }
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Import Messages") }) },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "Select an SMS Backup & Restore XML or JSON file to import.",
                style = MaterialTheme.typography.bodyLarge
            )

            if (uiState.importing) {
                LinearProgressIndicator(
                    progress = uiState.progress / 100f,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Importing... ${uiState.progress}%", style = MaterialTheme.typography.bodyMedium)
            }

            Button(
                onClick = { fileLauncher.launch("*/*") },
                enabled = !uiState.importing
            ) {
                Text("Select File")
            }

            if (uiState.totalImported > 0) {
                Text(
                    "Last import: ${uiState.totalImported} messages",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}
