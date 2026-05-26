package com.wozabal.reminder.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.wozabal.reminder.MainActivity
import com.wozabal.reminder.R
import com.wozabal.reminder.data.ActivityEntity
import com.wozabal.reminder.data.CompletionEntity
import com.wozabal.reminder.data.ReminderDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class ReminderForegroundService : Service() {
    companion object {
        const val CHANNEL_ID = "reminder_persistent"
        const val NOTIFICATION_ID = 1001
        const val ACTION_MARK_DONE = "com.wozabal.reminder.MARK_DONE"
        const val ACTION_MARK_MISSED = "com.wozabal.reminder.MARK_MISSED"
        const val ACTION_DISMISS_ALL = "com.wozabal.reminder.DISMISS_ALL"
        const val EXTRA_ACTIVITY_ID = "activityId"
        const val EXTRA_DATE = "date"

        fun start(context: Context) {
            val intent = Intent(context, ReminderForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ReminderForegroundService::class.java))
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_MARK_DONE) {
            val activityId = intent.getLongExtra(EXTRA_ACTIVITY_ID, -1)
            val date = intent.getStringExtra(EXTRA_DATE) ?: return START_STICKY
            if (activityId >= 0) {
                Handler(Looper.getMainLooper()).post {
                    GlobalScope.launch(Dispatchers.IO) {
                        markActivity(activityId, date, "DONE")
                    }
                }
            }
        } else if (intent?.action == ACTION_MARK_MISSED) {
            val activityId = intent.getLongExtra(EXTRA_ACTIVITY_ID, -1)
            val date = intent.getStringExtra(EXTRA_DATE) ?: return START_STICKY
            if (activityId >= 0) {
                Handler(Looper.getMainLooper()).post {
                    GlobalScope.launch(Dispatchers.IO) {
                        markActivity(activityId, date, "MISSED")
                    }
                }
            }
        } else if (intent?.action == ACTION_DISMISS_ALL) {
            val date = intent?.getStringExtra(EXTRA_DATE) ?: return START_STICKY
            Handler(Looper.getMainLooper()).post {
                GlobalScope.launch(Dispatchers.IO) {
                    markAllPendingAsMissed()
                }
            }
            stopSelf()
        } else {
            Handler(Looper.getMainLooper()).post {
                GlobalScope.launch(Dispatchers.IO) {
                    updateNotification()
                }
            }
        }

        return START_STICKY
    }

    private suspend fun updateNotification() {
        try {
            val today = java.time.LocalDate.now().toString()
            val db = ReminderDatabase.getInstance(this)
            val repo = com.wozabal.reminder.data.ReminderRepository(db, this)
            val activities = repo.getActiveActivitiesForDate(java.time.LocalDate.now())
            val completions = repo.getCompletionsForDate(today)

            val pendingActivities = activities.filter { activity ->
                completions.none { it.activityId == activity.id }
            }

            if (pendingActivities.isEmpty()) {
                stopSelf()
                return
            }

            val notification = buildNotification(pendingActivities, today)
            startForeground(NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            android.util.Log.e("ForegroundService", "Failed to update notification", e)
            stopSelf()
        }
    }

    private fun buildNotification(activities: List<ActivityEntity>, date: String): Notification {
        val openIntent = Intent(this, MainActivity::class.java).let {
            PendingIntent.getActivity(this, 0, it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("Today's Activities")
            .setContentText("${activities.size} pending")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(openIntent)
            .setStyle(NotificationCompat.BigTextStyle().bigText(
                activities.joinToString("\n") { a ->
                    val time = String.format("%02d:%02d", a.dueHour, a.dueMinute)
                    "$time — ${a.name}"
                }
            ))

        // Add "Done" action for each activity (max 4, which matches notification limit)
        for (activity in activities.take(4)) {
            val doneIntent = Intent(this, ReminderForegroundService::class.java).apply {
                action = ACTION_MARK_DONE
                putExtra(EXTRA_ACTIVITY_ID, activity.id)
                putExtra(EXTRA_DATE, date)
            }
            val donePending = PendingIntent.getService(
                this, (activity.id * 2).toInt(), doneIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(0, "✓ ${activity.name}", donePending)
        }

        // Dismiss All action
        if (activities.size > 1) {
            val dismissIntent = Intent(this, ReminderForegroundService::class.java).apply {
                action = ACTION_DISMISS_ALL
                putExtra(EXTRA_DATE, date)
            }
            val dismissPending = PendingIntent.getService(
                this, 99999, dismissIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(0, "✗ All Missed", dismissPending)
        }

        return builder.build()
    }

    private suspend fun markActivity(activityId: Long, date: String, status: String) {
        val db = ReminderDatabase.getInstance(this)
        val repo = com.wozabal.reminder.data.ReminderRepository(db, this)
        repo.markActivity(activityId, date, status)
        com.wozabal.reminder.engine.ReminderEngine.cancelEscalation(
            this, activityId, java.time.LocalDate.parse(date)
        )
    }

    private suspend fun markAllPendingAsMissed() {
        val db = ReminderDatabase.getInstance(this)
        val repo = com.wozabal.reminder.data.ReminderRepository(db, this)
        val today = java.time.LocalDate.now()
        val dateStr = today.toString()
        val activities = repo.getActiveActivitiesForDate(today)
        val completions = repo.getCompletionsForDate(dateStr)
        for (a in activities) {
            if (completions.none { it.activityId == a.id }) {
                repo.markActivity(a.id, dateStr, "MISSED")
                com.wozabal.reminder.engine.ReminderEngine.cancelEscalation(this, a.id, today)
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.channel_reminders),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.channel_reminders_desc)
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }
}
