package com.docscan.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.docscan.data.db.AppDatabase
import com.docscan.data.db.QrHistoryDao
import com.docscan.data.model.ParsedBarcode
import com.docscan.data.model.QrHistoryEntity
import kotlinx.coroutines.flow.Flow

class QrHistoryRepository(
    private val context: Context,
    private val qrHistoryDao: QrHistoryDao = AppDatabase.getInstance(context).qrHistoryDao()
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("qr_scanner_prefs", Context.MODE_PRIVATE)

    var isHistorySaveEnabled: Boolean
        get() = prefs.getBoolean(KEY_SAVE_HISTORY, true)
        set(value) = prefs.edit().putBoolean(KEY_SAVE_HISTORY, value).apply()

    var isScanSoundEnabled: Boolean
        get() = prefs.getBoolean(KEY_SCAN_SOUND, true)
        set(value) = prefs.edit().putBoolean(KEY_SCAN_SOUND, value).apply()

    var isScanVibrateEnabled: Boolean
        get() = prefs.getBoolean(KEY_SCAN_VIBRATE, true)
        set(value) = prefs.edit().putBoolean(KEY_SCAN_VIBRATE, value).apply()

    val allHistory: Flow<List<QrHistoryEntity>> = qrHistoryDao.getAllHistory()

    fun searchHistory(query: String): Flow<List<QrHistoryEntity>> =
        qrHistoryDao.searchHistory(query)

    suspend fun saveScan(parsed: ParsedBarcode): Long {
        if (!isHistorySaveEnabled) return 0L
        val entity = QrHistoryEntity(
            rawValue = parsed.rawValue,
            displayValue = parsed.displayValue,
            format = parsed.format,
            formatName = parsed.formatName,
            valueType = parsed.valueType.name,
            title = parsed.title,
            subtitle = parsed.subtitle,
            timestamp = parsed.timestamp
        )
        return qrHistoryDao.insertHistory(entity)
    }

    suspend fun deleteHistory(id: Long) {
        qrHistoryDao.deleteById(id)
    }

    suspend fun clearAllHistory() {
        qrHistoryDao.clearAll()
    }

    companion object {
        private const val KEY_SAVE_HISTORY = "key_save_history"
        private const val KEY_SCAN_SOUND = "key_scan_sound"
        private const val KEY_SCAN_VIBRATE = "key_scan_vibrate"
    }
}
