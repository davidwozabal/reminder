package com.wozabal.reminder.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "completions",
    indices = [Index(value = ["activityId", "date"], unique = true)]
)
data class CompletionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val activityId: Long,
    val date: String,    // "YYYY-MM-DD"
    val status: String   // "DONE" or "MISSED"
)
