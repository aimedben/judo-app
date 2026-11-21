package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.model.WeightEntry
import kotlinx.coroutines.flow.Flow

@Dao
interface WeightEntryDao {
    @Query("SELECT * FROM weight_entries ORDER BY dateString DESC, timestamp DESC")
    fun allEntries(): Flow<List<WeightEntry>>

    @Query("SELECT * FROM weight_entries WHERE subjectType = :type AND subjectId = :id ORDER BY dateString DESC, timestamp DESC")
    fun entriesFor(type: String, id: Long): Flow<List<WeightEntry>>

    @Query("SELECT * FROM weight_entries WHERE subjectType = :type AND subjectId = :id ORDER BY dateString DESC, timestamp DESC LIMIT 1")
    suspend fun latestFor(type: String, id: Long): WeightEntry?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: WeightEntry): Long

    @Query("DELETE FROM weight_entries WHERE id = :id")
    suspend fun deleteById(id: Long)
}