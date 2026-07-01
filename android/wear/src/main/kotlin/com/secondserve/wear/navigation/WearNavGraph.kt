package com.secondserve.wear.navigation

import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import com.secondserve.domain.model.MatchFormat
import com.secondserve.domain.model.ThirdSetRule
import com.secondserve.wear.presentation.match.ScoreScreen
import com.secondserve.wear.presentation.start.StartMatchScreen

private const val SCORE_ROUTE = "score/{matchFormat}/{thirdSetRule}?opponent={opponent}"

private fun scoreRoute(matchFormat: MatchFormat, thirdSetRule: ThirdSetRule, opponent: String? = null): String {
    val base = "score/${matchFormat.name}/${thirdSetRule.name}"
    return if (opponent.isNullOrBlank()) base else "$base?opponent=${Uri.encode(opponent)}"
}

@Composable
fun WearNavGraph(
    pendingStartIntent: Intent? = null,
    onStartIntentConsumed: () -> Unit = {}
) {
    val navController = rememberSwipeDismissableNavController()

    LaunchedEffect(pendingStartIntent) {
        val intent = pendingStartIntent ?: return@LaunchedEffect
        val rawFormat = intent.getStringExtra("matchFormat") ?: return@LaunchedEffect
        val rawRule = intent.getStringExtra("thirdSetRule") ?: "FULL_ADVANTAGE"
        val opponent = intent.getStringExtra("opponent")
        val format = runCatching { MatchFormat.valueOf(rawFormat) }.getOrNull() ?: return@LaunchedEffect
        val rule = runCatching { ThirdSetRule.valueOf(rawRule) }.getOrElse { ThirdSetRule.FULL_ADVANTAGE }
        navController.navigate(scoreRoute(format, rule, opponent)) {
            popUpTo("start_match") { inclusive = true }
        }
        onStartIntentConsumed()
    }

    SwipeDismissableNavHost(
        navController = navController,
        startDestination = "start_match"
    ) {
        composable("start_match") {
            StartMatchScreen(
                onStartLocal = { matchFormat, thirdSetRule ->
                    navController.navigate(scoreRoute(matchFormat, thirdSetRule)) {
                        popUpTo("start_match") { inclusive = true }
                    }
                }
            )
        }
        composable(
            route = SCORE_ROUTE,
            arguments = listOf(
                navArgument("matchFormat") { type = NavType.StringType },
                navArgument("thirdSetRule") { type = NavType.StringType },
                navArgument("opponent") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
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
