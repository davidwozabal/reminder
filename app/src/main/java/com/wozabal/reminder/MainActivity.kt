package com.wozabal.reminder

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.wozabal.reminder.data.ReminderDatabase
import com.wozabal.reminder.engine.ReminderEngine
import com.wozabal.reminder.service.ReminderForegroundService
import com.wozabal.reminder.ui.CalendarScreen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate

class MainActivity : ComponentActivity() {
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startReminderService()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val db = try {
            ReminderDatabase.getInstance(this)
        } catch (e: Exception) {
            e.printStackTrace()
            return
        }

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    CalendarScreen(
                        db = db,
                        onRequestNotificationPermission = { requestNotificationPermission() }
                    )
                }
            }
        }

        // Start service if permission already granted
        if (hasNotificationPermission()) {
            startReminderService()
        } else {
            requestNotificationPermission()
        }
    }

    private fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            startReminderService()
        }
    }

    private fun startReminderService() {
        val db = ReminderDatabase.getInstance(this)
        val repo = com.wozabal.reminder.data.ReminderRepository(db, this)
        CoroutineScope(Dispatchers.IO).launch {
            val today = LocalDate.now()
            val activities = repo.getActiveActivitiesForDate(today)
            if (activities.isNotEmpty()) {
                ReminderEngine.scheduleDailyReminders(this@MainActivity, activities)
                ReminderForegroundService.start(this@MainActivity)
            }
            ReminderEngine.scheduleMidnightCleanup(this@MainActivity)
        }
    }
}
