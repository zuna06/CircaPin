package com.circapin.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.circapin.screentime.ScreenTimeTracker
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

@HiltWorker
class ScreenTimeWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val tracker: ScreenTimeTracker,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        return try { tracker.refresh(); Result.success() }
        catch (e: kotlin.coroutines.cancellation.CancellationException) { throw e }
        catch (_: Exception) { Result.retry() }
    }
    companion object {
        fun enqueue(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork("screen_time", ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<ScreenTimeWorker>(15, TimeUnit.MINUTES).build())
        }
    }
}
