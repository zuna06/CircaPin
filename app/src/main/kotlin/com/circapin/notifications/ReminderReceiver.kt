package com.circapin.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.circapin.MainActivity
import com.circapin.R

class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val typeName = intent.getStringExtra(ReminderScheduler.EXTRA_TYPE) ?: return
        val type = ReminderType.entries.firstOrNull { it.name == typeName } ?: return

        when (intent.action) {
            ReminderScheduler.ACTION_SNOOZE -> {
                NotificationManagerCompat.from(context).cancel(type.requestCodeBase)
                val scheduler = ReminderScheduler(context)
                scheduler.snooze(type)
                return
            }
        }

        ensureChannel(context, type)
        if (!hasNotificationPermission(context)) return

        val openApp = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_SINGLE_TOP },
            PendingIntent.FLAG_IMMUTABLE,
        )
        val snooze = PendingIntent.getBroadcast(
            context, type.requestCodeBase + 1,
            Intent(context, ReminderReceiver::class.java).apply {
                action = ReminderScheduler.ACTION_SNOOZE
                putExtra(ReminderScheduler.EXTRA_TYPE, type.name)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val (title, text) = content(context, type)
        val notification = NotificationCompat.Builder(context, type.channelId)
            .setSmallIcon(R.drawable.ic_nav_today)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .addAction(0, context.getString(R.string.reminder_snooze), snooze)
            .build()

        NotificationManagerCompat.from(context).notify(type.requestCodeBase, notification)
    }

    private fun content(context: Context, type: ReminderType): Pair<String, String> = when (type) {
        ReminderType.LIGHT -> context.getString(R.string.reminder_light_title) to context.getString(R.string.reminder_light_text)
        ReminderType.CAFFEINE -> context.getString(R.string.reminder_caffeine_title) to context.getString(R.string.reminder_caffeine_text)
        ReminderType.BEDTIME -> context.getString(R.string.reminder_bedtime_title) to context.getString(R.string.reminder_bedtime_text)
        ReminderType.CHECK_IN -> context.getString(R.string.reminder_checkin_title) to context.getString(R.string.reminder_checkin_text)
        ReminderType.MEAL -> context.getString(R.string.reminder_meal_title) to context.getString(R.string.reminder_meal_text)
    }

    private fun ensureChannel(context: Context, type: ReminderType) {
        val name = when (type) {
            ReminderType.LIGHT -> context.getString(R.string.channel_light)
            ReminderType.CAFFEINE -> context.getString(R.string.channel_caffeine)
            ReminderType.BEDTIME -> context.getString(R.string.channel_bedtime)
            ReminderType.CHECK_IN -> context.getString(R.string.channel_check_in)
            ReminderType.MEAL -> context.getString(R.string.channel_meal)
        }
        val channel = NotificationChannel(type.channelId, name, NotificationManager.IMPORTANCE_HIGH)
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun hasNotificationPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
}
