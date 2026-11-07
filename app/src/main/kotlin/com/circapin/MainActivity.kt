package com.circapin

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
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

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Health Connect only allows reads in the foreground, so sync each time the app comes back to it,
        // including after its permission screen closes.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) { sleepSync.sync() }
        }
        setContent {
            MaterialTheme {
                CircaPinNavHost()
            }
        }
    }
}
