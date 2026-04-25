package com.coparenting.chronicle.horizon.ui.navigation

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.navigation.*
import androidx.navigation.compose.*
import com.coparenting.chronicle.horizon.ui.screens.*
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private val dtFmt = DateTimeFormatter.ISO_LOCAL_DATE_TIME

object Routes {
    const val HOME = "home"
    const val DAY_DETAIL = "day/{dateStr}"
    const val ENTRY_NEW = "entry/new/{dateStr}"
    const val ENTRY_EDIT = "entry/edit/{entryId}"
    const val AI_ASSISTANT = "assistant"
    const val SETTINGS = "settings"

    fun dayDetail(date: LocalDateTime) = "day/${encode(date.format(dtFmt))}"
    fun entryNew(date: LocalDateTime) = "entry/new/${encode(date.format(dtFmt))}"
    fun entryEdit(id: String) = "entry/edit/${encode(id)}"

    private fun encode(s: String) = URLEncoder.encode(s, StandardCharsets.UTF_8.toString())
}

@Composable
fun AppNavigation() {
    val context = LocalContext.current
    val navController = rememberNavController()

    var hasSmsPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED
        )
    }

    val smsPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasSmsPermission = granted }

    NavHost(navController = navController, startDestination = Routes.HOME) {

        composable(Routes.HOME) {
            HomeScreen(
                onDaySelected = { date -> navController.navigate(Routes.dayDetail(date)) },
                onSettingsClick = { navController.navigate(Routes.SETTINGS) }
            )
        }

        composable(
            route = Routes.DAY_DETAIL,
            arguments = listOf(navArgument("dateStr") { type = NavType.StringType })
        ) { backStack ->
            val raw = backStack.arguments?.getString("dateStr") ?: return@composable
            val date = LocalDateTime.parse(URLDecoder.decode(raw, StandardCharsets.UTF_8.toString()), dtFmt)
            DayDetailScreen(
                date = date,
                hasSmsPermission = hasSmsPermission,
                onBack = { navController.popBackStack() },
                onAddEntry = { d -> navController.navigate(Routes.entryNew(d)) },
                onEditEntry = { id -> navController.navigate(Routes.entryEdit(id)) },
                onAskAI = { navController.navigate(Routes.AI_ASSISTANT) }
            )
        }

        composable(
            route = Routes.ENTRY_NEW,
            arguments = listOf(navArgument("dateStr") { type = NavType.StringType })
        ) { backStack ->
            val raw = backStack.arguments?.getString("dateStr") ?: return@composable
            val date = LocalDateTime.parse(URLDecoder.decode(raw, StandardCharsets.UTF_8.toString()), dtFmt)
            EntryEditorScreen(
                date = date,
                entryId = null,
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Routes.ENTRY_EDIT,
            arguments = listOf(navArgument("entryId") { type = NavType.StringType })
        ) { backStack ->
            val rawId = backStack.arguments?.getString("entryId") ?: return@composable
            val entryId = URLDecoder.decode(rawId, StandardCharsets.UTF_8.toString())
            // Use the entry's date for display; actual date comes from loaded entry
            EntryEditorScreen(
                date = LocalDateTime.now(),
                entryId = entryId,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.AI_ASSISTANT) {
            AiAssistantScreen(
                hasSmsPermission = hasSmsPermission,
                onBack = { navController.popBackStack() },
                onGoToSettings = {
                    navController.navigate(Routes.SETTINGS) {
                        launchSingleTop = true
                    }
                }
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                hasSmsPermission = hasSmsPermission,
                onRequestSmsPermission = { smsPermissionLauncher.launch(Manifest.permission.READ_SMS) },
                onBack = { navController.popBackStack() }
            )
        }
    }
}
