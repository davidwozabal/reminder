package com.wozabal.reminder.data

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Serializable
data class BackupData(
    val activities: List<BackupActivity> = emptyList(),
    val completions: List<BackupCompletion> = emptyList()
)

@Serializable
data class BackupActivity(
    val id: Long,
    val name: String,
    val dueHour: Int,
    val dueMinute: Int,
    val recurrenceType: String,
    val recurrenceDays: Int,
    val enabled: Boolean,
    val createdAt: String = ""
)

@Serializable
data class BackupCompletion(
    val activityId: Long,
    val date: String,
    val status: String
)

class ReminderRepository(private val db: ReminderDatabase, private val context: Context) {
    private val activityDao = db.activityDao()
    private val completionDao = db.completionDao()
    private val json = Json { prettyPrint = true }
    private val backupFile = File(context.filesDir, "reminder_backup.json")
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    fun getAllActivities(): Flow<List<ActivityEntity>> = activityDao.getAll()

    suspend fun getActivity(id: Long): ActivityEntity? = activityDao.getById(id)

    suspend fun insertActivity(activity: ActivityEntity): Long {
        val id = activityDao.insert(activity)
        backupToFile()
        return id
    }

    suspend fun updateActivity(activity: ActivityEntity) {
        activityDao.update(activity)
        backupToFile()
    }

    suspend fun deleteActivity(activity: ActivityEntity) {
        activityDao.delete(activity)
        backupToFile()
    }

    suspend fun getCompletionsForDate(date: String): List<CompletionEntity> =
        completionDao.getByDate(date)

    suspend fun getCompletionsForRange(startDate: String, endDate: String): List<CompletionEntity> =
        completionDao.getByDateRange(startDate, endDate)

    suspend fun getCompletionsForActivityInRange(activityId: Long, startDate: String, endDate: String): List<CompletionEntity> =
        completionDao.getByActivityAndRange(activityId, startDate, endDate)

    suspend fun markActivity(activityId: Long, date: String, status: String) {
        val existing = completionDao.getByDate(date).find { it.activityId == activityId }
        if (existing != null) {
            completionDao.insert(existing.copy(status = status))
        } else {
            completionDao.insert(CompletionEntity(activityId = activityId, date = date, status = status))
        }
        backupToFile()
    }

    suspend fun unmarkActivity(activityId: Long, date: String) {
        completionDao.deleteByActivityAndDate(activityId, date)
        backupToFile()
    }

    // Get activities that are active on a specific date (considering recurrence)
    suspend fun getActiveActivitiesForDate(date: LocalDate): List<ActivityEntity> {
        val all = activityDao.getEnabled()
        return all.filter { isActiveOnDate(it, date) }
    }

    private fun isActiveOnDate(activity: ActivityEntity, date: LocalDate): Boolean {
        return when (activity.recurrenceType) {
            "DAILY" -> true
            "WEEKLY" -> {
                val dayBit = 1 shl (date.dayOfWeek.value - 1) // Monday=1 → bit 0
                (activity.recurrenceDays and dayBit) != 0
            }
            else -> false
        }
    }

    // Backup / Restore
    suspend fun backupToFile() {
        try {
            val activities = activityDao.getAllList().map { a ->
                BackupActivity(
                    id = a.id, name = a.name, dueHour = a.dueHour,
                    dueMinute = a.dueMinute, recurrenceType = a.recurrenceType,
                    recurrenceDays = a.recurrenceDays, enabled = a.enabled
                )
            }
            // Get all completions - we need a snapshot
            val allCompletions = completionDao.getByDateRange("2000-01-01", "2099-12-31")
            val completions = allCompletions.map { c ->
                BackupCompletion(activityId = c.activityId, date = c.date, status = c.status)
            }
            backupFile.writeText(json.encodeToString(BackupData(activities, completions)))
        } catch (_: Exception) {}
    }

    suspend fun restoreFromBackupIfNeeded() {
        if (!backupFile.exists()) return
        try {
            val data = json.decodeFromString<BackupData>(backupFile.readText())
            val existingActivities = activityDao.getAllList()
            if (existingActivities.isEmpty() && data.activities.isNotEmpty()) {
                for (a in data.activities) {
                    activityDao.insert(ActivityEntity(
                        id = a.id, name = a.name, dueHour = a.dueHour,
                        dueMinute = a.dueMinute, recurrenceType = a.recurrenceType,
                        recurrenceDays = a.recurrenceDays, enabled = a.enabled,
                        createdAt = a.createdAt
                    ))
                }
                for (c in data.completions) {
                    completionDao.insert(CompletionEntity(
                        activityId = c.activityId, date = c.date, status = c.status
                    ))
                }
            }
        } catch (_: Exception) {}
    }
}
