package com.circapin.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.circapin.domain.repository.UserProfileRepository
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
private const val SLEEP_LOG_ROUTE = "sleep_log"
private const val SLEEP_ENTRY_ROUTE = "sleep_entry"

/** Works out the first screen once, so finishing onboarding doesn't rebuild the graph. */
@HiltViewModel
class StartRouteViewModel @Inject constructor(profiles: UserProfileRepository) : ViewModel() {
    val startRoute: StateFlow<String?> = flow {
        val onboarded = profiles.observeProfile().first()?.onboardingComplete == true
        emit(if (onboarded) SLEEP_LOG_ROUTE else ONBOARDING_ROUTE)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)
}

@Composable
fun CircaPinNavHost(viewModel: StartRouteViewModel = hiltViewModel()) {
    val startRoute by viewModel.startRoute.collectAsStateWithLifecycle()
    val start = startRoute ?: return
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = start) {
        composable(ONBOARDING_ROUTE) {
            OnboardingRoute(
                onFinished = {
                    navController.navigate(SLEEP_LOG_ROUTE) {
                        popUpTo(ONBOARDING_ROUTE) { inclusive = true }
                    }
                },
            )
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
