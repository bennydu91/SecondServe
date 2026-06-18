package com.secondserve.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.secondserve.HomeScreen
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
                onNavigateToProfile = { navController.navigate("profile") }
            )
        }
        composable("new_match") {
            NewMatchScreen(
                onSessionStarted = { navController.popBackStack() },
                onNavigateBack = { navController.popBackStack() }
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
    }
}
