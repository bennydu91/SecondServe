package com.secondserve.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.secondserve.HomeScreen
import com.secondserve.feature.coaching.CoachingScreen
import com.secondserve.feature.history.AddRetroSessionScreen
import com.secondserve.feature.history.HistoryScreen
import com.secondserve.feature.history.SessionDetailScreen
import com.secondserve.feature.history.StatsScreen
import com.secondserve.feature.match.MatchScreen
import com.secondserve.feature.match.NewMatchScreen
import com.secondserve.feature.profile.ProfileScreen
import com.secondserve.feature.profile.SettingsScreen
import com.secondserve.feature.profile.WorkAxesScreen

private enum class BottomNavItem(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    HOME("home", "Accueil", Icons.Filled.Home, Icons.Outlined.Home),
    HISTORY("history", "Historique", Icons.Filled.History, Icons.Outlined.History),
    COACHING("coaching", "Coaching", Icons.Filled.AutoAwesome, Icons.Outlined.AutoAwesome),
    PROFILE("profile", "Profil", Icons.Filled.Person, Icons.Outlined.Person)
}

private val bottomNavRoutes = BottomNavItem.entries.map { it.route }.toSet()

// Routes plein écran sans bottom nav
private val fullScreenRoutes = setOf(
    "new_match", "settings", "work_axes", "add_retro_session", "stats"
)

@Composable
fun AppNavGraph() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val showBottomBar = currentRoute != null
        && !fullScreenRoutes.contains(currentRoute)
        && !currentRoute.startsWith("match/")
        && !currentRoute.startsWith("session_detail/")

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    BottomNavItem.entries.forEach { item ->
                        val selected = currentRoute == item.route
                        NavigationBarItem(
                            icon = {
                                Icon(
                                    imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                    contentDescription = item.label
                                )
                            },
                            label = { Text(item.label) },
                            selected = selected,
                            onClick = {
                                navController.navigate(item.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        NavHost(
            navController = navController,
            startDestination = "home",
            modifier = Modifier.padding(paddingValues)
        ) {
            composable("home") {
                HomeScreen(
                    onNavigateToNewMatch = { navController.navigate("new_match") },
                    onNavigateToProfile = { navController.navigate("profile") },
                    onNavigateToHistory = { navController.navigate("history") },
                    onNavigateToStats = { navController.navigate("stats") },
                    onNavigateToCoaching = { navController.navigate("coaching") }
                )
            }
            composable("coaching") {
                CoachingScreen(onNavigateBack = { navController.popBackStack() })
            }
            composable("new_match") {
                NewMatchScreen(
                    onSessionStarted = { sessionId ->
                        navController.navigate("match/$sessionId") {
                            popUpTo("home")
                        }
                    },
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            composable(
                route = "match/{sessionId}",
                arguments = listOf(navArgument("sessionId") { type = NavType.LongType })
            ) {
                MatchScreen(
                    onSessionClosed = {
                        navController.navigate("home") {
                            popUpTo("home") { inclusive = true }
                        }
                    }
                )
            }
            composable("stats") {
                StatsScreen(onNavigateBack = { navController.popBackStack() })
            }
            composable("history") {
                HistoryScreen(
                    onNavigateToDetail = { sessionId ->
                        navController.navigate("session_detail/$sessionId")
                    },
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToAddRetroSession = { navController.navigate("add_retro_session") },
                    onNavigateToStats = { navController.navigate("stats") }
                )
            }
            composable("add_retro_session") {
                AddRetroSessionScreen(onNavigateBack = { navController.popBackStack() })
            }
            composable(
                route = "session_detail/{sessionId}",
                arguments = listOf(navArgument("sessionId") { type = NavType.LongType })
            ) {
                SessionDetailScreen(onNavigateBack = { navController.popBackStack() })
            }
            composable("profile") {
                ProfileScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToWorkAxes = { navController.navigate("work_axes") },
                    onNavigateToSettings = { navController.navigate("settings") }
                )
            }
            composable("settings") {
                SettingsScreen(onNavigateBack = { navController.popBackStack() })
            }
            composable("work_axes") {
                WorkAxesScreen(onNavigateBack = { navController.popBackStack() })
            }
        }
    }
}
