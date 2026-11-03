package com.docscan.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.docscan.data.model.QrHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface QrHistoryDao {
    @Query("SELECT * FROM qr_scan_history ORDER BY timestamp DESC")
    fun getAllHistory(): Flow<List<QrHistoryEntity>>

    @Query("SELECT * FROM qr_scan_history WHERE rawValue LIKE '%' || :query || '%' OR title LIKE '%' || :query || '%' OR subtitle LIKE '%' || :query || '%' ORDER BY timestamp DESC")
    fun searchHistory(query: String): Flow<List<QrHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(item: QrHistoryEntity): Long

    @Query("DELETE FROM qr_scan_history WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM qr_scan_history")
    suspend fun clearAll()
}
