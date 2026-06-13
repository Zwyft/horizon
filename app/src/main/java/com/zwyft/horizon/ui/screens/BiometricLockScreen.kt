package com.zwyft.horizon.ui.screens

import android.content.Intent
import android.provider.Settings
import android.util.Log
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NoAccounts
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay

private const val TAG = "BiometricLock"

/**
 * States the biometric lock screen can be in.
 */
private enum class BiometricLockState {
    CHECKING,
    READY,
    NOT_ENROLLED,
    NO_HARDWARE,
    ERROR,
    UNLOCKED
}

/**
 * Full-screen lock that gates access to the main app content.
 *
 * Shows the app name, a lock icon, and triggers [BiometricPrompt] with
 * `BIOMETRIC_STRONG or DEVICE_CREDENTIAL` fallback. Handles every
 * [BiometricManager.canAuthenticate] state.
 *
 * @param onAuthenticated Called once authentication succeeds.
 * @param onDisableLock Called when the user opts to disable biometric lock.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BiometricLockScreen(
    onAuthenticated: () -> Unit,
    onDisableLock: () -> Unit
) {
    val context = LocalContext.current
    // MainActivity now extends AppCompatActivity (→ FragmentActivity), so this cast succeeds
    val fragmentActivity = context as? androidx.fragment.app.FragmentActivity

    var lockState by remember { mutableStateOf(BiometricLockState.CHECKING) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val shouldAutoLaunch = remember { mutableStateOf(true) }

    // ── Check biometric availability on mount ──
    LaunchedEffect(Unit) {
        val mgr = BiometricManager.from(context)
        lockState = when (mgr.canAuthenticate(BIOMETRIC_STRONG or DEVICE_CREDENTIAL)) {
            BiometricManager.BIOMETRIC_SUCCESS -> BiometricLockState.READY
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> BiometricLockState.NOT_ENROLLED
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> BiometricLockState.NO_HARDWARE
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> BiometricLockState.ERROR.also {
                errorMessage = "Hardware unavailable. Try again later."
            }
            BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED -> BiometricLockState.ERROR.also {
                errorMessage = "A security update is required. Please update your device."
            }
            else -> BiometricLockState.ERROR.also {
                errorMessage = "Biometric authentication is not available."
            }
        }
    }

    // ── Build BiometricPrompt ──
    val executor = remember { ContextCompat.getMainExecutor(context) }

    val authCallback = remember {
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                Log.i(TAG, "Authentication succeeded")
                lockState = BiometricLockState.UNLOCKED
                onAuthenticated()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                Log.w(TAG, "Authentication error $errorCode: $errString")
                when (errorCode) {
                    BiometricPrompt.ERROR_USER_CANCELED,
                    BiometricPrompt.ERROR_NEGATIVE_BUTTON -> {
                        // User dismissed — stay on lock screen
                    }
                    BiometricPrompt.ERROR_LOCKOUT,
                    BiometricPrompt.ERROR_LOCKOUT_PERMANENT -> {
                        errorMessage = "Too many failed attempts. Try again later."
                        lockState = BiometricLockState.ERROR
                    }
                    else -> {
                        errorMessage = errString.toString()
                    }
                }
            }

            override fun onAuthenticationFailed() {
                Log.w(TAG, "Authentication failed (no match)")
            }
        }
    }

    val promptInfo = remember {
        BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock Horizon")
            .setSubtitle("Authenticate to access your journal and messages")
            .setAllowedAuthenticators(BIOMETRIC_STRONG or DEVICE_CREDENTIAL)
            .build()
    }

    // Create the BiometricPrompt with FragmentActivity (MainActivity is now AppCompatActivity)
    val biometricPrompt = remember(fragmentActivity) {
        fragmentActivity?.let { BiometricPrompt(it, executor, authCallback) }
    }

    // Auto-launch on first READY
    LaunchedEffect(lockState, shouldAutoLaunch.value) {
        if (lockState == BiometricLockState.READY && shouldAutoLaunch.value) {
            shouldAutoLaunch.value = false
            delay(300)
            biometricPrompt?.authenticate(promptInfo)
        }
    }

    // ── Enroll launcher ──
    val enrollLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { _ ->
        val mgr = BiometricManager.from(context)
        lockState = when (mgr.canAuthenticate(BIOMETRIC_STRONG or DEVICE_CREDENTIAL)) {
            BiometricManager.BIOMETRIC_SUCCESS -> {
                shouldAutoLaunch.value = true
                BiometricLockState.READY
            }
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> BiometricLockState.NOT_ENROLLED
            else -> BiometricLockState.ERROR.also {
                errorMessage = "Biometrics still not available after enrollment."
            }
        }
    }

    // ── UI ──
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                Icons.Filled.Lock,
                contentDescription = null,
                modifier = Modifier.size(72.dp),
                tint = MaterialTheme.colorScheme.primary
            )

            Spacer(Modifier.height(16.dp))
            Text("Horizon", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            Text("Your data is protected", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)

            Spacer(Modifier.height(32.dp))

            when (lockState) {
                BiometricLockState.CHECKING -> {
                    CircularProgressIndicator(modifier = Modifier.size(48.dp), strokeWidth = 4.dp)
                    Spacer(Modifier.height(16.dp))
                    Text("Checking device security…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                BiometricLockState.READY -> {
                    Icon(Icons.Filled.Fingerprint, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(16.dp))
                    Text("Tap to unlock", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(24.dp))
                    FilledTonalButton(
                        onClick = { biometricPrompt?.authenticate(promptInfo) },
                        modifier = Modifier.size(64.dp),
                        shape = MaterialTheme.shapes.extraLarge
                    ) {
                        Icon(Icons.Filled.Fingerprint, contentDescription = "Unlock", modifier = Modifier.size(28.dp))
                    }
                    Spacer(Modifier.height(12.dp))
                    Text("Use your fingerprint, face, or device PIN", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                }

                BiometricLockState.NOT_ENROLLED -> {
                    Icon(Icons.Filled.NoAccounts, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(16.dp))
                    Text("No biometrics set up", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    Text("Go to your device settings to set up a fingerprint, face unlock, or screen lock.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(24.dp))
                    Button(onClick = {
                        try {
                            val intent = Intent(Settings.ACTION_BIOMETRIC_ENROLL).apply {
                                putExtra(Settings.EXTRA_BIOMETRIC_AUTHENTICATORS_ALLOWED, BIOMETRIC_STRONG or DEVICE_CREDENTIAL)
                            }
                            enrollLauncher.launch(intent)
                        } catch (_: Exception) {
                            context.startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS))
                        }
                    }) { Text("Set Up Lock Screen") }
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = { onDisableLock() }) { Text("Disable app lock instead") }
                }

                BiometricLockState.NO_HARDWARE -> {
                    Icon(Icons.Filled.Android, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(16.dp))
                    Text("Biometrics not available", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    Text("This device does not support fingerprint, face unlock, or device credentials. You can disable app lock to proceed.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(24.dp))
                    Button(onClick = { onDisableLock() }) { Text("Disable App Lock") }
                }

                BiometricLockState.ERROR -> {
                    Icon(Icons.Filled.Warning, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(16.dp))
                    Text(errorMessage ?: "Something went wrong", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            errorMessage = null
                            biometricPrompt?.authenticate(promptInfo)
                        }) { Text("Try Again") }
                        OutlinedButton(onClick = { onDisableLock() }) { Text("Disable Lock") }
                    }
                }

                BiometricLockState.UNLOCKED -> {
                    CircularProgressIndicator()
                }
            }
        }
    }
}
