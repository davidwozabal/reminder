package com.wozabal.reminder.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "activities")
data class ActivityEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val dueHour: Int,        // 0–23
    val dueMinute: Int,      // 0–59
    val recurrenceType: String,  // "DAILY" or "WEEKLY"
    val recurrenceDays: Int,     // bitmask: 1=Mon 2=Tue 4=Wed 8=Thu 16=Fri 32=Sat 64=Sun
    val enabled: Boolean = true,
    val createdAt: String = ""   // yyyy-MM-dd — first day this activity exists
)
