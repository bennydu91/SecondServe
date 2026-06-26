package com.secondserve.wear.navigation

import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import com.secondserve.wear.presentation.match.ScoreScreen
import com.secondserve.wear.presentation.start.StartMatchScreen

@Composable
fun WearNavGraph(pendingStartIntent: State<Intent?> = mutableStateOf(null)) {
    val navController = rememberSwipeDismissableNavController()

    LaunchedEffect(pendingStartIntent.value) {
        val intent = pendingStartIntent.value ?: return@LaunchedEffect
        val format = intent.getStringExtra("matchFormat") ?: return@LaunchedEffect
        val rule = intent.getStringExtra("thirdSetRule") ?: "FULL_ADVANTAGE"
        navController.navigate("score/$format/$rule") {
            popUpTo("start_match") { inclusive = true }
        }
        (pendingStartIntent as? MutableState)?.value = null
    }

    SwipeDismissableNavHost(
        navController = navController,
        startDestination = "start_match"
    ) {
        composable("start_match") {
            StartMatchScreen(
                onStartLocal = { matchFormat, thirdSetRule ->
                    navController.navigate("score/${matchFormat.name}/${thirdSetRule.name}") {
                        popUpTo("start_match") { inclusive = true }
                    }
                }
            )
        }
        composable(
            route = "score/{matchFormat}/{thirdSetRule}",
            arguments = listOf(
                navArgument("matchFormat") { type = NavType.StringType },
                navArgument("thirdSetRule") { type = NavType.StringType }
            )
        ) {
            ScoreScreen(
                onClose = {
                    navController.navigate("start_match") {
                        popUpTo("start_match") { inclusive = true }
                    }
                }
            )
        }
    }
}
