package com.coparenting.chronicle.horizon.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.coparenting.chronicle.horizon.data.remote.openrouter.OpenRouterModel
import com.coparenting.chronicle.horizon.presentation.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    hasSmsPermission: Boolean,
    onRequestSmsPermission: () -> Unit,
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    var showClaudeKey by remember { mutableStateOf(false) }
    var showOrKey by remember { mutableStateOf(false) }
    var modelDropdownExpanded by remember { mutableStateOf(false) }

    var nameInput by remember(state.coParentName) { mutableStateOf(state.coParentName) }
    var phoneInput by remember(state.coParentPhone) { mutableStateOf(state.coParentPhone) }
    var claudeKeyInput by remember(state.claudeApiKey) { mutableStateOf(state.claudeApiKey) }
    var orKeyInput by remember(state.openRouterApiKey) { mutableStateOf(state.openRouterApiKey) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {

            // ── CO-PARENT ──────────────────────────────────────────────────
            SectionHeader(icon = Icons.Default.Person, title = "Co-Parent")
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = { nameInput = it; viewModel.setCoParentName(it) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Name") },
                        placeholder = { Text("Co-parent's name") },
                        leadingIcon = { Icon(Icons.Default.Person, null) },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp)
                    )
                    OutlinedTextField(
                        value = phoneInput,
                        onValueChange = { phoneInput = it; viewModel.setCoParentPhone(it) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Phone Number") },
                        placeholder = { Text("+1 555 000 0000") },
                        leadingIcon = { Icon(Icons.Default.Phone, null) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp)
                    )
                    Text(
                        "SMS messages from this number will appear in the daily timeline.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // ── AI PROVIDER TOGGLE ─────────────────────────────────────────
            SectionHeader(icon = Icons.Default.AutoAwesome, title = "AI Provider")
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Choose which AI service generates diary entries and answers questions.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ProviderChip(
                            label = "Claude (Anthropic)",
                            selected = state.aiProvider == "claude",
                            onClick = { viewModel.setAiProvider("claude") }
                        )
                        ProviderChip(
                            label = "OpenRouter (Free)",
                            selected = state.aiProvider == "openrouter",
                            onClick = { viewModel.setAiProvider("openrouter") }
                        )
                    }
                }
            }

            // ── CLAUDE API KEY ─────────────────────────────────────────────
            SectionHeader(icon = Icons.Default.VpnKey, title = "Claude API Key")
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = claudeKeyInput,
                        onValueChange = { claudeKeyInput = it; viewModel.setClaudeApiKey(it) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Claude API Key") },
                        placeholder = { Text("sk-ant-…") },
                        leadingIcon = { Icon(Icons.Default.VpnKey, null) },
                        trailingIcon = {
                            IconButton(onClick = { showClaudeKey = !showClaudeKey }) {
                                Icon(if (showClaudeKey) Icons.Default.VisibilityOff else Icons.Default.Visibility, null)
                            }
                        },
                        visualTransformation = if (showClaudeKey) VisualTransformation.None else PasswordVisualTransformation(),
                        singleLine = true,
                        enabled = state.aiProvider == "claude",
                        shape = RoundedCornerShape(14.dp)
                    )
                    Text(
                        "Stored locally on this device only. Get a key at console.anthropic.com.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (claudeKeyInput.isNotBlank()) ConfiguredBadge()
                }
            }

            // ── OPENROUTER API KEY + MODEL ─────────────────────────────────
            SectionHeader(icon = Icons.Default.Cloud, title = "OpenRouter (Free Models)")
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = orKeyInput,
                        onValueChange = { orKeyInput = it; viewModel.setOpenRouterApiKey(it) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("OpenRouter API Key") },
                        placeholder = { Text("sk-or-…") },
                        leadingIcon = { Icon(Icons.Default.VpnKey, null) },
                        trailingIcon = {
                            IconButton(onClick = { showOrKey = !showOrKey }) {
                                Icon(if (showOrKey) Icons.Default.VisibilityOff else Icons.Default.Visibility, null)
                            }
                        },
                        visualTransformation = if (showOrKey) VisualTransformation.None else PasswordVisualTransformation(),
                        singleLine = true,
                        enabled = state.aiProvider == "openrouter",
                        shape = RoundedCornerShape(14.dp)
                    )
                    Text(
                        "Free to use. Get a key at openrouter.ai — no payment required for free models.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (orKeyInput.isNotBlank()) ConfiguredBadge()

                    HorizontalDivider()

                    Text("Model", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)

                    val selectedModel = state.availableModels
                        .find { it.id == state.selectedOpenRouterModel }
                        ?: state.availableModels.first()

                    ExposedDropdownMenuBox(
                        expanded = modelDropdownExpanded,
                        onExpandedChange = {
                            if (state.aiProvider == "openrouter") modelDropdownExpanded = it
                        }
                    ) {
                        OutlinedTextField(
                            value = selectedModel.displayName,
                            onValueChange = {},
                            readOnly = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(),
                            label = { Text("Selected model") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = modelDropdownExpanded) },
                            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                            enabled = state.aiProvider == "openrouter"
                        )
                        ExposedDropdownMenu(
                            expanded = modelDropdownExpanded,
                            onDismissRequest = { modelDropdownExpanded = false }
                        ) {
                            state.availableModels.forEach { model ->
                                ModelDropdownItem(
                                    model = model,
                                    selected = model.id == state.selectedOpenRouterModel,
                                    onClick = {
                                        viewModel.setSelectedOpenRouterModel(model.id)
                                        modelDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    Text(
                        "All models listed are free via OpenRouter. Llama 3.3 70B and Qwen 2.5 72B " +
                            "give the best diary writing quality.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // ── SMS ────────────────────────────────────────────────────────
            SectionHeader(icon = Icons.Default.Sms, title = "Text Messages")
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Show SMS in Timeline", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            Text(
                                "Display text messages alongside journal entries",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(checked = state.showSmsInTimeline, onCheckedChange = viewModel::setShowSms)
                    }

                    HorizontalDivider()

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("SMS Permission", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            Text(
                                if (hasSmsPermission) "Granted — messages are visible" else "Not granted — tap to request",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (hasSmsPermission) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                            )
                        }
                        if (!hasSmsPermission) {
                            Button(onClick = onRequestSmsPermission) { Text("Grant") }
                        } else {
                            Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }

            // ── ABOUT ──────────────────────────────────────────────────────
            SectionHeader(icon = Icons.Default.Info, title = "About")
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SettingsRow("App", "Horizon — Co-Parenting Journal")
                    SettingsRow("Version", "1.0.0")
                    Text(
                        "All data is stored locally on your device. AI queries are sent to the provider " +
                            "you select (Anthropic or OpenRouter) using your own API key.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun ProviderChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, style = MaterialTheme.typography.bodySmall) },
        leadingIcon = if (selected) {
            { Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp)) }
        } else null
    )
}

@Composable
private fun ModelDropdownItem(model: OpenRouterModel, selected: Boolean, onClick: () -> Unit) {
    DropdownMenuItem(
        text = {
            Column {
                Text(
                    model.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                )
                Text(
                    model.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        onClick = onClick,
        trailingIcon = if (selected) {
            { Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp)) }
        } else null
    )
}

@Composable
private fun ConfiguredBadge() {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
        Text("API key configured", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun SectionHeader(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Text(
            title.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun SettingsRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}
