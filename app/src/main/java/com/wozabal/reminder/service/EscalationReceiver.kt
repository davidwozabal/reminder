package com.wozabal.reminder.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.wozabal.reminder.data.ReminderDatabase
import com.wozabal.reminder.engine.ReminderEngine
import java.time.LocalDate

class EscalationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Midnight cleanup — reset everything and reschedule for new day
        if (intent.getBooleanExtra("midnightCleanup", false)) {
            rescheduleForNewDay(context)
            return
        }

        val activityId = intent.getLongExtra("activityId", -1)
        val activityName = intent.getStringExtra("activityName") ?: return
        val dueHour = intent.getIntExtra("dueHour", 0)
        val dueMinute = intent.getIntExtra("dueMinute", 0)
        val dateStr = intent.getStringExtra("date") ?: return

        if (activityId < 0) return

        // Check if activity was already handled
        val db = ReminderDatabase.getInstance(context)
        val repo = com.wozabal.reminder.data.ReminderRepository(db, context)
        kotlinx.coroutines.runBlocking {
            val completions = repo.getCompletionsForDate(dateStr)
            val alreadyHandled = completions.any { it.activityId == activityId }
            if (alreadyHandled) return@runBlocking

            // Vibrate
            vibrate(context)

            // Schedule next escalation
            ReminderEngine.scheduleNextEscalation(
                context, activityId, activityName, dueHour, dueMinute, dateStr
            )
        }
    }

    private fun vibrate(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            val vibrator = vibratorManager.defaultVibrator
            vibrator.vibrate(VibrationEffect.createWaveform(
                longArrayOf(0, 500, 200, 500, 200, 500),
                intArrayOf(0, 255, 0, 255, 0, 255),
                -1
            ))
        } else {
            @Suppress("DEPRECATION")
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(
                    longArrayOf(0, 500, 200, 500, 200, 500),
                    intArrayOf(0, 255, 0, 255, 0, 255),
                    -1
                ))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(longArrayOf(0, 500, 200, 500, 200, 500), -1)
            }
        }
    }

    private fun rescheduleForNewDay(context: Context) {
        val db = ReminderDatabase.getInstance(context)
        val repo = com.wozabal.reminder.data.ReminderRepository(db, context)
        kotlinx.coroutines.runBlocking {
            val today = LocalDate.now()
            val activities = repo.getActiveActivitiesForDate(today)
            if (activities.isNotEmpty()) {
                ReminderEngine.scheduleDailyReminders(context, activities)
                ReminderForegroundService.start(context)
            }
            ReminderEngine.scheduleMidnightCleanup(context)
        }
    }
}
