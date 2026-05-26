package com.wozabal.reminder.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ActivityDao {
    @Query("SELECT * FROM activities ORDER BY dueHour ASC, dueMinute ASC")
    fun getAll(): Flow<List<ActivityEntity>>

    @Query("SELECT * FROM activities ORDER BY dueHour ASC, dueMinute ASC")
    suspend fun getAllList(): List<ActivityEntity>

    @Query("SELECT * FROM activities WHERE id = :id")
    suspend fun getById(id: Long): ActivityEntity?

    @Query("SELECT * FROM activities WHERE enabled = 1 ORDER BY dueHour ASC, dueMinute ASC")
    suspend fun getEnabled(): List<ActivityEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(activity: ActivityEntity): Long

    @Update
    suspend fun update(activity: ActivityEntity)

    @Delete
    suspend fun delete(activity: ActivityEntity)
}
