package com.wozabal.reminder

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ReminderApp : Application() {
    override fun onCreate() {
        super.onCreate()

        // Global exception handler to prevent crashes
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            android.util.Log.e("ReminderApp", "Uncaught exception on ${thread.name}", throwable)
        }

        // Restore data from backup if DB is empty (survives reinstall)
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = com.wozabal.reminder.data.ReminderDatabase.getInstance(this@ReminderApp)
                val repo = com.wozabal.reminder.data.ReminderRepository(db, this@ReminderApp)
                repo.restoreFromBackupIfNeeded()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
