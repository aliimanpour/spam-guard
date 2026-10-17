package com.smsspam.detector.ui

import android.net.Uri
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Rule
import androidx.compose.material.icons.automirrored.outlined.Rule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Sms
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
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
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.smsspam.detector.ui.screens.ConversationScreen
import com.smsspam.detector.ui.screens.ConversationsScreen
import com.smsspam.detector.ui.screens.QuarantineScreen
import com.smsspam.detector.ui.screens.RuleEditorScreen
import com.smsspam.detector.ui.screens.RulesScreen
import com.smsspam.detector.ui.screens.ScheduledScreen
import com.smsspam.detector.ui.screens.SettingsScreen

/**
 * A "new message" request coming from outside the app (an `sms:`/`smsto:` link
 * handled by [com.smsspam.detector.MainActivity]). Carries the recipient and an
 * optional prefilled body.
 */
data class ComposeTarget(
    val recipient: String,
    val body: String,
)

private object Routes {
    const val MESSAGES = "messages"
    const val QUARANTINE = "quarantine"
    const val RULES = "rules"
    const val SETTINGS = "settings"
    const val SCHEDULED = "scheduled"
    const val CONVERSATION = "conversation"
    const val CONVERSATION_ARG = "address"
    const val RULE_EDITOR = "rule_editor"
    const val RULE_EDITOR_ARG = "ruleId"
    const val COMPOSE = "compose"
    const val COMPOSE_RECIPIENT_ARG = "recipient"
    const val COMPOSE_BODY_ARG = "body"
    fun conversation(address: String) = "$CONVERSATION/${Uri.encode(address)}"
    fun ruleEditor(id: String) = "$RULE_EDITOR/$id"
    fun compose(recipient: String, body: String) =
        "$COMPOSE?$COMPOSE_RECIPIENT_ARG=${Uri.encode(recipient)}&$COMPOSE_BODY_ARG=${Uri.encode(body)}"
}

private data class TopLevelDestination(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
)

@Composable
fun AppRoot(
    viewModel: AppViewModel = viewModel(),
    composeTarget: ComposeTarget? = null,
    onComposeTargetConsumed: () -> Unit = {},
) {
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    val topLevel = listOf(
        TopLevelDestination(Routes.MESSAGES, "Messages", Icons.Filled.Sms, Icons.Outlined.Sms),
        TopLevelDestination(Routes.QUARANTINE, "Blocked", Icons.Filled.Shield, Icons.Outlined.Shield),
        TopLevelDestination(Routes.RULES, "Rules", Icons.AutoMirrored.Filled.Rule, Icons.AutoMirrored.Outlined.Rule),
        TopLevelDestination(Routes.SETTINGS, "Settings", Icons.Filled.Settings, Icons.Outlined.Settings),
    )
    val showBottomBar = topLevel.any { dest ->
        currentDestination?.hierarchy?.any { it.route == dest.route } == true
    }

    val event by viewModel.events.collectAsStateWithLifecycle()
    val newBlockedCount by viewModel.newBlockedCount.collectAsStateWithLifecycle()
    LaunchedEffect(event) {
        val current = event ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(
            when (current) {
                is UiEvent.Message -> current.text
            }
        )
        viewModel.consumeEvent()
    }

    // An external `sms:`/`smsto:` link opens a prefilled compose screen here,
    // inside the single activity, instead of a separate compose activity.
    LaunchedEffect(composeTarget) {
        val target = composeTarget ?: return@LaunchedEffect
        navController.navigate(Routes.compose(target.recipient, target.body))
        onComposeTargetConsumed()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        // This outer scaffold only owns the bottom navigation bar. Each screen
        // draws its own top bar and handles the status bar inset, so consuming
        // the window insets here too left a blank status-bar-tall gap above
        // every screen's title bar.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
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
                                val badgeCount = if (dest.route == Routes.QUARANTINE) newBlockedCount else 0
                                val imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon
                                if (badgeCount > 0) {
                                    BadgedBox(badge = {
                                        Badge { Text(if (badgeCount > 99) "99+" else "$badgeCount") }
                                    }) {
                                        Icon(imageVector = imageVector, contentDescription = dest.label)
                                    }
                                } else {
                                    Icon(imageVector = imageVector, contentDescription = dest.label)
                                }
                            },
                            label = { Text(dest.label) },
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        // No screen transitions: navigation is instant, so taps and back never
        // wait on an animation.
        NavHost(
            navController = navController,
            startDestination = Routes.MESSAGES,
            modifier = Modifier.padding(innerPadding),
            enterTransition = { EnterTransition.None },
            exitTransition = { ExitTransition.None },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = { ExitTransition.None },
        ) {
            composable(Routes.MESSAGES) {
                ConversationsScreen(
                    viewModel = viewModel,
                    onOpenConversation = { address -> navController.navigate(Routes.conversation(address)) },
                    onNewMessage = { navController.navigate(Routes.compose("", "")) },
                    onOpenScheduled = { navController.navigate(Routes.SCHEDULED) },
                )
            }
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
            composable(Routes.SCHEDULED) {
                ScheduledScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
            }
            composable(
                route = "${Routes.COMPOSE}?${Routes.COMPOSE_RECIPIENT_ARG}={${Routes.COMPOSE_RECIPIENT_ARG}}" +
                    "&${Routes.COMPOSE_BODY_ARG}={${Routes.COMPOSE_BODY_ARG}}",
                arguments = listOf(
                    navArgument(Routes.COMPOSE_RECIPIENT_ARG) {
                        type = NavType.StringType
                        defaultValue = ""
                    },
                    navArgument(Routes.COMPOSE_BODY_ARG) {
                        type = NavType.StringType
                        defaultValue = ""
                    },
                ),
            ) { entry ->
                val recipient = entry.arguments?.getString(Routes.COMPOSE_RECIPIENT_ARG)
                    ?.let { Uri.decode(it) }.orEmpty()
                val body = entry.arguments?.getString(Routes.COMPOSE_BODY_ARG)
                    ?.let { Uri.decode(it) }.orEmpty()
                ConversationScreen(
                    viewModel = viewModel,
                    address = recipient.takeIf { it.isNotBlank() },
                    initialBody = body,
                    onBack = { navController.popBackStack() },
                )
            }
            composable(
                route = "${Routes.CONVERSATION}/{${Routes.CONVERSATION_ARG}}",
                arguments = listOf(navArgument(Routes.CONVERSATION_ARG) { type = NavType.StringType }),
            ) { entry ->
                val address = entry.arguments?.getString(Routes.CONVERSATION_ARG)?.let { Uri.decode(it) }
                ConversationScreen(
                    viewModel = viewModel,
                    address = address,
                    onBack = { navController.popBackStack() },
                )
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
