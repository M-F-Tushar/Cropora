package com.cropora.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ScanDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertScan(record: ScanRecord): Long

    @Query("SELECT * FROM scan_history ORDER BY timestamp DESC, id DESC")
    fun observeAllScans(): Flow<List<ScanRecord>>

    @Query("SELECT * FROM scan_history WHERE id = :id LIMIT 1")
    suspend fun getScanById(id: Long): ScanRecord?

    @Query("DELETE FROM scan_history WHERE id = :id")
    suspend fun deleteScanById(id: Long): Int
}
