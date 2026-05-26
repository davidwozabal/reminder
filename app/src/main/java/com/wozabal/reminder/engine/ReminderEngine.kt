package com.wozabal.reminder.engine

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import com.wozabal.reminder.data.ActivityEntity
import com.wozabal.reminder.service.EscalationReceiver
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

object ReminderEngine {
    private const val ESCALATION_INTERVAL_MINUTES = 30L
    private const val ESCALATION_START_BEFORE_HOURS = 1L

    /**
     * Schedule escalating reminders for all active activities due today.
     * First alarm fires 1 hour before due time, then repeats every 30 minutes
     * until end of day.
     */
    fun scheduleDailyReminders(context: Context, activities: List<ActivityEntity>) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val today = LocalDate.now()
        val zone = ZoneId.systemDefault()

        for (activity in activities) {
            // First reminder: 1 hour before due time
            val dueTime = ZonedDateTime.of(today.year, today.monthValue, today.dayOfMonth,
                activity.dueHour, activity.dueMinute, 0, 0, zone)
            val firstReminder = dueTime.minusHours(ESCALATION_START_BEFORE_HOURS)

            // If first reminder is already in the past, start from now + ESCALATION_INTERVAL
            val now = ZonedDateTime.now(zone)
            val startTime = if (firstReminder.isBefore(now)) {
                val minutesPast = java.time.Duration.between(firstReminder, now).toMinutes()
                val intervalsPast = minutesPast / ESCALATION_INTERVAL_MINUTES
                firstReminder.plusMinutes((intervalsPast + 1) * ESCALATION_INTERVAL_MINUTES)
            } else {
                firstReminder
            }

            // Schedule the first alarm
            val intent = Intent(context, EscalationReceiver::class.java).apply {
                putExtra("activityId", activity.id)
                putExtra("activityName", activity.name)
                putExtra("dueHour", activity.dueHour)
                putExtra("dueMinute", activity.dueMinute)
                putExtra("date", today.toString())
            }

            val requestCode = (activity.id * 1000 + today.dayOfYear).toInt()
            val pendingIntent = PendingIntent.getBroadcast(
                context, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val triggerMs = startTime.toInstant().toEpochMilli()
            if (triggerMs > System.currentTimeMillis()) {
                try {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP, triggerMs, pendingIntent
                    )
                } catch (e: SecurityException) {
                    android.util.Log.w("ReminderEngine", "No exact alarm permission (scheduleDailyReminders), using inexact", e)
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP, triggerMs, pendingIntent
                    )
                }
            }
        }
    }

    /**
     * Schedule the next escalation alarm (called from EscalationReceiver after each trigger).
     */
    fun scheduleNextEscalation(context: Context, activityId: Long, activityName: String,
                                dueHour: Int, dueMinute: Int, dateStr: String) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val zone = ZoneId.systemDefault()
        val now = ZonedDateTime.now(zone)

        // End of day
        val date = LocalDate.parse(dateStr)
        val endOfDay = ZonedDateTime.of(date.year, date.monthValue, date.dayOfMonth,
            23, 59, 59, 0, zone)
        val nextReminder = now.plusMinutes(ESCALATION_INTERVAL_MINUTES)

        // Don't schedule if we're past end of day or past due time + some grace period
        if (nextReminder.isAfter(endOfDay)) return

        val intent = Intent(context, EscalationReceiver::class.java).apply {
            putExtra("activityId", activityId)
            putExtra("activityName", activityName)
            putExtra("dueHour", dueHour)
            putExtra("dueMinute", dueMinute)
            putExtra("date", dateStr)
        }

        val requestCode = (activityId * 1000 + date.dayOfYear).toInt()
        val pendingIntent = PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP, nextReminder.toInstant().toEpochMilli(), pendingIntent
            )
        } catch (e: SecurityException) {
            android.util.Log.w("ReminderEngine", "No exact alarm permission (scheduleNextEscalation), using inexact", e)
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP, nextReminder.toInstant().toEpochMilli(), pendingIntent
            )
        }
    }

    /**
     * Cancel all escalation alarms for a specific activity on a specific date.
     */
    fun cancelEscalation(context: Context, activityId: Long, date: LocalDate) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, EscalationReceiver::class.java)
        val requestCode = (activityId * 1000 + date.dayOfYear).toInt()
        val pendingIntent = PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }

    /**
     * Schedule the midnight cleanup alarm to reset/stop all reminders.
     */
    fun scheduleMidnightCleanup(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val zone = ZoneId.systemDefault()
        val now = ZonedDateTime.now(zone)
        val midnight = ZonedDateTime.of(now.year, now.monthValue, now.dayOfMonth,
            0, 0, 0, 0, zone).plusDays(1)

        val intent = Intent(context, EscalationReceiver::class.java).apply {
            putExtra("midnightCleanup", true)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context, 9999, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP, midnight.toInstant().toEpochMilli(), pendingIntent
            )
        } catch (e: SecurityException) {
            android.util.Log.w("ReminderEngine", "No exact alarm permission, using inexact", e)
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP, midnight.toInstant().toEpochMilli(), pendingIntent
            )
        }
    }
}
