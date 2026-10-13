package com.example.storage.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.storage.entities.ConfigEntity
import com.example.storage.entities.ConfigSourceCrossRef
import com.example.storage.entities.ConfigSourceEntity
import com.example.storage.entities.DiagnosticLogEntity
import com.example.storage.entities.ProxyEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ConfigDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertConfig(config: ConfigEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(configs: List<ConfigEntity>): List<Long>

    @Query("SELECT * FROM configs WHERE normalizedHash = :hash LIMIT 1")
    suspend fun findByHash(hash: String): ConfigEntity?

    @Query("SELECT * FROM configs WHERE id = :id LIMIT 1")
    suspend fun findById(id: Long): ConfigEntity?

    @Query("SELECT * FROM configs ORDER BY qualityScore DESC, pingMs ASC, id DESC")
    fun getAllConfigsFlow(): Flow<List<ConfigEntity>>

    @Query("SELECT * FROM configs ORDER BY qualityScore DESC, pingMs ASC, id DESC")
    suspend fun getAllConfigs(): List<ConfigEntity>

    @Query("SELECT * FROM configs WHERE testStatus IN ('GLOBAL_ACCESS_CONFIRMED', 'TUNNEL_CONNECTED', 'REACHABLE_ENDPOINT') ORDER BY qualityScore DESC, pingMs ASC")
    fun getVerifiedConfigsFlow(): Flow<List<ConfigEntity>>

    @Query("SELECT * FROM configs WHERE testStatus IN ('GLOBAL_ACCESS_CONFIRMED', 'TUNNEL_CONNECTED', 'REACHABLE_ENDPOINT') ORDER BY qualityScore DESC, pingMs ASC LIMIT :limit")
    suspend fun getBestVerifiedConfigs(limit: Int): List<ConfigEntity>

    @Query("SELECT * FROM configs WHERE protocol = :protocol ORDER BY qualityScore DESC")
    fun getConfigsByProtocolFlow(protocol: String): Flow<List<ConfigEntity>>

    @Update
    suspend fun updateConfig(config: ConfigEntity)

    @Update
    suspend fun updateAll(configs: List<ConfigEntity>)

    @Query("""
        UPDATE configs 
        SET testStatus = :status, 
            pingMs = :pingMs, 
            qualityScore = :score, 
            lastTestTime = :testTime, 
            lastSuccessTime = CASE WHEN :isSuccess = 1 THEN :testTime ELSE lastSuccessTime END,
            lastErrorMessage = :errorMessage,
            successCount = successCount + CASE WHEN :isSuccess = 1 THEN 1 ELSE 0 END,
            failureCount = failureCount + CASE WHEN :isSuccess = 0 THEN 1 ELSE 0 END
        WHERE id = :id
    """)
    suspend fun updateTestResult(
        id: Long,
        status: String,
        pingMs: Long,
        score: Int,
        testTime: Long,
        errorMessage: String?,
        isSuccess: Boolean
    )

    @Query("UPDATE configs SET isFavorite = :isFavorite WHERE id = :id")
    suspend fun setFavorite(id: Long, isFavorite: Boolean)

    @Delete
    suspend fun deleteConfig(config: ConfigEntity)

    @Query("DELETE FROM configs")
    suspend fun clearAll()

    @Query("DELETE FROM configs WHERE testStatus = 'FAILED'")
    suspend fun deleteFailedConfigs(): Int

    @Query("SELECT COUNT(*) FROM configs")
    suspend fun getCount(): Int

    @Query("SELECT COUNT(*) FROM configs WHERE testStatus = 'GLOBAL_ACCESS_CONFIRMED' OR testStatus = 'TUNNEL_CONNECTED' OR testStatus = 'REACHABLE_ENDPOINT'")
    suspend fun getHealthyCount(): Int

    @Query("SELECT COUNT(*) FROM configs WHERE testStatus = 'FAILED'")
    suspend fun getFailedCount(): Int

    @Query("SELECT COUNT(*) FROM configs WHERE testStatus = 'UNTESTED'")
    suspend fun getUntestedCount(): Int
}

@Dao
interface ConfigSourceDao {

    @Query("SELECT * FROM config_sources ORDER BY id ASC")
    fun getAllSourcesFlow(): Flow<List<ConfigSourceEntity>>

    @Query("SELECT * FROM config_sources ORDER BY id ASC")
    suspend fun getAllSources(): List<ConfigSourceEntity>

    @Query("SELECT * FROM config_sources WHERE isEnabled = 1")
    suspend fun getActiveSources(): List<ConfigSourceEntity>

    @Query("SELECT * FROM config_sources WHERE id = :id LIMIT 1")
    suspend fun getSourceById(id: Long): ConfigSourceEntity?

    @Query("SELECT * FROM config_sources WHERE url = :url LIMIT 1")
    suspend fun findByUrl(url: String): ConfigSourceEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSource(source: ConfigSourceEntity): Long

    @Update
    suspend fun updateSource(source: ConfigSourceEntity)

    @Delete
    suspend fun deleteSource(source: ConfigSourceEntity)

    @Query("UPDATE config_sources SET lastFetchTime = :time, lastFetchStatus = :status, configCount = :count WHERE id = :sourceId")
    suspend fun updateFetchStatus(sourceId: Long, time: Long, status: String, count: Int)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCrossRef(ref: ConfigSourceCrossRef)

    @Query("SELECT sourceId FROM config_source_cross_ref WHERE configId = :configId")
    suspend fun getSourcesForConfig(configId: Long): List<Long>

    @Query("SELECT COUNT(DISTINCT configId) FROM config_source_cross_ref WHERE sourceId = :sourceId")
    suspend fun getConfigCountForSource(sourceId: Long): Int

    @Query("SELECT COUNT(*) FROM config_sources")
    suspend fun getSourceCount(): Int

    @Query("DELETE FROM config_source_cross_ref WHERE sourceId = :sourceId")
    suspend fun clearCrossRefsForSource(sourceId: Long)
}

@Dao
interface DiagnosticDao {

    @Insert
    suspend fun insertLog(log: DiagnosticLogEntity): Long

    @Query("SELECT * FROM diagnostic_logs ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentLogsFlow(limit: Int): Flow<List<DiagnosticLogEntity>>

    @Query("DELETE FROM diagnostic_logs")
    suspend fun clearLogs()
}

@Dao
interface ProxyDao {

    @Query("SELECT * FROM proxies ORDER BY pingMs ASC, id DESC")
    fun getAllProxiesFlow(): Flow<List<ProxyEntity>>

    @Query("SELECT * FROM proxies ORDER BY pingMs ASC, id DESC")
    suspend fun getAllProxies(): List<ProxyEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertProxy(proxy: ProxyEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(proxies: List<ProxyEntity>): List<Long>

    @Update
    suspend fun updateProxy(proxy: ProxyEntity)

    @Update
    suspend fun updateAll(proxies: List<ProxyEntity>)

    @Delete
    suspend fun deleteProxy(proxy: ProxyEntity)

    @Query("DELETE FROM proxies")
    suspend fun clearAll()

    @Query("DELETE FROM proxies WHERE isHealthy = 0 AND (pingMs = -1 OR pingMs > 4000)")
    suspend fun deleteFailedProxies(): Int

    @Query("SELECT COUNT(*) FROM proxies")
    suspend fun getCount(): Int

    @Query("SELECT COUNT(*) FROM proxies WHERE isHealthy = 1")
    suspend fun getHealthyCount(): Int
}
