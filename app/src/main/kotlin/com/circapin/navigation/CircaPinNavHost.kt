package com.circapin.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
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
import com.circapin.ui.home.HomeRoute
import com.circapin.ui.onboarding.GoalSetupRoute
import com.circapin.ui.onboarding.OnboardingRoute
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

private enum class Tab(val route: String, @StringRes val label: Int, @DrawableRes val icon: Int) {
    TODAY(HOME_ROUTE, R.string.nav_today, R.drawable.ic_nav_today),
    SLEEP_LOG(SLEEP_LOG_ROUTE, R.string.nav_sleep_log, R.drawable.ic_nav_sleep_log),
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
fun CircaPinNavHost(viewModel: StartRouteViewModel = hiltViewModel()) {
    val startRoute by viewModel.startRoute.collectAsStateWithLifecycle()
    val start = startRoute ?: return
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            if (Tab.entries.any { it.route == currentRoute }) BottomBar(navController, currentRoute)
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = start,
            modifier = Modifier
                .padding(padding)
                .consumeWindowInsets(padding),
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
                HomeRoute(onEditGoal = { navController.navigate(EDIT_GOAL_ROUTE) })
            }
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

@Composable
private fun BottomBar(navController: NavHostController, currentRoute: String?) {
    NavigationBar {
        Tab.entries.forEach { tab ->
            NavigationBarItem(
                selected = currentRoute == tab.route,
                onClick = {
                    navController.navigate(tab.route) {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = { Icon(painterResource(tab.icon), contentDescription = null) },
                label = { Text(stringResource(tab.label)) },
            )
        }
    }
}
