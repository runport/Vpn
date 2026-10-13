package com.example.config.collector

import com.example.config.parser.ConfigParser
import com.example.storage.AzadiRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.concurrent.TimeUnit

data class FetchProgress(
    val currentSourceName: String = "",
    val completedSources: Int = 0,
    val totalSources: Int = 0,
    val newConfigsFound: Int = 0,
    val totalConfigsProcessed: Int = 0,
    val errorCount: Int = 0,
    val isRunning: Boolean = false,
    val summaryMessage: String = ""
)

class ConfigSourceManager(private val repository: AzadiRepository) {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val maxResponseSizeBytes = 4 * 1024 * 1024 // 4 Megabytes limit

    fun fetchAllSourcesFlow(): Flow<FetchProgress> = flow {
        var activeSources = repository.getActiveSources()
        if (activeSources.isEmpty()) {
            repository.ensureDefaultSourcesPopulated()
            activeSources = repository.getActiveSources()
        }

        if (activeSources.isEmpty()) {
            emit(FetchProgress(summaryMessage = "هیچ منبع فعالی برای دریافت یافت نشد", isRunning = false))
            return@flow
        }

        var completed = 0
        var totalFound = 0
        var errors = 0

        emit(
            FetchProgress(
                currentSourceName = "شروع دریافت کانفیگ‌ها از منابع...",
                completedSources = 0,
                totalSources = activeSources.size,
                isRunning = true
            )
        )

        for (source in activeSources) {
            emit(
                FetchProgress(
                    currentSourceName = source.name,
                    completedSources = completed,
                    totalSources = activeSources.size,
                    newConfigsFound = totalFound,
                    errorCount = errors,
                    isRunning = true
                )
            )

            try {
                val fetchedContent = downloadContentSafely(source.url)
                val parsedItems = ConfigParser.parseSubscriptionContent(fetchedContent)

                var sourceNewCount = 0
                for (item in parsedItems) {
                    val id = repository.insertOrUpdateConfig(item, source.id)
                    if (id > 0) {
                        sourceNewCount++
                        totalFound++
                    }
                }

                // Also parse any proxies embedded in the content
                val parsedProxies = ConfigParser.parseProxiesContent(fetchedContent, source.name)
                if (parsedProxies.isNotEmpty()) {
                    repository.insertProxies(parsedProxies)
                }

                val statusMessage = if (parsedItems.isEmpty() && parsedProxies.isEmpty()) {
                    "پاسخ دریافت شد اما کانفیگ معتبری یافت نشد"
                } else {
                    "موفق: $sourceNewCount کانفیگ معتبر"
                }
                repository.updateSourceFetchStatus(source.id, statusMessage, parsedItems.size + parsedProxies.size)
            } catch (e: Exception) {
                errors++
                val err = "خطا در دریافت: ${e.message?.take(50) ?: "عدم اتصال"}"
                repository.updateSourceFetchStatus(source.id, err, 0)
            }

            completed++
            emit(
                FetchProgress(
                    currentSourceName = source.name,
                    completedSources = completed,
                    totalSources = activeSources.size,
                    newConfigsFound = totalFound,
                    errorCount = errors,
                    isRunning = true
                )
            )
        }

        val summary = "پایان جمع‌آوری: $totalFound کانفیگ جدید ثبت شد ($errors خطا)"
        emit(
            FetchProgress(
                currentSourceName = "تکمیل شد",
                completedSources = completed,
                totalSources = activeSources.size,
                newConfigsFound = totalFound,
                errorCount = errors,
                isRunning = false,
                summaryMessage = summary
            )
        )
    }.flowOn(Dispatchers.IO)

    suspend fun fetchSingleSource(sourceId: Long): Result<Int> {
        val sources = repository.getActiveSources()
        val source = sources.find { it.id == sourceId }
            ?: return Result.failure(IllegalArgumentException("منبع یافت نشد"))

        return try {
            val content = downloadContentSafely(source.url)
            val parsed = ConfigParser.parseSubscriptionContent(content)
            var count = 0
            for (item in parsed) {
                repository.insertOrUpdateConfig(item, source.id)
                count++
            }
            val proxies = ConfigParser.parseProxiesContent(content, source.name)
            if (proxies.isNotEmpty()) {
                repository.insertProxies(proxies)
                count += proxies.size
            }
            repository.updateSourceFetchStatus(source.id, "موفق: $count کانفیگ و پروکسی", count)
            Result.success(count)
        } catch (e: Exception) {
            val err = "خطا: ${e.message?.take(50)}"
            repository.updateSourceFetchStatus(source.id, err, 0)
            Result.failure(e)
        }
    }

    private fun downloadContentSafely(url: String): String {
        return try {
            executeDownload(url)
        } catch (e: Exception) {
            // Automatic CDN mirror fallback if raw.githubusercontent.com is blocked
            if (url.contains("raw.githubusercontent.com")) {
                val mirrorUrl = convertToJsDelivrMirror(url)
                if (mirrorUrl != null) {
                    try {
                        return executeDownload(mirrorUrl)
                    } catch (_: Exception) {}
                }
            }
            throw e
        }
    }

    private fun convertToJsDelivrMirror(rawUrl: String): String? {
        // Example: https://raw.githubusercontent.com/owner/repo/branch/path
        // -> https://cdn.jsdelivr.net/gh/owner/repo@branch/path
        val prefix = "https://raw.githubusercontent.com/"
        if (!rawUrl.startsWith(prefix)) return null
        val sub = rawUrl.substring(prefix.length)
        val parts = sub.split("/")
        if (parts.size >= 4) {
            val owner = parts[0]
            val repo = parts[1]
            val branch = parts[2]
            val path = parts.drop(3).joinToString("/")
            return "https://cdn.jsdelivr.net/gh/$owner/$repo@$branch/$path"
        }
        return null
    }

    private fun executeDownload(url: String): String {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "v2rayN/6.23 (Windows NT 10.0; Win64; x64) AzadiYar/1.0")
            .header("Accept", "*/*")
            .build()

        val response = httpClient.newCall(request).execute()
        if (!response.isSuccessful) {
            throw IllegalStateException("کد پاسخ HTTP ${response.code}")
        }

        val body = response.body ?: throw IllegalStateException("بدنه پاسخ خالی است")
        val inputStream: InputStream = body.byteStream()
        val buffer = ByteArray(8192)
        val output = ByteArrayOutputStream()
        var totalBytes = 0

        while (true) {
            val read = inputStream.read(buffer)
            if (read == -1) break
            totalBytes += read
            if (totalBytes > maxResponseSizeBytes) {
                throw IllegalStateException("اندازه پاسخ از سقف مجاز (۴ مگابایت) فراتر رفت")
            }
            output.write(buffer, 0, read)
        }

        return output.toString("UTF-8")
    }
}
