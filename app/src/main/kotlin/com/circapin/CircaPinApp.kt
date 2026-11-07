package com.circapin

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

@HiltAndroidApp
class CircaPinApp : Application() {
    @Inject
    lateinit var modelUpdater: ModelUpdater

    @Inject
    lateinit var todayUpdater: TodayUpdater

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        modelUpdater.start(appScope)
        todayUpdater.start(appScope)
    }
}
