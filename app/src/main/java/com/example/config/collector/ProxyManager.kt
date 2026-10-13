package com.example.config.collector

import com.example.config.parser.ConfigParser
import com.example.storage.AzadiRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

data class ProxyFetchProgress(
    val currentSource: String = "",
    val totalFound: Int = 0,
    val isRunning: Boolean = false,
    val message: String = ""
)

class ProxyManager(private val repository: AzadiRepository) {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val proxySources = listOf(
        Pair("مخزن پروکسی تلگرام و داخلی ایران", "https://raw.githubusercontent.com/Bardiafa/Proxy-Telegram/main/telegram_proxies.txt"),
        Pair("مخزن بین‌المللی TheSpeedX SOCKS5", "https://raw.githubusercontent.com/TheSpeedX/PROXY-List/master/socks5.txt"),
        Pair("مخزن بین‌المللی TheSpeedX HTTP", "https://raw.githubusercontent.com/TheSpeedX/PROXY-List/master/http.txt"),
        Pair("پروکسی‌های عمومی Monosans", "https://raw.githubusercontent.com/monosans/proxy-list/main/proxies/all.txt"),
        Pair("مخزن عمومی Hookzof SOCKS5", "https://raw.githubusercontent.com/hookzof/socks5_list/master/proxy.txt"),
        Pair("مخزن پرسرعت RoosterKid HTTPS", "https://raw.githubusercontent.com/roosterkid/openproxylist/main/HTTPS_RAW.txt"),
        Pair("مخزن پرسرعت RoosterKid SOCKS5", "https://raw.githubusercontent.com/roosterkid/openproxylist/main/SOCKS5_RAW.txt"),
        Pair("پروکسی‌های اختصاصی ShiftyTR", "https://raw.githubusercontent.com/ShiftyTR/Proxy-List/master/socks5.txt")
    )

    fun fetchAllProxiesFlow(): Flow<ProxyFetchProgress> = flow {
        emit(ProxyFetchProgress(isRunning = true, message = "شروع جمع‌آوری پروکسی‌های معتبر..."))
        var totalProxies = 0

        for ((name, url) in proxySources) {
            emit(ProxyFetchProgress(currentSource = name, totalFound = totalProxies, isRunning = true))
            try {
                val content = downloadSafely(url)
                val parsed = ConfigParser.parseProxiesContent(content, name)
                if (parsed.isNotEmpty()) {
                    repository.insertProxies(parsed)
                    totalProxies += parsed.size
                }
            } catch (_: Exception) {}
        }

        emit(
            ProxyFetchProgress(
                isRunning = false,
                totalFound = totalProxies,
                message = "پایان: $totalProxies پروکسی با موفقیت جمع‌آوری و ذخیره شد."
            )
        )
    }.flowOn(Dispatchers.IO)

    private fun downloadSafely(url: String): String {
        return try {
            fetchDirect(url)
        } catch (e: Exception) {
            if (url.contains("raw.githubusercontent.com")) {
                val mirror = convertToJsDelivrMirror(url)
                if (mirror != null) {
                    try {
                        return fetchDirect(mirror)
                    } catch (_: Exception) {}
                }
            }
            throw e
        }
    }

    private fun fetchDirect(url: String): String {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AzadiYar/1.0")
            .header("Accept", "*/*")
            .build()
        val response = httpClient.newCall(request).execute()
        if (response.isSuccessful) {
            return response.body?.string() ?: ""
        }
        throw IllegalStateException("HTTP ${response.code}")
    }

    private fun convertToJsDelivrMirror(rawUrl: String): String? {
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
}
