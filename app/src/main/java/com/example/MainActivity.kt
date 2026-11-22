package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MeetingRoom
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.outlined.MeetingRoom
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.SubstitutionsScreen
import com.example.ui.theme.WolneSaleTheme
import com.example.viewmodel.MainViewModel

const val ROUTE_HOME = "home"
const val ROUTE_SUBSTITUTIONS = "substitutions"
const val ROUTE_SETTINGS = "settings"

private data class NavDestination(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

private val topDestinations = listOf(
    NavDestination(
        route = ROUTE_HOME,
        label = "Wolne sale",
        selectedIcon = Icons.Filled.MeetingRoom,
        unselectedIcon = Icons.Outlined.MeetingRoom,
        testTag = "nav_home"
    ),
    NavDestination(
        route = ROUTE_SUBSTITUTIONS,
        label = "Zastępstwa",
        selectedIcon = Icons.Filled.SwapHoriz,
        unselectedIcon = Icons.Outlined.SwapHoriz,
        testTag = "nav_substitutions"
    ),
    NavDestination(
        route = ROUTE_SETTINGS,
        label = "Ustawienia",
        selectedIcon = Icons.Filled.Settings,
        unselectedIcon = Icons.Outlined.Settings,
        testTag = "nav_settings"
    )
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            WolneSaleTheme {
                WolneSaleApp()
            }
        }
    }
}

@Composable
fun WolneSaleApp(viewModel: MainViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: ROUTE_HOME

    val navigateTo: (String) -> Unit = { route ->
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 600.dp

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = if (useRail) {
                WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.End + WindowInsetsSides.Bottom)
            } else {
                WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
            },
            bottomBar = {
                if (!useRail) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        topDestinations.forEach { dest ->
                            val selected = currentRoute == dest.route
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateTo(dest.route) },
                                icon = {
                                    NavItemIcon(
                                        dest = dest,
                                        selected = selected,
                                        substitutionsCount = state.substitutions.size
                                    )
                                },
                                label = { Text(dest.label) },
                                modifier = Modifier.testTag(dest.testTag)
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useRail) {
                    NavigationRail(
                        modifier = Modifier.windowInsetsPadding(
                            WindowInsets.safeDrawing.only(WindowInsetsSides.Start)
                        ),
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        topDestinations.forEach { dest ->
                            val selected = currentRoute == dest.route
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateTo(dest.route) },
                                icon = {
                                    NavItemIcon(
                                        dest = dest,
                                        selected = selected,
                                        substitutionsCount = state.substitutions.size
                                    )
                                },
                                label = { Text(dest.label) },
                                modifier = Modifier.testTag(dest.testTag)
                            )
                        }
                    }
                }

                NavHost(
                    navController = navController,
                    startDestination = ROUTE_HOME,
                    modifier = Modifier.weight(1f)
                ) {
                    composable(ROUTE_HOME) {
                        HomeScreen(
                            state = state,
                            onRefresh = { viewModel.refreshData(isManual = true) },
                            onSearchQueryChange = viewModel::updateSearchQuery,
                            onFloorLevelSelect = viewModel::selectFloorLevel,
                            onToggleOnlyWithSubstitutions = viewModel::toggleOnlyWithSubstitutions,
                            onToggleHideCleanedRooms = viewModel::toggleHideCleanedRooms,
                            onToggleRoomCleaned = viewModel::toggleRoomCleaned,
                            onOpenRoomDetails = viewModel::openRoomDetails,
                            onNavigateToSettings = { navigateTo(ROUTE_SETTINGS) },
                            onDownloadUpdate = viewModel::downloadUpdate,
                            onCancelUpdateDownload = viewModel::cancelUpdateDownload,
                            onUpdateInstallError = viewModel::reportUpdateError,
                            onDismissUpdateBanner = viewModel::dismissUpdateBanner
                        )
                    }

                    composable(ROUTE_SUBSTITUTIONS) {
                        SubstitutionsScreen(
                            state = state,
                            onRefresh = { viewModel.refreshData(isManual = true) }
                        )
                    }

                    composable(ROUTE_SETTINGS) {
                        SettingsScreen(
                            state = state,
                            onSaveSettings = { timetableUrl, substitutionsUrl, refreshMins ->
                                viewModel.saveSettings(timetableUrl, substitutionsUrl, refreshMins)
                            },
                            onResetDefaultUrls = viewModel::resetDefaultUrls,
                            onClearCleanedRooms = viewModel::clearCleanedRooms,
                            onClearCache = viewModel::clearCache,
                            onAddFloorLevel = viewModel::addFloorLevel,
                            onRenameFloorLevel = viewModel::renameFloorLevel,
                            onDeleteFloorLevel = viewModel::deleteFloorLevel,
                            onAddRoomRuleToLevel = viewModel::addRuleToFloorLevel,
                            onRemoveRoomRuleFromLevel = viewModel::removeRuleFromFloorLevel,
                            onResetDefaultFloorLevels = viewModel::resetDefaultFloorLevels,
                            onSaveGithubSettings = viewModel::saveGithubSettings,
                            onCheckForUpdates = { repoOverride ->
                                viewModel.checkForUpdates(isManual = true, repoOverride = repoOverride)
                            },
                            onDownloadUpdate = viewModel::downloadUpdate,
                            onCancelUpdateDownload = viewModel::cancelUpdateDownload,
                            onUpdateInstallError = viewModel::reportUpdateError
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NavItemIcon(
    dest: NavDestination,
    selected: Boolean,
    substitutionsCount: Int
) {
    val icon = if (selected) dest.selectedIcon else dest.unselectedIcon
    if (dest.route == ROUTE_SUBSTITUTIONS && substitutionsCount > 0) {
        BadgedBox(
            badge = {
                Badge { Text(substitutionsCount.toString()) }
            }
        ) {
            Icon(imageVector = icon, contentDescription = dest.label)
        }
    } else {
        Icon(imageVector = icon, contentDescription = dest.label)
    }
}
