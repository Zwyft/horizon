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
import kotlinx.coroutines.launch
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
                    val tempFile = copyUriToTempFile(context, it, "import_tmp.xml")
                    viewModel.onFileSelected(context, tempFile)
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
                is ImportViewModel.ImportEvent.Done -> {
                    snackbarHostState.showSnackbar("Imported ${event.total} messages")
                }
                is ImportViewModel.ImportEvent.Error -> {
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

/**
 * Copy a content:// URI to a temp file (required for SAF URIs).
 */
private suspend fun copyUriToTempFile(context: Context, uri: Uri, fileName: String): File =
    withContext(Dispatchers.IO) {
        val tempFile = File(context.cacheDir, fileName)
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(tempFile).use { output ->
                input.copyTo(output)
            }
        }
        tempFile
    }
