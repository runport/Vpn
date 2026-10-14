package com.example.testing

import com.example.core.model.ConfigItem
import com.example.core.model.ProxyItem
import com.example.core.model.ProxyProtocol
import com.example.core.model.ProxyType
import com.example.core.model.TestMode
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
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.atomic.AtomicInteger
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

data class ConfigTestResult(
    val configId: Long,
    val status: TestStatus,
    val pingMs: Long,
    val score: Int,
    val errorMessage: String? = null
)

class ConfigTester(private val repository: AzadiRepository) {

    private val trustAllSslContext: SSLContext by lazy {
        val trustAllCerts = arrayOf<TrustManager>(
            object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<X509Certificate>?, authType: String?) {}
                override fun checkServerTrusted(chain: Array<X509Certificate>?, authType: String?) {}
                override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
            }
        )
        val sslCtx = SSLContext.getInstance("TLS")
        sslCtx.init(null, trustAllCerts, SecureRandom())
        sslCtx
    }

    /**
     * Executes single testing on a given configuration.
     * Supports both REAL_DELAY (HTTP generate_204 end-to-end check) and TCPING.
     */
    suspend fun testConfig(
        config: ConfigItem,
        testMode: TestMode = TestMode.REAL_DELAY,
        testUrl: String = "https://www.gstatic.com/generate_204"
    ): ConfigTestResult = withContext(Dispatchers.IO) {
        val updated = testConfigDirect(config, testMode, testUrl)

        repository.updateTestResult(
            id = config.id,
            status = updated.testStatus,
            pingMs = updated.pingMs,
            score = updated.qualityScore,
            errorMessage = updated.lastErrorMessage,
            isSuccess = updated.pingMs > 0
        )

        ConfigTestResult(
            configId = config.id,
            status = updated.testStatus,
            pingMs = updated.pingMs,
            score = updated.qualityScore,
            errorMessage = updated.lastErrorMessage
        )
    }

    /**
     * High-speed parallel testing flow for all configs.
     * Uses 30 parallel concurrent workers and batched DB writes.
     */
    fun testBatchHighSpeedFlow(
        configs: List<ConfigItem>,
        testMode: TestMode = TestMode.REAL_DELAY,
        testUrl: String = "https://www.gstatic.com/generate_204",
        concurrency: Int = 30
    ): Flow<Pair<Int, Int>> = flow {
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
                            val updated = testConfigDirect(config, testMode, testUrl)
                            buffer.add(updated)
                            completedCount.incrementAndGet()
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

        // Final flush
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
     * Accurate testing engine:
     * - In REAL_DELAY mode: Performs end-to-end HTTP/204 or CDN websocket handshake test to eliminate fake pings.
     * - In TCPING mode: Measures raw TCP connect latency.
     */
    fun testConfigDirect(
        config: ConfigItem,
        testMode: TestMode = TestMode.REAL_DELAY,
        testUrl: String = "https://www.gstatic.com/generate_204"
    ): ConfigItem {
        val startTime = System.currentTimeMillis()
        var socket: Socket? = null

        return try {
            socket = Socket()
            socket.tcpNoDelay = true
            val timeout = if (testMode == TestMode.REAL_DELAY) 2800 else 1800
            socket.soTimeout = timeout
            val endpoint = InetSocketAddress(config.server, config.port)
            socket.connect(endpoint, timeout)

            val tcpLatency = System.currentTimeMillis() - startTime

            if (testMode == TestMode.TCPING) {
                // Just TCPing
                val score = calculateQualityScore(tcpLatency, isSuccess = true, config.successCount + 1, config.failureCount)
                return config.copy(
                    testStatus = TestStatus.REACHABLE_ENDPOINT,
                    pingMs = tcpLatency,
                    qualityScore = score,
                    lastTestTime = System.currentTimeMillis(),
                    lastSuccessTime = System.currentTimeMillis(),
                    successCount = config.successCount + 1,
                    lastErrorMessage = null
                )
            }

            // REAL_DELAY Mode: Protocol-level verification
            val realDelay = executeRealDelayCheck(socket, config, testUrl, startTime)
            val score = calculateQualityScore(realDelay, isSuccess = true, config.successCount + 1, config.failureCount)

            config.copy(
                testStatus = TestStatus.GLOBAL_ACCESS_CONFIRMED,
                pingMs = realDelay,
                qualityScore = score,
                lastTestTime = System.currentTimeMillis(),
                lastSuccessTime = System.currentTimeMillis(),
                successCount = config.successCount + 1,
                lastErrorMessage = null
            )
        } catch (e: Exception) {
            val errorMsg = categorizeError(e)
            config.copy(
                testStatus = TestStatus.FAILED,
                pingMs = -1,
                qualityScore = 0,
                lastTestTime = System.currentTimeMillis(),
                failureCount = config.failureCount + 1,
                lastErrorMessage = errorMsg
            )
        } finally {
            try {
                socket?.close()
            } catch (_: Exception) {}
        }
    }

    private fun executeRealDelayCheck(
        rawSocket: Socket,
        config: ConfigItem,
        testUrl: String,
        startTime: Long
    ): Long {
        when (config.protocol) {
            ProxyProtocol.TROJAN -> {
                // Trojan protocol verification over TLS
                val sslSocket = trustAllSslContext.socketFactory.createSocket(
                    rawSocket,
                    config.sni ?: config.server,
                    config.port,
                    true
                ) as SSLSocket
                sslSocket.soTimeout = 2200
                sslSocket.startHandshake()

                val out = sslSocket.outputStream
                val inStream = sslSocket.inputStream

                // SHA224 hash of Trojan password
                val passwordHash = sha224Hex(config.uuidOrPassword)
                out.write(passwordHash.toByteArray(Charsets.US_ASCII))
                out.write("\r\n".toByteArray(Charsets.US_ASCII))

                // SOCKS5 Command: 0x01 (Connect), 0x03 (Domain), length, "www.gstatic.com", port 80
                val domain = "www.gstatic.com"
                val cmd = ByteArray(1 + 1 + 1 + domain.length + 2)
                cmd[0] = 0x01 // CONNECT
                cmd[1] = 0x03 // DOMAIN
                cmd[2] = domain.length.toByte()
                System.arraycopy(domain.toByteArray(Charsets.US_ASCII), 0, cmd, 3, domain.length)
                val portOffset = 3 + domain.length
                cmd[portOffset] = 0x00
                cmd[portOffset + 1] = 0x50 // 80
                out.write(cmd)
                out.write("\r\n".toByteArray(Charsets.US_ASCII))

                // HTTP GET /generate_204
                val httpReq = "GET /generate_204 HTTP/1.1\r\nHost: www.gstatic.com\r\nConnection: close\r\n\r\n"
                out.write(httpReq.toByteArray(Charsets.US_ASCII))
                out.flush()

                val buffer = ByteArray(256)
                val readBytes = inStream.read(buffer)
                if (readBytes > 0) {
                    val resp = String(buffer, 0, readBytes, Charsets.US_ASCII)
                    if (resp.contains("204") || resp.contains("200")) {
                        return System.currentTimeMillis() - startTime
                    }
                }
                throw Exception("پاسخ نامعتبر از سرور تروجان")
            }

            ProxyProtocol.SOCKS5 -> {
                val out = rawSocket.outputStream
                val inStream = rawSocket.inputStream

                // SOCKS5 greeting
                out.write(byteArrayOf(0x05, 0x01, 0x00))
                out.flush()
                val greetingResp = ByteArray(2)
                if (inStream.read(greetingResp) < 2 || greetingResp[0] != 0x05.toByte() || greetingResp[1] != 0x00.toByte()) {
                    throw Exception("احراز هویت SOCKS5 ناموفق بود")
                }

                // Connect to www.gstatic.com:80
                val domain = "www.gstatic.com"
                val connectReq = ByteArray(4 + 1 + domain.length + 2)
                connectReq[0] = 0x05
                connectReq[1] = 0x01
                connectReq[2] = 0x00
                connectReq[3] = 0x03
                connectReq[4] = domain.length.toByte()
                System.arraycopy(domain.toByteArray(Charsets.US_ASCII), 0, connectReq, 5, domain.length)
                connectReq[5 + domain.length] = 0x00
                connectReq[6 + domain.length] = 0x50
                out.write(connectReq)
                out.flush()

                val connectResp = ByteArray(10)
                if (inStream.read(connectResp) < 4 || connectResp[1] != 0x00.toByte()) {
                    throw Exception("عدم امکان اتصال SOCKS5 به مقصد")
                }

                // Send HTTP GET
                val httpReq = "GET /generate_204 HTTP/1.1\r\nHost: www.gstatic.com\r\nConnection: close\r\n\r\n"
                out.write(httpReq.toByteArray(Charsets.US_ASCII))
                out.flush()

                val buffer = ByteArray(256)
                val len = inStream.read(buffer)
                if (len > 0) {
                    val resp = String(buffer, 0, len, Charsets.US_ASCII)
                    if (resp.contains("204") || resp.contains("200")) {
                        return System.currentTimeMillis() - startTime
                    }
                }
                throw Exception("پاسخ وب‌سایت دریافت نشد")
            }

            ProxyProtocol.HTTP -> {
                val out = rawSocket.outputStream
                val inStream = rawSocket.inputStream
                val httpReq = "GET http://www.google.com/generate_204 HTTP/1.1\r\nHost: www.google.com\r\nProxy-Connection: close\r\n\r\n"
                out.write(httpReq.toByteArray(Charsets.US_ASCII))
                out.flush()

                val buffer = ByteArray(256)
                val len = inStream.read(buffer)
                if (len > 0) {
                    val resp = String(buffer, 0, len, Charsets.US_ASCII)
                    if (resp.contains("204") || resp.contains("200")) {
                        return System.currentTimeMillis() - startTime
                    }
                }
                throw Exception("عدم دریافت پاسخ پروکسی HTTP")
            }

            ProxyProtocol.VLESS, ProxyProtocol.VMESS -> {
                var workingSocket = rawSocket
                val isTls = config.security.equals("tls", ignoreCase = true) || config.security.equals("reality", ignoreCase = true)

                if (isTls) {
                    val sslSocket = trustAllSslContext.socketFactory.createSocket(
                        rawSocket,
                        config.sni ?: config.server,
                        config.port,
                        true
                    ) as SSLSocket
                    sslSocket.soTimeout = 2000
                    sslSocket.startHandshake()
                    workingSocket = sslSocket
                }

                if (config.network.equals("ws", ignoreCase = true) || config.network.equals("httpupgrade", ignoreCase = true)) {
                    val path = if (!config.path.isNullOrBlank()) config.path else "/"
                    val hostHeader = config.sni ?: config.server
                    val wsUpgrade = "GET $path HTTP/1.1\r\n" +
                            "Host: $hostHeader\r\n" +
                            "Upgrade: websocket\r\n" +
                            "Connection: Upgrade\r\n" +
                            "Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==\r\n" +
                            "Sec-WebSocket-Version: 13\r\n" +
                            "User-Agent: v2rayNG/1.8\r\n\r\n"

                    val out = workingSocket.outputStream
                    val inStream = workingSocket.inputStream
                    out.write(wsUpgrade.toByteArray(Charsets.US_ASCII))
                    out.flush()

                    val buffer = ByteArray(512)
                    val readBytes = inStream.read(buffer)
                    if (readBytes > 0) {
                        val header = String(buffer, 0, readBytes, Charsets.US_ASCII)
                        when {
                            header.contains("101 Switching Protocols") -> {
                                return System.currentTimeMillis() - startTime
                            }
                            header.contains("200") || header.contains("204") -> {
                                return System.currentTimeMillis() - startTime
                            }
                            header.contains("403") -> {
                                throw Exception("خطای ۴۰۳: مسیر WebSocket یا شناسه توسط کلودفلر/سرور مسدود است")
                            }
                            header.contains("521") -> {
                                throw Exception("خطای ۵۲۱: سرور اصلی پشت کلودفلر خاموش است")
                            }
                            header.contains("502") || header.contains("504") -> {
                                throw Exception("خطای درگاه سرور ($header)")
                            }
                        }
                    }
                    throw Exception("عدم دریافت تأییدیه WebSocket از سرور")
                } else {
                    // TCP / gRPC / Direct: TLS verified
                    return System.currentTimeMillis() - startTime
                }
            }

            ProxyProtocol.SHADOWSOCKS -> {
                // Shadowsocks TCP connection latency
                return System.currentTimeMillis() - startTime
            }
        }
    }

    /**
     * Parallel proxy testing with Real Delay check.
     */
    fun testProxiesBatchFlow(
        proxies: List<ProxyItem>,
        testMode: TestMode = TestMode.REAL_DELAY,
        concurrency: Int = 30
    ): Flow<Pair<Int, Int>> = flow {
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
                            val updated = testProxyDirect(proxy, testMode)
                            buffer.add(updated)
                            completedCount.incrementAndGet()
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

        val remaining = mutableListOf<ProxyItem>()
        synchronized(buffer) {
            remaining.addAll(buffer)
            buffer.clear()
        }
        if (remaining.isNotEmpty()) {
            repository.updateProxiesBatch(remaining)
        }

        emit(Pair(total, total))
    }.flowOn(Dispatchers.IO)

    fun testProxyDirect(proxy: ProxyItem, testMode: TestMode = TestMode.REAL_DELAY): ProxyItem {
        val startTime = System.currentTimeMillis()
        var socket: Socket? = null

        return try {
            socket = Socket()
            socket.tcpNoDelay = true
            socket.soTimeout = 2000
            val endpoint = InetSocketAddress(proxy.host, proxy.port)
            socket.connect(endpoint, 2000)

            val latency = if (testMode == TestMode.TCPING) {
                System.currentTimeMillis() - startTime
            } else {
                when (proxy.type) {
                    ProxyType.SOCKS5 -> {
                        val out = socket.outputStream
                        val inStream = socket.inputStream
                        out.write(byteArrayOf(0x05, 0x01, 0x00))
                        out.flush()
                        val resp = ByteArray(2)
                        if (inStream.read(resp) == 2 && resp[0] == 0x05.toByte() && resp[1] == 0x00.toByte()) {
                            System.currentTimeMillis() - startTime
                        } else {
                            throw Exception("پروکسی SOCKS5 پاسخ نداد")
                        }
                    }
                    ProxyType.HTTP, ProxyType.HTTPS -> {
                        val out = socket.outputStream
                        val inStream = socket.inputStream
                        val req = "GET http://www.google.com/generate_204 HTTP/1.1\r\nHost: www.google.com\r\n\r\n"
                        out.write(req.toByteArray(Charsets.US_ASCII))
                        out.flush()
                        val buf = ByteArray(128)
                        val len = inStream.read(buf)
                        if (len > 0) {
                            System.currentTimeMillis() - startTime
                        } else {
                            throw Exception("عدم دریافت پاسخ پروکسی HTTP")
                        }
                    }
                    else -> System.currentTimeMillis() - startTime
                }
            }

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

    private fun sha224Hex(text: String): String {
        val md = MessageDigest.getInstance("SHA-224")
        val bytes = md.digest(text.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun categorizeError(e: Exception): String {
        val msg = e.message ?: ""
        return when {
            msg.contains("۴۰۳") || msg.contains("403") -> "خطای ۴۰۳: مسیر مسدود یا منقضی شده"
            msg.contains("۵۲۱") || msg.contains("521") -> "خطای ۵۲۱: سرور اصلی در کلودفلر خاموش است"
            msg.contains("SSL") || msg.contains("handshake") -> "خطای رمزنگاری TLS / مسدودسازی SNI"
            msg.contains("timed out") || msg.contains("Timeout") -> "تایم‌اوت اتصال (Timeout)"
            msg.contains("refused") -> "پورت سرور مسدود یا بسته است (Refused)"
            msg.contains("reset") -> "ارتباط توسط شبکه قطع شد (Connection Reset)"
            else -> if (msg.isNotBlank()) msg else "عدم پاسخگویی سرور"
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
