package com.ironmind.app.ui.navigation

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.ironmind.app.R
import com.ironmind.app.ui.dashboard.DashboardScreen
import com.ironmind.app.ui.model.ModelDownloadScreen
import com.ironmind.app.ui.progress.ProgressScreen
import com.ironmind.app.ui.routineedit.RoutineEditScreen
import com.ironmind.app.ui.session.SessionScreen
import com.ironmind.app.ui.theme.Black
import com.ironmind.app.ui.theme.Cyan
import com.ironmind.app.ui.theme.Gold
import com.ironmind.app.ui.theme.TextMuted

/** Root navigation graph with a bottom bar on the two top-level tabs (Home / Progress). */
@Composable
fun IronMindNavHost(navController: NavHostController = rememberNavController()) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = currentRoute == Destinations.DASHBOARD || currentRoute == Destinations.PROGRESS_ROUTE

    Scaffold(
        containerColor = Black,
        // Inner screens (their own Scaffold/TopAppBar) handle top insets; the NavigationBar
        // handles its own bottom inset. Zeroing here avoids double-counting the status bar.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (showBottomBar) IronMindBottomBar(navController = navController, currentRoute = currentRoute)
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Destinations.DASHBOARD,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(Destinations.DASHBOARD) {
                DashboardScreen(
                    onStartSession = { routineId ->
                        navController.navigate(Destinations.session(routineId = routineId))
                    },
                    onOpenProgress = { exerciseId ->
                        navController.navigate(Destinations.progress(exerciseId))
                    },
                    onNewRoutine = { navController.navigate(Destinations.routineEdit()) },
                    onEditRoutine = { routineId ->
                        navController.navigate(Destinations.routineEdit(routineId))
                    },
                    onDownloadModel = { navController.navigate(Destinations.MODEL_ROUTE) },
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

            composable(
                route = Destinations.ROUTINE_EDIT_ROUTE,
                arguments = listOf(
                    navArgument(Destinations.ARG_ROUTINE_ID) { type = NavType.LongType; defaultValue = 0L },
                ),
            ) {
                RoutineEditScreen(onDone = { navController.popBackStack() })
            }

            composable(Destinations.MODEL_ROUTE) {
                ModelDownloadScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}

@Composable
private fun IronMindBottomBar(navController: NavHostController, currentRoute: String?) {
    val itemColors = NavigationBarItemDefaults.colors(
        selectedIconColor = Gold,
        selectedTextColor = Gold,
        unselectedIconColor = TextMuted,
        unselectedTextColor = TextMuted,
        indicatorColor = Cyan.copy(alpha = 0.12f),
    )

    fun navigateTab(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.startDestinationId) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    NavigationBar(containerColor = Black) {
        NavigationBarItem(
            selected = currentRoute == Destinations.DASHBOARD,
            onClick = { navigateTab(Destinations.DASHBOARD) },
            icon = { Icon(Icons.Filled.Home, contentDescription = stringResource(R.string.nav_home)) },
            label = { Text(stringResource(R.string.nav_home)) },
            colors = itemColors,
        )
        NavigationBarItem(
            selected = currentRoute == Destinations.PROGRESS_ROUTE,
            onClick = { navigateTab(Destinations.progress()) },
            icon = { Icon(Icons.Filled.BarChart, contentDescription = stringResource(R.string.progress_title)) },
            label = { Text(stringResource(R.string.progress_title)) },
            colors = itemColors,
        )
    }
}
