package com.zwyft.horizon.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import com.zwyft.horizon.export.ExportViewModel
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportScreen(
    navController: androidx.navigation.NavController,
    viewModel: ExportViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var startDate by remember { mutableStateOf("") }
    var endDate by remember { mutableStateOf("") }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Export as PDF") }) },
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
                "Generate a court-ready PDF of your messages.",
                style = MaterialTheme.typography.bodyLarge
            )

            // Date range (simplified text fields — real app uses DatePicker)
            OutlinedTextField(
                value = startDate,
                onValueChange = { startDate = it },
                label = { Text("Start Date (YYYY-MM-DD)") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = endDate,
                onValueChange = { endDate = it },
                label = { Text("End Date (YYYY-MM-DD)") },
                modifier = Modifier.fillMaxWidth()
            )

            if (uiState.exporting) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text("Generating PDF...", style = MaterialTheme.typography.bodyMedium)
            }

            Button(
                onClick = {
                    // Parse dates (simplified)
                    val start = if (startDate.isNotBlank()) java.text.SimpleDateFormat("yyyy-MM-dd").parse(startDate) else null
                    val end = if (endDate.isNotBlank()) java.text.SimpleDateFormat("yyyy-MM-dd").parse(endDate) else null
                    viewModel.generatePdf(start, end)
                },
                enabled = !uiState.exporting
            ) {
                Text("Generate PDF")
            }

            uiState.lastExportPath?.let { path ->
                Text(
                    "Last export: $path",
                    style = MaterialTheme.typography.bodyMedium
                )
                Button(onClick = {
                    // Share the PDF
                    val file = File(path)
                    val uri: Uri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.provider",
                        file
                    )
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "application/pdf"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(shareIntent, "Share PDF"))
                }) {
                    Text("Share PDF")
                }
            }

            uiState.error?.let { err ->
                Text(
                    "Error: $err",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}
