package com.circapin.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.circapin.R
import com.circapin.domain.repository.UserProfileRepository
import com.circapin.ui.checkin.CheckInRoute
import com.circapin.ui.home.HomeRoute
import com.circapin.ui.onboarding.GoalSetupRoute
import com.circapin.ui.onboarding.OnboardingRoute
import com.circapin.ui.progress.ProgressRoute
import com.circapin.ui.settings.SettingsRoute
import com.circapin.ui.sleeplog.SLEEP_ENTRY_IS_NAP_ARG
import com.circapin.ui.sleeplog.SleepEntryRoute
import com.circapin.ui.sleeplog.SleepLogRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn

private const val ONBOARDING_ROUTE = "onboarding"
private const val FIRST_GOAL_ROUTE = "first_goal"
private const val EDIT_GOAL_ROUTE = "edit_goal"
private const val HOME_ROUTE = "home"
private const val SLEEP_LOG_ROUTE = "sleep_log"
private const val SLEEP_ENTRY_ROUTE = "sleep_entry"
private const val SETTINGS_ROUTE = "settings"
private const val PROGRESS_ROUTE = "progress"
private const val CHECK_IN_ROUTE = "check_in"

private enum class Tab(val route: String, @StringRes val label: Int, @DrawableRes val icon: Int) {
    TODAY(HOME_ROUTE, R.string.nav_today, R.drawable.ic_nav_today),
    SLEEP_LOG(SLEEP_LOG_ROUTE, R.string.nav_sleep_log, R.drawable.ic_nav_sleep_log),
    SETTINGS(SETTINGS_ROUTE, R.string.nav_settings, R.drawable.ic_nav_settings),
}

/** Works out the first screen once, so finishing onboarding doesn't rebuild the graph. */
@HiltViewModel
class StartRouteViewModel @Inject constructor(profiles: UserProfileRepository) : ViewModel() {
    val startRoute: StateFlow<String?> = flow {
        val onboarded = profiles.observeProfile().first()?.onboardingComplete == true
        emit(if (onboarded) HOME_ROUTE else ONBOARDING_ROUTE)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)
}

@Composable
fun CircaPinNavHost(openCheckIn: Boolean = false, onDeepLinkHandled: () -> Unit = {}, viewModel: StartRouteViewModel = hiltViewModel()) {
    val startRoute by viewModel.startRoute.collectAsStateWithLifecycle()
    val start = startRoute ?: return
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    LaunchedEffect(openCheckIn, currentRoute) {
        if (openCheckIn && currentRoute != null && currentRoute !in listOf(ONBOARDING_ROUTE, FIRST_GOAL_ROUTE)) {
            navController.navigate(CHECK_IN_ROUTE) { launchSingleTop = true }
            onDeepLinkHandled()
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
    val useRail = maxWidth >= 840.dp && LocalDensity.current.fontScale <= 1.4f
    val showNavigation = Tab.entries.any { it.route == currentRoute }
    Scaffold(
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            if (showNavigation && !useRail) BottomBar(navController, currentRoute)
        },
    ) { padding ->
        Row(Modifier.padding(padding).consumeWindowInsets(padding)) {
        if (showNavigation && useRail) {
            NavigationRail {
                Tab.entries.forEach { tab ->
                    NavigationRailItem(selected = currentRoute == tab.route,
                        onClick = { navController.openTab(tab) },
                        icon = { Icon(painterResource(tab.icon), contentDescription = null) },
                        label = { Text(stringResource(tab.label)) })
                }
            }
        }
        NavHost(
            navController = navController,
            startDestination = start,
            modifier = Modifier.weight(1f),
        ) {
            composable(ONBOARDING_ROUTE) {
                OnboardingRoute(onFinished = { navController.navigate(FIRST_GOAL_ROUTE) { popUpTo(ONBOARDING_ROUTE) { inclusive = true } } })
            }
            composable(FIRST_GOAL_ROUTE) {
                GoalSetupRoute(
                    onSaved = { navController.navigate(HOME_ROUTE) { popUpTo(FIRST_GOAL_ROUTE) { inclusive = true } } },
                    onCancel = null,
                )
            }
            composable(EDIT_GOAL_ROUTE) {
                GoalSetupRoute(onSaved = { navController.popBackStack() }, onCancel = { navController.popBackStack() })
            }
            composable(HOME_ROUTE) {
                HomeRoute(
                    onEditGoal = { navController.navigate(EDIT_GOAL_ROUTE) },
                    onProgress = { navController.navigate(PROGRESS_ROUTE) },
                    onCheckIn = { navController.navigate(CHECK_IN_ROUTE) },
                )
            }
            composable(PROGRESS_ROUTE) { ProgressRoute() }
            composable(CHECK_IN_ROUTE) {
                CheckInRoute(onDone = { navController.popBackStack() })
            }
            composable(SETTINGS_ROUTE) { SettingsRoute(onReset = { navController.navigate(ONBOARDING_ROUTE) { popUpTo(navController.graph.id) { inclusive = true } } }) }
            composable(SLEEP_LOG_ROUTE) {
                SleepLogRoute(onAddEntry = { isNap -> navController.navigate("$SLEEP_ENTRY_ROUTE/$isNap") })
            }
            composable(
                route = "$SLEEP_ENTRY_ROUTE/{$SLEEP_ENTRY_IS_NAP_ARG}",
                arguments = listOf(navArgument(SLEEP_ENTRY_IS_NAP_ARG) { type = NavType.BoolType }),
            ) {
                SleepEntryRoute(onDone = { navController.popBackStack() })
            }
        }
        }
    }
    }
}

@Composable
private fun BottomBar(navController: NavHostController, currentRoute: String?) {
    NavigationBar {
        Tab.entries.forEach { tab ->
            NavigationBarItem(
                selected = currentRoute == tab.route,
                onClick = { navController.openTab(tab) },
                icon = { Icon(painterResource(tab.icon), contentDescription = null) },
                label = { Text(stringResource(tab.label)) },
            )
        }
    }
}

private fun NavHostController.openTab(tab: Tab) {
    navigate(tab.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
