package com.ironmind.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.ironmind.app.ui.dashboard.DashboardScreen
import com.ironmind.app.ui.progress.ProgressScreen
import com.ironmind.app.ui.session.SessionScreen

/** Root navigation graph: Dashboard → Session / Progress. */
@Composable
fun IronMindNavHost(navController: NavHostController = rememberNavController()) {
    NavHost(navController = navController, startDestination = Destinations.DASHBOARD) {

        composable(Destinations.DASHBOARD) {
            DashboardScreen(
                onStartSession = { routineId ->
                    navController.navigate(Destinations.session(routineId = routineId))
                },
                onOpenProgress = { exerciseId ->
                    navController.navigate(Destinations.progress(exerciseId))
                },
            )
        }

        composable(
            route = Destinations.SESSION_ROUTE,
            arguments = listOf(
                navArgument(Destinations.ARG_SESSION_ID) { type = NavType.LongType; defaultValue = 0L },
                navArgument(Destinations.ARG_ROUTINE_ID) { type = NavType.LongType; defaultValue = 0L },
            ),
        ) {
            SessionScreen(onBack = { navController.popBackStack() })
        }

        composable(
            route = Destinations.PROGRESS_ROUTE,
            arguments = listOf(
                navArgument(Destinations.ARG_EXERCISE_ID) { type = NavType.LongType; defaultValue = 0L },
            ),
        ) {
            ProgressScreen(onBack = { navController.popBackStack() })
        }
    }
}
