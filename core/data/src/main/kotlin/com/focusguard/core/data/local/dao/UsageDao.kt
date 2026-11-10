package com.focusguard.core.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.focusguard.core.data.local.entity.AppUsageSessionEntity
import com.focusguard.core.data.local.entity.BlockedAttemptEntity
import com.focusguard.core.data.local.entity.ExtensionRecordEntity
import kotlinx.coroutines.flow.Flow

data class DailyUsageSummaryRow(
    val packageName: String,
    val totalMinutes: Long,
)

data class WeeklyTotalRow(
    val date: Long,
    val totalMinutes: Long,
)

/**
 * Per-app usage rows are whole minutes (integer division), so an app opened for a few seconds
 * would otherwise come back as a "0m" row — listed on Home, and filling the weekly top-N slots
 * ahead of real usage. Apps below one minute are left out of per-app results entirely. Daily
 * totals (getWeeklyTotals) still sum every session, so seconds aren't lost from screen time.
 */
private const val MIN_LISTED_USAGE_MS = 60_000

@Dao
interface UsageDao {
    @Query(
        """
        SELECT packageName,
               CAST(SUM(endTime - startTime) / 60000 AS INTEGER) AS totalMinutes
        FROM app_usage_sessions
        WHERE date = :epochDay
        GROUP BY packageName
        HAVING SUM(endTime - startTime) >= $MIN_LISTED_USAGE_MS
        ORDER BY totalMinutes DESC
    """,
    )
    fun getDailyUsage(epochDay: Long): Flow<List<DailyUsageSummaryRow>>

    @Query(
        """
        SELECT date,
               CAST(SUM(endTime - startTime) / 60000 AS INTEGER) AS totalMinutes
        FROM app_usage_sessions
        WHERE date >= :startEpochDay AND date <= :endEpochDay
        GROUP BY date
        ORDER BY date ASC
    """,
    )
    fun getWeeklyTotals(
        startEpochDay: Long,
        endEpochDay: Long,
    ): Flow<List<WeeklyTotalRow>>

    @Query(
        """
        SELECT packageName,
               CAST(SUM(endTime - startTime) / 60000 AS INTEGER) AS totalMinutes
        FROM app_usage_sessions
        WHERE date >= :startEpochDay AND date <= :endEpochDay
        GROUP BY packageName
        HAVING SUM(endTime - startTime) >= $MIN_LISTED_USAGE_MS
        ORDER BY totalMinutes DESC
        LIMIT :limit
    """,
    )
    fun getWeeklyAppTotals(
        startEpochDay: Long,
        endEpochDay: Long,
        limit: Int,
    ): Flow<List<DailyUsageSummaryRow>>

    @Query(
        """
        SELECT CAST(COALESCE(SUM(endTime - startTime), 0) / 60000 AS INTEGER)
        FROM app_usage_sessions
        WHERE packageName = :packageName AND date = :epochDay
    """,
    )
    suspend fun getTodayUsageMinutes(
        packageName: String,
        epochDay: Long,
    ): Long

    @Insert
    suspend fun insertSession(entity: AppUsageSessionEntity)

    @Query("SELECT COUNT(*) FROM blocked_attempts WHERE date = :epochDay")
    fun getBlockedAttempts(epochDay: Long): Flow<Int>

    @Insert
    suspend fun insertBlockedAttempt(entity: BlockedAttemptEntity)

    @Query(
        """
        SELECT COUNT(*) FROM extension_records
        WHERE packageName = :packageName AND date = :epochDay
    """,
    )
    fun countExtension(
        packageName: String,
        epochDay: Long,
    ): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun recordExtension(entity: ExtensionRecordEntity)

    @Query("DELETE FROM app_usage_sessions WHERE date < :beforeEpochDay")
    suspend fun deleteSessionsBefore(beforeEpochDay: Long): Int

    @Query("DELETE FROM blocked_attempts WHERE date < :beforeEpochDay")
    suspend fun deleteBlockedAttemptsBefore(beforeEpochDay: Long): Int

    @Query("DELETE FROM extension_records WHERE date < :beforeEpochDay")
    suspend fun deleteExtensionsBefore(beforeEpochDay: Long): Int

    /**
     * Drops everything dated before [beforeEpochDay] in one transaction. Without this every poll
     * and every block added rows forever, while the app only ever reads the last week or so.
     */
    @Transaction
    suspend fun pruneBefore(beforeEpochDay: Long) {
        deleteSessionsBefore(beforeEpochDay)
        deleteBlockedAttemptsBefore(beforeEpochDay)
        deleteExtensionsBefore(beforeEpochDay)
    }
}
