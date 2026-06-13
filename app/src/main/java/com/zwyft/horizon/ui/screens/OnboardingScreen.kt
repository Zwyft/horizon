package com.zwyft.horizon.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.zwyft.horizon.ai.AiProvider
import com.zwyft.horizon.data.HorizonDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

/**
 * Modern multi-step onboarding wizard with animated page transitions.
 *
 * Steps:
 *  0 → Welcome
 *  1 → Permissions
 *  2 → API Key
 *  3 → Contacts
 *  4 → Done
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(
    @Suppress("UNUSED_PARAMETER") navController: NavController,
    onComplete: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var currentStep by remember { mutableIntStateOf(0) }
    val totalSteps = 4
    val scrollState = rememberScrollState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Setup", fontWeight = FontWeight.SemiBold)
                    }
                },
                actions = {
                    if (currentStep < totalSteps - 1) {
                        TextButton(onClick = onComplete) {
                            Text("Skip", fontWeight = FontWeight.Medium)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            )
        },
        bottomBar = {
            ModernBottomBar(
                currentStep = currentStep,
                totalSteps = totalSteps,
                onBack = { currentStep-- },
                onNext = {
                    if (currentStep < totalSteps - 1) {
                        currentStep++
                    } else {
                        onComplete()
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            AnimatedContent(
                targetState = currentStep,
                transitionSpec = {
                    val direction = if (targetState > initialState) 1 else -1
                    slideInHorizontally(
                        animationSpec = tween(300),
                        initialOffsetX = { fullWidth -> direction * fullWidth / 4 }
                    ) + fadeIn(tween(250)) togetherWith
                        slideOutHorizontally(
                            animationSpec = tween(300),
                            targetOffsetX = { fullWidth -> -direction * fullWidth / 4 }
                        ) + fadeOut(tween(200))
                },
                label = "step-transition",
                modifier = Modifier.fillMaxSize()
            ) { step ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    when (step) {
                        0 -> WelcomeStep()
                        1 -> PermissionsStep(context = context)
                        2 -> ApiKeyStep(context = context)
                        3 -> ContactsStep()
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  MODERN BOTTOM BAR
// ─────────────────────────────────────────────────────────────

@Composable
private fun ModernBottomBar(
    currentStep: Int,
    totalSteps: Int,
    onBack: () -> Unit,
    onNext: () -> Unit
) {
    Surface(
        tonalElevation = 3.dp,
        shadowElevation = 8.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Animated step indicator
            StepIndicator(currentStep = currentStep, totalSteps = totalSteps)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (currentStep > 0) {
                    OutlinedButton(
                        onClick = onBack,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Back")
                    }
                } else {
                    Spacer(Modifier.width(1.dp))
                }

                Button(
                    onClick = onNext,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(if (currentStep < totalSteps - 1) "Next" else "Start")
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        if (currentStep < totalSteps - 1) Icons.AutoMirrored.Filled.ArrowForward else Icons.Filled.Check,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

/**
 * Animated step indicator with connected dots.
 */
@Composable
private fun StepIndicator(currentStep: Int, totalSteps: Int) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        repeat(totalSteps) { step ->
            val isActive = step == currentStep
            val isDone = step < currentStep

            AnimatedContent(
                targetState = isActive,
                transitionSpec = {
                    fadeIn(tween(200)) togetherWith fadeOut(tween(200))
                },
                label = "dot-anim"
            ) { active ->
                if (active) {
                    Surface(
                        modifier = Modifier.width(28.dp).height(8.dp),
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.primary,
                        shadowElevation = 2.dp
                    ) {}
                } else {
                    Surface(
                        modifier = Modifier.size(8.dp),
                        shape = CircleShape,
                        color = if (isDone) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant
                    ) {}
                }
            }

            if (step < totalSteps - 1) {
                Surface(
                    modifier = Modifier
                        .width(16.dp)
                        .height(2.dp),
                    color = if (step < currentStep) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                    else MaterialTheme.colorScheme.surfaceVariant
                ) {}
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  STEP 0 — WELCOME
// ─────────────────────────────────────────────────────────────

@Composable
private fun WelcomeStep() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(16.dp))

        // Hero icon
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(96.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Filled.AutoAwesome,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        Spacer(Modifier.height(20.dp))
        Text(
            "Welcome to Horizon",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Your autonomous co-parenting diary agent.",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))

        // Feature cards
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(
                modifier = Modifier.padding(4.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                OnboardingFeatureRow(
                    icon = Icons.Filled.AutoStories,
                    title = "AI Journaling",
                    desc = "Automatically generates journal entries from your co-parenting messages"
                )
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                OnboardingFeatureRow(
                    icon = Icons.Filled.Search,
                    title = "Smart Search",
                    desc = "Ask questions about past conversations with natural language"
                )
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                OnboardingFeatureRow(
                    icon = Icons.Filled.Shield,
                    title = "Privacy First",
                    desc = "All data stays on your device, encrypted and secure"
                )
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                OnboardingFeatureRow(
                    icon = Icons.Filled.CloudUpload,
                    title = "Cloud Backup",
                    desc = "Optional Google Drive backup for peace of mind"
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        Text(
            "Let's set up a few things to get started.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun OnboardingFeatureRow(icon: ImageVector, title: String, desc: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 14.dp),
        verticalAlignment = Alignment.Top
    ) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
            modifier = Modifier.size(40.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(2.dp))
            Text(
                desc,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  STEP 1 — PERMISSIONS
// ─────────────────────────────────────────────────────────────

@Composable
private fun PermissionsStep(context: Context) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(16.dp))
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(72.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Filled.Lock,
                    contentDescription = null,
                    modifier = Modifier.size(36.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(
            "Grant Permissions",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Horizon needs a few permissions to capture messages and provide journaling.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(
                modifier = Modifier.padding(4.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                PermissionItem(
                    icon = Icons.Filled.Notifications,
                    title = "Notification Listener",
                    desc = "Captures incoming SMS/RCS notifications",
                    action = "Enable",
                    onClick = {
                        context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                    }
                )
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                PermissionItem(
                    icon = Icons.Filled.Accessibility,
                    title = "Accessibility Service",
                    desc = "Captures RCS messages from Google Messages",
                    action = "Enable",
                    onClick = {
                        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    }
                )
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                PermissionItem(
                    icon = Icons.Filled.Sms,
                    title = "SMS Permission",
                    desc = "Needed for importing SMS history",
                    action = "App Info",
                    onClick = {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                        intent.data = Uri.parse("package:${context.packageName}")
                        context.startActivity(intent)
                    }
                )
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                PermissionItem(
                    icon = Icons.Filled.Contacts,
                    title = "Contacts",
                    desc = "Match phone numbers to contact names",
                    action = "App Info",
                    onClick = {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                        intent.data = Uri.parse("package:${context.packageName}")
                        context.startActivity(intent)
                    }
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        Text(
            "You can always adjust these later in Settings.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun PermissionItem(
    icon: ImageVector,
    title: String,
    desc: String,
    action: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.size(40.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
            Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        OutlinedButton(
            onClick = onClick,
            shape = RoundedCornerShape(8.dp)
        ) {
            Text(action, fontSize = 12.sp)
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  STEP 2 — API KEY
// ─────────────────────────────────────────────────────────────

@Composable
private fun ApiKeyStep(context: Context) {
    var apiKeyInput by remember { mutableStateOf("") }
    var openrouterKeyInput by remember { mutableStateOf("") }
    var selectedProvider by remember {
        val prefs = context.getSharedPreferences("horizon_settings", Context.MODE_PRIVATE)
        val nousKey = prefs.getString("nous_api_key", null)
        val orKey = prefs.getString("openrouter_api_key", null)
        mutableStateOf(
            when {
                !orKey.isNullOrBlank() -> AiProvider.OPENROUTER
                !nousKey.isNullOrBlank() -> AiProvider.NOUS
                else -> AiProvider.NOUS
            }
        )
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(16.dp))
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(72.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Filled.Key,
                    contentDescription = null,
                    modifier = Modifier.size(36.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(
            "AI API Key",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Horizon uses AI to generate journal entries. Choose your provider and enter your API key.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(16.dp))

        // Provider selector
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AiProvider.entries.forEach { provider ->
                FilterChip(
                    selected = selectedProvider == provider,
                    onClick = { selectedProvider = provider },
                    label = { Text(provider.displayName, fontSize = 13.sp) },
                    leadingIcon = {
                        Icon(
                            when (provider) {
                                AiProvider.NOUS -> Icons.Filled.Rocket
                                AiProvider.OPENROUTER -> Icons.Filled.Hub
                                AiProvider.LOCAL -> Icons.Filled.PhoneAndroid
                            },
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        val isOpenRouter = selectedProvider == AiProvider.OPENROUTER
        val currentKeyInput = if (isOpenRouter) openrouterKeyInput else apiKeyInput
        val onKeyChange: (String) -> Unit =
            if (isOpenRouter) {{ openrouterKeyInput = it }} else {{ apiKeyInput = it }}

        OutlinedTextField(
            value = currentKeyInput,
            onValueChange = onKeyChange,
            label = { Text("${selectedProvider.displayName} API Key") },
            leadingIcon = { Icon(Icons.Filled.VpnKey, contentDescription = null, modifier = Modifier.size(18.dp)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text("sk-or-v1-...") },
            shape = RoundedCornerShape(12.dp)
        )

        Spacer(Modifier.height(12.dp))

        Button(
            onClick = {
                val trimmed = currentKeyInput.trim()
                if (trimmed.isNotBlank()) {
                    val prefs = context.getSharedPreferences("horizon_settings", Context.MODE_PRIVATE)
                    runBlocking(Dispatchers.IO) {
                        try {
                            val dao = HorizonDatabase.getInstance(context).settingDao()
                            if (isOpenRouter) {
                                dao.setValue("openrouter_api_key", trimmed)
                                prefs.edit().putString("openrouter_api_key", trimmed).apply()
                            } else {
                                dao.setValue("nous_api_key", trimmed)
                                prefs.edit().putString("nous_api_key", trimmed).apply()
                            }
                            dao.setValue("ai_provider", selectedProvider.name)
                            prefs.edit().putString("ai_provider", selectedProvider.name).apply()
                        } catch (_: Exception) {}
                    }
                }
            },
            enabled = currentKeyInput.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("Save ${selectedProvider.displayName} Key")
        }

        Spacer(Modifier.height(12.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
            )
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    Icons.Filled.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    if (isOpenRouter)
                        "Get a free key at openrouter.ai/keys. Free models are available with rate limits."
                    else
                        "You can get an API key from openrouter.ai (recommended) or directly from NousResearch. The key is stored only on your device.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        Text(
            "You can skip this and set it later in Settings.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

// ─────────────────────────────────────────────────────────────
//  STEP 3 — CONTACTS
// ─────────────────────────────────────────────────────────────

@Composable
private fun ContactsStep() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(16.dp))
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(72.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Filled.People,
                    contentDescription = null,
                    modifier = Modifier.size(36.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(
            "Co-Parent Contacts",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Add the people you co-parent with. Horizon will monitor messages from these contacts.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "Default contacts added:",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(12.dp))
                ContactLabel("Mom", Icons.Filled.Female)
                ContactLabel("Dad", Icons.Filled.Male)
                ContactLabel("Grandma", Icons.Filled.Woman)
                ContactLabel("Grandpa", Icons.Filled.Man)
                ContactLabel("Amanda (babysitter)", Icons.Filled.Person)
            }
        }

        Spacer(Modifier.height(16.dp))
        Text(
            "You can add phone numbers and customize contacts from the Contacts tab after setup.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun ContactLabel(name: String, icon: ImageVector) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
            modifier = Modifier.size(28.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Text("•  $name", style = MaterialTheme.typography.bodyMedium)
    }
}
