package com.circapin.ui.settings

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.HealthConnectFeatures
import androidx.health.connect.client.permission.HealthPermission
import com.circapin.screentime.ScreenTimeTracker
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

data class PermissionState(
    val exact: Boolean = false,
    val backgroundAvailable: Boolean = false,
    val background: Boolean = false,
    val usage: Boolean = false,
    val notifications: Boolean = false,
)

interface PermissionReader { suspend fun read(): PermissionState }

class DevicePermissions @Inject constructor(
    @ApplicationContext private val context: Context,
    private val screenTime: ScreenTimeTracker,
) : PermissionReader {
    override suspend fun read(): PermissionState {
        val available = HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE
        val client = if (available) HealthConnectClient.getOrCreate(context) else null
        val backgroundAvailable = client?.features?.getFeatureStatus(HealthConnectFeatures.FEATURE_READ_HEALTH_DATA_IN_BACKGROUND) == HealthConnectFeatures.FEATURE_STATUS_AVAILABLE
        val granted = client?.permissionController?.getGrantedPermissions().orEmpty()
        return PermissionState(
            exact = Build.VERSION.SDK_INT < 31 || context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms(),
            backgroundAvailable = backgroundAvailable,
            background = HealthPermission.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND in granted,
            usage = screenTime.hasPermission(),
            notifications = Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED,
        )
    }
}
