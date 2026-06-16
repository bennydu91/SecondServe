package com.secondserve.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.secondserve.feature.profile.ProfileScreen
import com.secondserve.feature.profile.WorkAxesScreen

@Composable
fun AppNavGraph() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = "profile") {
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
