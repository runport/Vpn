package com.example.testing

import com.example.core.model.ConfigItem
import com.example.core.model.ProxyItem
import com.example.core.model.TestStatus
import com.example.storage.AzadiRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.atomic.AtomicInteger
import javax.net.ssl.SSLSocketFactory

data class ConfigTestResult(
    val configId: Long,
    val status: TestStatus,
    val pingMs: Long,
    val score: Int,
    val errorMessage: String? = null
)

class ConfigTester(private val repository: AzadiRepository) {

    /**
     * Executes single multi-stage testing on a given configuration.
     */
    suspend fun testConfig(config: ConfigItem): ConfigTestResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        var socket: Socket? = null

        try {
            socket = Socket()
            socket.soTimeout = 2500
            val endpoint = InetSocketAddress(config.server, config.port)
            socket.connect(endpoint, 2500)
            val tcpLatency = System.currentTimeMillis() - startTime

            var finalPing = tcpLatency
            var finalStatus = TestStatus.REACHABLE_ENDPOINT

            if (config.security.equals("tls", ignoreCase = true) || config.security.equals("reality", ignoreCase = true)) {
                try {
                    val sslFactory = SSLSocketFactory.getDefault() as SSLSocketFactory
                    val sslSocket = sslFactory.createSocket(socket, config.sni ?: config.server, config.port, true) as javax.net.ssl.SSLSocket
                    sslSocket.soTimeout = 2000
                    sslSocket.startHandshake()
                    finalPing = System.currentTimeMillis() - startTime
                    finalStatus = TestStatus.TUNNEL_CONNECTED
                    sslSocket.close()
                } catch (_: Exception) {
                    finalStatus = TestStatus.REACHABLE_ENDPOINT
                }
            }

            val score = calculateQualityScore(finalPing, isSuccess = true, config.successCount + 1, config.failureCount)

            repository.updateTestResult(
                id = config.id,
                status = finalStatus,
                pingMs = finalPing,
                score = score,
                errorMessage = null,
                isSuccess = true
            )

            ConfigTestResult(
                configId = config.id,
                status = finalStatus,
                pingMs = finalPing,
                score = score
            )
        } catch (e: Exception) {
            val errorMsg = e.message ?: "اتصال با خطا مواجه شد"
            repository.updateTestResult(
                id = config.id,
                status = TestStatus.FAILED,
                pingMs = -1,
                score = 0,
                errorMessage = errorMsg,
                isSuccess = false
            )

            ConfigTestResult(
                configId = config.id,
                status = TestStatus.FAILED,
                pingMs = -1,
                score = 0,
                errorMessage = errorMsg
            )
        } finally {
            try {
                socket?.close()
            } catch (_: Exception) {}
        }
    }

    /**
     * High-speed parallel testing engine for thousands of configs (e.g. 4000 configs):
     * - Uses 40 parallel concurrent workers
     * - Fast non-blocking socket connect with 1400ms timeout
     * - Batch updates to database every 40 items to prevent SQLite disk locking
     */
    fun testBatchHighSpeedFlow(configs: List<ConfigItem>, concurrency: Int = 40): Flow<Pair<Int, Int>> = flow {
        val total = configs.size
        if (total == 0) {
            emit(Pair(0, 0))
            return@flow
        }

        emit(Pair(0, total))
        val completedCount = AtomicInteger(0)
        val semaphore = Semaphore(concurrency)
        val buffer = java.util.Collections.synchronizedList(mutableListOf<ConfigItem>())

        coroutineScope {
            configs.chunked(concurrency * 2).forEach { chunk ->
                val jobs = chunk.map { config ->
                    async(Dispatchers.IO) {
                        semaphore.withPermit {
                            val updated = testConfigDirect(config)
                            buffer.add(updated)
                            val completed = completedCount.incrementAndGet()
                            completed
                        }
                    }
                }

                jobs.awaitAll()

                // Flush batch to database
                val toFlush = mutableListOf<ConfigItem>()
                synchronized(buffer) {
                    toFlush.addAll(buffer)
                    buffer.clear()
                }
                if (toFlush.isNotEmpty()) {
                    repository.updateConfigsBatch(toFlush)
                }

                emit(Pair(completedCount.get().coerceAtMost(total), total))
            }
        }

        // Final flush if any remaining
        val remaining = mutableListOf<ConfigItem>()
        synchronized(buffer) {
            remaining.addAll(buffer)
            buffer.clear()
        }
        if (remaining.isNotEmpty()) {
            repository.updateConfigsBatch(remaining)
        }

        emit(Pair(total, total))
    }.flowOn(Dispatchers.IO)

    /**
     * Fast direct socket test without individual DB writes (for high-speed batching).
     */
    private fun testConfigDirect(config: ConfigItem): ConfigItem {
        val startTime = System.currentTimeMillis()
        var socket: Socket? = null

        return try {
            socket = Socket()
            socket.soTimeout = 1400
            val endpoint = InetSocketAddress(config.server, config.port)
            socket.connect(endpoint, 1400)
            val tcpLatency = System.currentTimeMillis() - startTime

            var finalPing = tcpLatency
            var finalStatus = TestStatus.REACHABLE_ENDPOINT

            if (config.security.equals("tls", ignoreCase = true) || config.security.equals("reality", ignoreCase = true)) {
                try {
                    val sslFactory = SSLSocketFactory.getDefault() as SSLSocketFactory
                    val sslSocket = sslFactory.createSocket(socket, config.sni ?: config.server, config.port, true) as javax.net.ssl.SSLSocket
                    sslSocket.soTimeout = 1200
                    sslSocket.startHandshake()
                    finalPing = System.currentTimeMillis() - startTime
                    finalStatus = TestStatus.TUNNEL_CONNECTED
                    sslSocket.close()
                } catch (_: Exception) {
                    finalStatus = TestStatus.REACHABLE_ENDPOINT
                }
            }

            val score = calculateQualityScore(finalPing, isSuccess = true, config.successCount + 1, config.failureCount)
            config.copy(
                testStatus = finalStatus,
                pingMs = finalPing,
                qualityScore = score,
                lastTestTime = System.currentTimeMillis(),
                lastSuccessTime = System.currentTimeMillis(),
                successCount = config.successCount + 1,
                lastErrorMessage = null
            )
        } catch (e: Exception) {
            config.copy(
                testStatus = TestStatus.FAILED,
                pingMs = -1,
                qualityScore = 0,
                lastTestTime = System.currentTimeMillis(),
                failureCount = config.failureCount + 1,
                lastErrorMessage = e.message ?: "عدم اتصال"
            )
        } finally {
            try {
                socket?.close()
            } catch (_: Exception) {}
        }
    }

    /**
     * High-speed parallel proxy testing.
     */
    fun testProxiesBatchFlow(proxies: List<ProxyItem>, concurrency: Int = 40): Flow<Pair<Int, Int>> = flow {
        val total = proxies.size
        if (total == 0) {
            emit(Pair(0, 0))
            return@flow
        }

        emit(Pair(0, total))
        val completedCount = AtomicInteger(0)
        val semaphore = Semaphore(concurrency)
        val buffer = java.util.Collections.synchronizedList(mutableListOf<ProxyItem>())

        coroutineScope {
            proxies.chunked(concurrency * 2).forEach { chunk ->
                val jobs = chunk.map { proxy ->
                    async(Dispatchers.IO) {
                        semaphore.withPermit {
                            val updated = testProxyDirect(proxy)
                            buffer.add(updated)
                            val completed = completedCount.incrementAndGet()
                            completed
                        }
                    }
                }

                jobs.awaitAll()

                val toFlush = mutableListOf<ProxyItem>()
                synchronized(buffer) {
                    toFlush.addAll(buffer)
                    buffer.clear()
                }
                if (toFlush.isNotEmpty()) {
                    repository.updateProxiesBatch(toFlush)
                }

                emit(Pair(completedCount.get().coerceAtMost(total), total))
            }
        }

        emit(Pair(total, total))
    }.flowOn(Dispatchers.IO)

    private fun testProxyDirect(proxy: ProxyItem): ProxyItem {
        val startTime = System.currentTimeMillis()
        var socket: Socket? = null

        return try {
            socket = Socket()
            socket.soTimeout = 1400
            val endpoint = InetSocketAddress(proxy.host, proxy.port)
            socket.connect(endpoint, 1400)
            val latency = System.currentTimeMillis() - startTime
            proxy.copy(
                pingMs = latency,
                isHealthy = true,
                lastChecked = System.currentTimeMillis()
            )
        } catch (_: Exception) {
            proxy.copy(
                pingMs = -1,
                isHealthy = false,
                lastChecked = System.currentTimeMillis()
            )
        } finally {
            try {
                socket?.close()
            } catch (_: Exception) {}
        }
    }

    companion object {
        fun calculateQualityScore(pingMs: Long, isSuccess: Boolean, successCount: Int, failureCount: Int): Int {
            if (!isSuccess || pingMs < 0) return 0

            var score = 50

            when {
                pingMs <= 150 -> score += 40
                pingMs <= 300 -> score += 30
                pingMs <= 500 -> score += 20
                pingMs <= 800 -> score += 10
                pingMs > 1200 -> score -= 20
            }

            val totalTests = successCount + failureCount
            if (totalTests > 0) {
                val successRatio = successCount.toFloat() / totalTests.toFloat()
                score += (successRatio * 20).toInt()
            }

            return score.coerceIn(5, 100)
        }
    }
}
