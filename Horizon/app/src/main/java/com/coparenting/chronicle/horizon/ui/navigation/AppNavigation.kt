package com.coparenting.chronicle.horizon.ui.navigation

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.calculateBottomPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
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
    const val HOME         = "home"
    const val DAY_DETAIL   = "day/{dateStr}"
    const val ENTRY_NEW    = "entry/new/{dateStr}"
    const val ENTRY_EDIT   = "entry/edit/{entryId}"
    const val AI_ASSISTANT = "assistant?q={q}"
    const val SETTINGS     = "settings"

    fun dayDetail(date: LocalDateTime) = "day/${encode(date.format(dtFmt))}"
    fun entryNew(date: LocalDateTime)  = "entry/new/${encode(date.format(dtFmt))}"
    fun entryEdit(id: String)          = "entry/edit/${encode(id)}"
    fun aiAssistant()                  = "assistant"
    fun aiAssistantForDay(date: LocalDateTime): String {
        val label = date.format(DateTimeFormatter.ofPattern("MMMM d, yyyy"))
        return "assistant?q=${encode("What happened on $label?")}"
    }

    private fun encode(s: String) = URLEncoder.encode(s, StandardCharsets.UTF_8.toString())
}

private data class BottomNavItem(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
)

private val bottomNavItems = listOf(
    BottomNavItem(Routes.HOME,        "Journal",  Icons.Filled.Home,        Icons.Outlined.Home),
    BottomNavItem(Routes.aiAssistant(), "AI Chat", Icons.Filled.AutoAwesome, Icons.Outlined.AutoAwesome),
    BottomNavItem(Routes.SETTINGS,    "Settings", Icons.Filled.Settings,    Icons.Outlined.Settings),
)

private val topLevelRoutes = setOf(Routes.HOME, Routes.aiAssistant(), Routes.SETTINGS)

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

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val showBottomBar = currentRoute?.substringBefore("?") in topLevelRoutes || currentRoute in topLevelRoutes

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    bottomNavItems.forEach { item ->
                        val selected = currentRoute?.startsWith(item.route.substringBefore("?")) == true ||
                                       currentRoute == item.route
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(item.route) {
                                    popUpTo(Routes.HOME) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(
                                    if (selected) item.selectedIcon else item.unselectedIcon,
                                    contentDescription = item.label
                                )
                            },
                            label = { Text(item.label) }
                        )
                    }
                }
            }
        }
    ) { outerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier.padding(bottom = outerPadding.calculateBottomPadding())
        ) {
            composable(Routes.HOME) {
                HomeScreen(
                    onDaySelected = { date -> navController.navigate(Routes.dayDetail(date)) }
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
                    onAskAI = { navController.navigate(Routes.aiAssistantForDay(date)) }
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
                EntryEditorScreen(
                    date = LocalDateTime.now(),
                    entryId = entryId,
                    onBack = { navController.popBackStack() }
                )
            }

            composable(
                route = Routes.AI_ASSISTANT,
                arguments = listOf(navArgument("q") { type = NavType.StringType; defaultValue = "" })
            ) { backStack ->
                val rawQ = backStack.arguments?.getString("q") ?: ""
                val initialQuestion = if (rawQ.isBlank()) "" else URLDecoder.decode(rawQ, StandardCharsets.UTF_8.toString())
                AiAssistantScreen(
                    hasSmsPermission = hasSmsPermission,
                    initialQuestion = initialQuestion,
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
}
