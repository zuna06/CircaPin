package com.circapin

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.circapin.worker.DailyPlanWorker
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

@HiltAndroidApp
class CircaPinApp : Application(), Configuration.Provider {
    @Inject
    lateinit var modelUpdater: ModelUpdater

    @Inject
    lateinit var todayUpdater: TodayUpdater

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        modelUpdater.start(appScope)
        todayUpdater.start(appScope)
        DailyPlanWorker.enqueue(this)
    }
}
