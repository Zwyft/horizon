package com.zwyft.horizon.ui.screens

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.zwyft.horizon.R
import com.zwyft.horizon.importing.ImportViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

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

    // SAF file picker — handles content:// URIs correctly
    val fileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            scope.launch {
                try {
                    viewModel.onFileSelected(context, it, "import_tmp")
                } catch (e: Exception) {
                    snackbarHostState.showSnackbar("Failed to read file: ${e.message}")
                }
            }
        }
    }

    // Observe events
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is com.zwyft.horizon.importing.ImportEvent.Done -> {
                    snackbarHostState.showSnackbar("Imported ${event.total} messages")
                }
                is com.zwyft.horizon.importing.ImportEvent.Error -> {
                    snackbarHostState.showSnackbar("Error: ${event.message}")
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
                onClick = { fileLauncher.launch(arrayOf("*/*")) },
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

