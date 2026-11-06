package com.entitypulse.app.ui.screens

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DynamicFeed
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.entitypulse.app.ui.MainViewModel
import com.entitypulse.app.ui.navigation.Screen
import com.entitypulse.app.ui.screens.entity.EntityDetailScreen
import com.entitypulse.app.ui.screens.feed.FeedScreen
import com.entitypulse.app.ui.screens.hub.HubScreen
import com.entitypulse.app.ui.screens.post.PostDetailScreen
import com.entitypulse.app.ui.screens.settings.SettingsScreen

@Composable
fun MainScreen(
    viewModel: MainViewModel,
    initialPostId: Long? = null,
    initialEntityId: Long? = null
) {
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.userMessage.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    LaunchedEffect(initialPostId, initialEntityId) {
        if (initialPostId != null && initialPostId > 0) {
            navController.navigate(Screen.PostDetail.createRoute(initialPostId))
        } else if (initialEntityId != null && initialEntityId > 0) {
            navController.navigate(Screen.EntityDetail.createRoute(initialEntityId))
        }
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val bottomBarRoutes = listOf(Screen.Hub.route, Screen.Feed.route, Screen.Settings.route)
    val shouldShowBottomBar = currentRoute in bottomBarRoutes

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (shouldShowBottomBar) {
                NavigationBar {
                    NavigationBarItem(
                        selected = currentRoute == Screen.Hub.route,
                        onClick = {
                            navController.navigate(Screen.Hub.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(Icons.Default.Hub, contentDescription = "看板") },
                        label = { Text("看板") }
                    )
                    NavigationBarItem(
                        selected = currentRoute == Screen.Feed.route,
                        onClick = {
                            navController.navigate(Screen.Feed.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(Icons.Default.DynamicFeed, contentDescription = "动态流") },
                        label = { Text("动态流") }
                    )
                    NavigationBarItem(
                        selected = currentRoute == Screen.Settings.route,
                        onClick = {
                            navController.navigate(Screen.Settings.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(Icons.Default.Settings, contentDescription = "设置") },
                        label = { Text("设置") }
                    )
                }
            }
        }
    ) { paddingValues ->
        NavHost(
            navController = navController,
            startDestination = Screen.Hub.route,
            modifier = Modifier.padding(paddingValues)
        ) {
            composable(Screen.Hub.route) {
                HubScreen(
                    viewModel = viewModel,
                    onNavigateToEntity = { entityId ->
                        navController.navigate(Screen.EntityDetail.createRoute(entityId))
                    }
                )
            }
            composable(Screen.Feed.route) {
                FeedScreen(
                    viewModel = viewModel,
                    onNavigateToPost = { postId ->
                        navController.navigate(Screen.PostDetail.createRoute(postId))
                    }
                )
            }
            composable(Screen.Settings.route) {
                SettingsScreen(viewModel = viewModel)
            }
            composable(
                route = Screen.EntityDetail.route,
                arguments = listOf(navArgument("entityId") { type = NavType.LongType })
            ) { backStackEntry ->
                val entityId = backStackEntry.arguments?.getLong("entityId") ?: 0L
                EntityDetailScreen(
                    entityId = entityId,
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                    onNavigateToPost = { postId ->
                        navController.navigate(Screen.PostDetail.createRoute(postId))
                    }
                )
            }
            composable(
                route = Screen.PostDetail.route,
                arguments = listOf(navArgument("postId") { type = NavType.LongType })
            ) { backStackEntry ->
                val postId = backStackEntry.arguments?.getLong("postId") ?: 0L
                PostDetailScreen(
                    postId = postId,
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}
