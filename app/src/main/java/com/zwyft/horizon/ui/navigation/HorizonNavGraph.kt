package com.zwyft.horizon.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.zwyft.horizon.ui.screens.*

/**
 * All navigation routes for the app.
 */
object Routes {
    const val DASHBOARD = "dashboard"
    const val MESSAGES  = "messages"
    const val JOURNAL    = "journal"
    const val JOURNAL_DETAIL = "journal/{entryId}"
    const val CONTACTS   = "contacts"
    const val IMPORT     = "import"
    const val SETTINGS   = "settings"
    const val AI_CHAT    = "ai_chat"
    const val EXPORT     = "export"

    fun journalDetail(entryId: Long) = "journal/$entryId"
}

/**
 * Root nav graph. Single NavHost, all screens registered.
 */
@Composable
fun HorizonNavGraph(startDestination: String = Routes.DASHBOARD) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(Routes.DASHBOARD) {
            DashboardScreen(navController = navController)
        }
        composable(Routes.MESSAGES) {
            MessagesScreen(navController = navController)
        }
        composable(Routes.JOURNAL) {
            JournalScreen(navController = navController)
        }
        composable(
            Routes.JOURNAL_DETAIL,
            arguments = listOf(navArgument("entryId") { type = NavType.LongType })
        ) { backStackEntry ->
            val entryId = backStackEntry.arguments?.getLong("entryId") ?: 0L
            JournalDetailScreen(navController = navController, entryId = entryId)
        }
        composable(Routes.CONTACTS) {
            ContactsScreen(navController = navController)
        }
        composable(Routes.IMPORT) {
            ImportScreen(navController = navController)
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(navController = navController)
        }
        composable(Routes.AI_CHAT) {
            AIChatScreen(navController = navController)
        }
        composable(Routes.EXPORT) {
            ExportScreen(navController = navController)
        }
    }
}
