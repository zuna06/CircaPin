package com.circapin.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.circapin.worker.DailyPlanWorker
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/** Android drops alarms on reboot; permission and clock changes also require rebuilding their targets. */
@AndroidEntryPoint
class ReminderRestoreReceiver : BroadcastReceiver() {
    @Inject lateinit var scheduler: ReminderScheduler
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in setOf("android.intent.action.BOOT_COMPLETED", "android.intent.action.TIME_SET",
                "android.intent.action.TIMEZONE_CHANGED", "android.intent.action.MY_PACKAGE_REPLACED",
                "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED")) return
        scheduler.cancelAll()
        WorkManager.getInstance(context).enqueue(OneTimeWorkRequestBuilder<DailyPlanWorker>().build())
    }
}
