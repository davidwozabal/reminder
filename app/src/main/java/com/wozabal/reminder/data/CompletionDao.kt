package com.wozabal.reminder.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface CompletionDao {
    @Query("SELECT * FROM completions WHERE date = :date ORDER BY activityId ASC")
    suspend fun getByDate(date: String): List<CompletionEntity>

    @Query("SELECT * FROM completions WHERE date BETWEEN :startDate AND :endDate ORDER BY date ASC")
    suspend fun getByDateRange(startDate: String, endDate: String): List<CompletionEntity>

    @Query("SELECT * FROM completions WHERE activityId = :activityId AND date BETWEEN :startDate AND :endDate")
    suspend fun getByActivityAndRange(activityId: Long, startDate: String, endDate: String): List<CompletionEntity>

    @Query("SELECT COUNT(*) FROM completions WHERE date BETWEEN :startDate AND :endDate AND status = :status")
    suspend fun countByStatusInRange(startDate: String, endDate: String, status: String): Int

    @Query("SELECT * FROM completions")
    fun getAll(): Flow<List<CompletionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(completion: CompletionEntity)

    @Query("DELETE FROM completions WHERE activityId = :activityId AND date = :date")
    suspend fun deleteByActivityAndDate(activityId: Long, date: String)
}
