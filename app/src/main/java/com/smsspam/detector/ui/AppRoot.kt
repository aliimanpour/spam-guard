package com.smsspam.detector.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Rule
import androidx.compose.material.icons.automirrored.outlined.Rule
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.NavType
import com.smsspam.detector.ui.screens.QuarantineScreen
import com.smsspam.detector.ui.screens.RuleEditorScreen
import com.smsspam.detector.ui.screens.RulesScreen
import com.smsspam.detector.ui.screens.SettingsScreen

private object Routes {
    const val QUARANTINE = "quarantine"
    const val RULES = "rules"
    const val SETTINGS = "settings"
    const val RULE_EDITOR = "rule_editor"
    const val RULE_EDITOR_ARG = "ruleId"
    fun ruleEditor(id: String) = "$RULE_EDITOR/$id"
}

private data class TopLevelDestination(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
)

@Composable
fun AppRoot(viewModel: AppViewModel = viewModel()) {
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    val topLevel = listOf(
        TopLevelDestination(Routes.QUARANTINE, "Blocked", Icons.Filled.Shield, Icons.Outlined.Shield),
        TopLevelDestination(Routes.RULES, "Rules", Icons.AutoMirrored.Filled.Rule, Icons.AutoMirrored.Outlined.Rule),
        TopLevelDestination(Routes.SETTINGS, "Settings", Icons.Filled.Settings, Icons.Outlined.Settings),
    )
    val showBottomBar = topLevel.any { dest ->
        currentDestination?.hierarchy?.any { it.route == dest.route } == true
    }

    val event by viewModel.events.collectAsStateWithLifecycle()
    LaunchedEffect(event) {
        val current = event ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(
            when (current) {
                is UiEvent.Message -> current.text
            }
        )
        viewModel.consumeEvent()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    topLevel.forEach { dest ->
                        val selected = currentDestination?.hierarchy?.any { it.route == dest.route } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(dest.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                    contentDescription = dest.label,
                                )
                            },
                            label = { Text(dest.label) },
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.QUARANTINE,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(Routes.QUARANTINE) {
                QuarantineScreen(
                    viewModel = viewModel,
                    snackbarHostState = snackbarHostState,
                )
            }
            composable(Routes.RULES) {
                RulesScreen(
                    viewModel = viewModel,
                    onEditRule = { id -> navController.navigate(Routes.ruleEditor(id)) },
                )
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(viewModel = viewModel)
            }
            composable(
                route = "${Routes.RULE_EDITOR}/{${Routes.RULE_EDITOR_ARG}}",
                arguments = listOf(navArgument(Routes.RULE_EDITOR_ARG) { type = NavType.StringType }),
            ) { entry ->
                val ruleId = entry.arguments?.getString(Routes.RULE_EDITOR_ARG)
                RuleEditorScreen(
                    viewModel = viewModel,
                    ruleId = ruleId?.takeIf { it != "new" },
                    onDone = { navController.popBackStack() },
                )
            }
        }
    }
}
