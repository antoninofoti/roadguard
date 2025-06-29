package com.example.roadguard.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.example.roadguard.view.MainScreen
import com.example.roadguard.view.MainViewModel
import com.example.roadguard.view.ReportsMapScreen
import com.example.roadguard.view.ReportsScreen
import com.example.roadguard.view.ReportsViewModel

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object Home : Screen("home", "Home", Icons.Default.Home)
    object Reports : Screen("reports", "Reports", Icons.Default.List)
    object Map : Screen("map", "Map", Icons.Default.LocationOn)
}

private val items = listOf(
    Screen.Home,
    Screen.Reports,
    Screen.Map
)

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val reportsViewModel: ReportsViewModel = viewModel()
    val mainViewModel: MainViewModel = viewModel()

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
            composable(Screen.Home.route) { MainScreen(viewModel = mainViewModel) }
            composable(Screen.Reports.route) { ReportsScreen(reportsViewModel = reportsViewModel) }
            composable(Screen.Map.route) { ReportsMapScreen(reportsViewModel = reportsViewModel) }
        }
    }
}
