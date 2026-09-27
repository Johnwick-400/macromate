package com.example.notify

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.domain.schedule.MealSlot
import com.example.domain.schedule.SlotStatus
import java.time.LocalDate
import java.time.ZoneId

/**
 * Offline meal-slot reminders: plain AlarmManager + a notification, no network and no extra
 * library. Alarms are inexact (10-minute window) so they don't need the exact-alarm permission.
 */
object MealReminders {
    private const val CHANNEL_ID = "meal_reminders"
    private const val MAX_SLOTS = 8 // request codes 0..7
    private const val WINDOW_MS = 10 * 60 * 1000L

    internal const val EXTRA_TITLE = "title"
    internal const val EXTRA_TEXT = "text"
    internal const val EXTRA_ID = "id"

    /** Replaces all pending reminders with today's still-upcoming slots (or none if disabled). */
    fun schedule(context: Context, date: LocalDate, slots: List<MealSlot>, enabled: Boolean) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        cancelAll(context, alarms)
        if (!enabled) return

        val now = System.currentTimeMillis()
        slots.take(MAX_SLOTS).forEachIndexed { index, slot ->
            if (slot.status != SlotStatus.UPCOMING) return@forEachIndexed
            val triggerAt = slot.time.atDate(date).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            if (triggerAt <= now) return@forEachIndexed
            val text = "Around ${slot.targetKcal} kcal · ${slot.targetProteinG} g protein. ${slot.suggestion}".trim()
            alarms.setWindow(AlarmManager.RTC_WAKEUP, triggerAt, WINDOW_MS, pendingIntent(context, index, slot.type.label, text))
        }
    }

    private fun cancelAll(context: Context, alarms: AlarmManager) {
        repeat(MAX_SLOTS) { index -> alarms.cancel(pendingIntent(context, index, "", "")) }
    }

    private fun pendingIntent(context: Context, index: Int, title: String, text: String): PendingIntent {
        val intent = Intent(context, MealReminderReceiver::class.java)
            .putExtra(EXTRA_TITLE, title)
            .putExtra(EXTRA_TEXT, text)
            .putExtra(EXTRA_ID, index)
        return PendingIntent.getBroadcast(
            context, index, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun canPostNotifications(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    internal fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Meal reminders", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "A nudge when a planned meal is due"
                }
            )
        }
    }

    internal fun show(context: Context, id: Int, title: String, text: String) {
        if (!canPostNotifications(context)) return
        ensureChannel(context)
        val open = PendingIntent.getActivity(
            context, 100 + id,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_dietary_log)
            .setContentTitle("$title time")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(1000 + id, notification)
        } catch (_: SecurityException) {
            // Permission revoked between the check and the post; nothing to do.
        }
    }
}

class MealReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        MealReminders.show(
            context,
            id = intent.getIntExtra(MealReminders.EXTRA_ID, 0),
            title = intent.getStringExtra(MealReminders.EXTRA_TITLE) ?: "Meal",
            text = intent.getStringExtra(MealReminders.EXTRA_TEXT).orEmpty()
        )
    }
}
