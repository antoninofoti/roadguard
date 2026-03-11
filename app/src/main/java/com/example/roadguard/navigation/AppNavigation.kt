package com.example.roadguard.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.roadguard.analytics.AnalyticsScreen
import com.example.roadguard.analytics.AnalyticsViewModel
import com.example.roadguard.home.HomeViewModel
import com.example.roadguard.operator.OperatorDashboardScreen
import com.example.roadguard.operator.OperatorViewModel
import com.example.roadguard.ui.LivePotholeDetectionFragmentComposable
import com.example.roadguard.view.MainScreen
import com.example.roadguard.view.MainViewModel
import com.example.roadguard.view.ReportsMapScreen
import com.example.roadguard.view.ReportsScreen
import com.example.roadguard.view.ReportsViewModel

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object Home : Screen("home", "Home", Icons.Default.Home)
    object Reports : Screen("reports", "Reports", Icons.AutoMirrored.Filled.List)
    object Map : Screen("map", "Map", Icons.Default.LocationOn)
    object OperatorDashboard : Screen("operator", "Dashboard", Icons.Default.Build)
    object Analytics : Screen("analytics", "Analytics", Icons.Default.Star)
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val reportsViewModel: ReportsViewModel = viewModel()
    val mainViewModel: MainViewModel = viewModel()
    val homeViewModel: HomeViewModel = viewModel()
    val operatorViewModel: OperatorViewModel = viewModel()
    val analyticsViewModel: AnalyticsViewModel = viewModel()

    // Observe user role for conditional navigation
    val user by homeViewModel.user.collectAsState()
    val isOperator = user?.isOperator() == true

    // Build navigation items — operator tab shown only for operators/admins
    val items = buildList {
        add(Screen.Home)
        add(Screen.Reports)
        add(Screen.Map)
        if (isOperator) {
            add(Screen.OperatorDashboard)
            add(Screen.Analytics)
        }
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination
                items.forEach { screen ->
                    NavigationBarItem(
                        icon = { Icon(screen.icon, contentDescription = null) },
                        label = { Text(screen.title) },
                        selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                        onClick = {
                            navController.navigate(screen.route) {
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
    ) { innerPadding ->
        NavHost(
            navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Home.route) { MainScreen(viewModel = mainViewModel, navController = navController) }
            composable(Screen.Reports.route) { ReportsScreen(reportsViewModel = reportsViewModel) }
            composable(Screen.Map.route) { ReportsMapScreen(reportsViewModel = reportsViewModel) }
            composable(Screen.OperatorDashboard.route) {
                OperatorDashboardScreen(operatorViewModel = operatorViewModel)
            }
            composable(Screen.Analytics.route) {
                AnalyticsScreen(analyticsViewModel = analyticsViewModel)
            }
        }
    }
}

