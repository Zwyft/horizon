package com.zwyft.horizon.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.zwyft.horizon.ai.JournalViewModel
import com.zwyft.horizon.contacts.ContactViewModel
import com.zwyft.horizon.data.HorizonDatabase
import com.zwyft.horizon.data.entity.ContactEntity

enum class DashboardTab(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    MESSAGES("Messages", Icons.Filled.Chat, Icons.Outlined.Chat),
    JOURNAL("Journal", Icons.Filled.AutoStories, Icons.Outlined.AutoStories),
    AI_CHAT("AI Chat", Icons.Filled.Psychology, Icons.Outlined.Psychology),
    CONTACTS("Contacts", Icons.Filled.Contacts, Icons.Outlined.Contacts),
    SETTINGS("Settings", Icons.Filled.Settings, Icons.Outlined.Settings);

    companion object { val entriesList = entries }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(navController: NavController) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var journalGenerateDialog by remember { mutableStateOf(false) }
    var contactsAddDialog by remember { mutableStateOf(false) }
    var contactsEditTarget by remember { mutableStateOf<ContactEntity?>(null) }

    val journalViewModel: JournalViewModel = hiltViewModel()
    val contactsViewModel: ContactViewModel = hiltViewModel()

    val currentTab = DashboardTab.entriesList.getOrElse(selectedTab) { DashboardTab.MESSAGES }

    // Badge counts (message count for demo)
    val context = androidx.compose.ui.platform.LocalContext.current
    val db = remember { HorizonDatabase.getInstance(context) }
    val messageCount by db.messageDao().observeMonitored().collectAsState(initial = emptyList())

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.WbTwilight,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text("Horizon", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                            Text(
                                currentTab.label,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        floatingActionButton = {
            AnimatedVisibility(
                visible = currentTab == DashboardTab.JOURNAL || currentTab == DashboardTab.CONTACTS,
                enter = scaleIn(animationSpec = tween(200)) + fadeIn(tween(200)),
                exit = scaleOut(animationSpec = tween(150)) + fadeOut(tween(150))
            ) {
                FloatingActionButton(
                    onClick = {
                        when (currentTab) {
                            DashboardTab.JOURNAL -> journalGenerateDialog = true
                            DashboardTab.CONTACTS -> contactsAddDialog = true
                            else -> {}
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ) {
                    Icon(
                        if (currentTab == DashboardTab.CONTACTS) Icons.Filled.PersonAdd
                        else Icons.Filled.Add,
                        contentDescription = "Create"
                    )
                }
            }
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp),
                tonalElevation = 6.dp
            ) {
                DashboardTab.entriesList.forEachIndexed { index, tab ->
                    val selected = selectedTab == index
                    val badge = when (tab) {
                        DashboardTab.MESSAGES -> messageCount.size
                        else -> 0
                    }

                    NavigationBarItem(
                        selected = selected,
                        onClick = { selectedTab = index },
                        icon = {
                            BadgedBox(
                                badge = {
                                    if (badge > 0 && !selected) {
                                        Badge(
                                            containerColor = MaterialTheme.colorScheme.error,
                                            contentColor = MaterialTheme.colorScheme.onError
                                        ) {
                                            Text(
                                                if (badge > 99) "99+" else "$badge",
                                                fontSize = 10.sp
                                            )
                                        }
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                    contentDescription = tab.label
                                )
                            }
                        },
                        label = {
                            Text(
                                tab.label,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                        )
                    )
                }
            }
        }
    ) { padding ->
        Crossfade(
            targetState = selectedTab,
            modifier = Modifier.padding(padding),
            animationSpec = tween(durationMillis = 250),
            label = "tab-crossfade"
        ) { tab ->
            when (DashboardTab.entriesList[tab]) {
                DashboardTab.MESSAGES -> MessagesContent()
                DashboardTab.JOURNAL -> JournalContent(
                    viewModel = journalViewModel,
                    showGenerateDialog = journalGenerateDialog,
                    onShowGenerateDialog = { journalGenerateDialog = it },
                    navController = navController
                )
                DashboardTab.AI_CHAT -> AIChatContent()
                DashboardTab.CONTACTS -> ContactsContent(
                    viewModel = contactsViewModel,
                    showAddDialog = contactsAddDialog,
                    onShowAddDialog = { contactsAddDialog = it },
                    editingContact = contactsEditTarget,
                    onEditingContact = { contactsEditTarget = it }
                )
                DashboardTab.SETTINGS -> SettingsContent()
            }
        }
    }
}
