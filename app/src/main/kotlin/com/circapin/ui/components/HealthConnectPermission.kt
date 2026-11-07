package com.circapin.ui.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.runtime.Composable
import androidx.health.connect.client.PermissionController
import com.circapin.data.healthconnect.HealthConnectSleepSource

/** Returns a function that shows the Health Connect permission screen. */
@Composable
fun rememberHealthConnectPermissionRequest(onResult: () -> Unit): () -> Unit {
    val launcher = rememberLauncherForActivityResult(
        contract = PermissionController.createRequestPermissionResultContract(),
        onResult = { onResult() },
    )
    return { launcher.launch(HealthConnectSleepSource.PERMISSIONS) }
}
