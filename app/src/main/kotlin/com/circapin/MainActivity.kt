package com.circapin

import android.os.Bundle
import android.content.Intent
import androidx.compose.runtime.*
import com.circapin.ui.theme.CircaPinTheme
import com.circapin.domain.repository.UserProfileRepository
import com.circapin.screentime.ScreenTimeTracker
import com.circapin.worker.ScreenTimeWorker
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.delay
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.circapin.navigation.CircaPinNavHost
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var sleepSync: SleepSync

    @Inject lateinit var profiles: UserProfileRepository
    @Inject lateinit var screenTime: ScreenTimeTracker
    private var openCheckIn by mutableStateOf(false)

    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); openCheckIn = intent.getBooleanExtra("open_check_in", false) }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        openCheckIn = intent.getBooleanExtra("open_check_in", false)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { profiles.observeProfile().map { it?.onboardingComplete == true }.distinctUntilChanged().collect { if (it) sleepSync.sync() } }
                launch { while (true) {
                    try { screenTime.refresh(); if (screenTime.hasPermission()) ScreenTimeWorker.enqueue(this@MainActivity)
                        else androidx.work.WorkManager.getInstance(this@MainActivity).cancelUniqueWork("screen_time") }
                    catch (e: kotlin.coroutines.cancellation.CancellationException) { throw e }
                    catch (_: Exception) { /* Usage access is optional. */ }
                    delay(60_000)
                } }
            }
        }
        setContent {
            CircaPinTheme {
                CircaPinNavHost(openCheckIn = openCheckIn, onDeepLinkHandled = { openCheckIn = false })
            }
        }
    }

}
