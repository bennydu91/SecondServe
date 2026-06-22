package com.secondserve.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.secondserve.HomeScreen
import com.secondserve.feature.history.HistoryScreen
import com.secondserve.feature.history.SessionDetailScreen
import com.secondserve.feature.history.StatsScreen
import com.secondserve.feature.match.MatchScreen
import com.secondserve.feature.match.NewMatchScreen
import com.secondserve.feature.profile.ProfileScreen
import com.secondserve.feature.profile.WorkAxesScreen

@Composable
fun AppNavGraph() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = "home") {
        composable("home") {
            HomeScreen(
                onNavigateToNewMatch = { navController.navigate("new_match") },
                onNavigateToProfile = { navController.navigate("profile") },
                onNavigateToHistory = { navController.navigate("history") },
                onNavigateToStats = { navController.navigate("stats") }
            )
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
        composable("profile") {
            ProfileScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToWorkAxes = { navController.navigate("work_axes") }
            )
        }
        composable("work_axes") {
            WorkAxesScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable("history") {
            HistoryScreen(
                onNavigateToDetail = { sessionId -> navController.navigate("session_detail/$sessionId") },
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable(
            route = "session_detail/{sessionId}",
            arguments = listOf(navArgument("sessionId") { type = NavType.LongType })
        ) {
            SessionDetailScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable("stats") {
            StatsScreen(onNavigateBack = { navController.popBackStack() })
        }
    }
}
