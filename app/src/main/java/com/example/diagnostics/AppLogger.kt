package com.example.diagnostics

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class AppLogLevel(val titlePersian: String, val badgeColor: Long) {
    DEBUG("دیباگ", 0xFF78909C),
    INFO("اطلاعات", 0xFF0288D1),
    SUCCESS("موفقیت", 0xFF2E7D32),
    WARN("هشدار", 0xFFF57F17),
    ERROR("خطا", 0xFFC62828)
}

data class LogItem(
    val id: Long = System.nanoTime(),
    val timestamp: Long = System.currentTimeMillis(),
    val level: AppLogLevel,
    val tag: String,
    val message: String,
    val details: String? = null
) {
    val formattedTime: String
        get() {
            val sdf = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
            return sdf.format(Date(timestamp))
        }
}

object AppLogger {
    private const val MAX_LOGS = 1000
    private val logList = ArrayList<LogItem>(MAX_LOGS)
    private val _logsFlow = MutableStateFlow<List<LogItem>>(emptyList())
    val logsFlow = _logsFlow.asStateFlow()

    @Synchronized
    private fun addEntry(level: AppLogLevel, tag: String, message: String, details: String? = null) {
        val entry = LogItem(level = level, tag = tag, message = message, details = details)
        if (logList.size >= MAX_LOGS) {
            logList.removeAt(0)
        }
        logList.add(entry)
        _logsFlow.value = logList.toList()
        android.util.Log.println(
            when (level) {
                AppLogLevel.DEBUG -> android.util.Log.DEBUG
                AppLogLevel.INFO -> android.util.Log.INFO
                AppLogLevel.SUCCESS -> android.util.Log.INFO
                AppLogLevel.WARN -> android.util.Log.WARN
                AppLogLevel.ERROR -> android.util.Log.ERROR
            },
            "AzadiYar/$tag",
            message + if (details != null) "\n$details" else ""
        )
    }

    fun d(tag: String, message: String) = addEntry(AppLogLevel.DEBUG, tag, message)
    fun i(tag: String, message: String) = addEntry(AppLogLevel.INFO, tag, message)
    fun s(tag: String, message: String) = addEntry(AppLogLevel.SUCCESS, tag, message)
    fun w(tag: String, message: String) = addEntry(AppLogLevel.WARN, tag, message)
    fun e(tag: String, message: String, tr: Throwable? = null) =
        addEntry(AppLogLevel.ERROR, tag, message, tr?.stackTraceToString())

    @Synchronized
    fun clear() {
        logList.clear()
        _logsFlow.value = emptyList()
        s("SYSTEM", "تمام لاگ‌های سیستم با موفقیت پاک شدند")
    }

    @Synchronized
    fun exportAsText(): String {
        val sb = StringBuilder()
        sb.appendLine("=== گزارش لاگ‌های زنده آزادیار (AzadiYar System Logs) ===")
        sb.appendLine("تاریخ گزارش: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())}")
        sb.appendLine("تعداد رکوردها: ${logList.size}")
        sb.appendLine("-------------------------------------------------------")
        for (item in logList) {
            sb.appendLine("[${item.formattedTime}] [${item.level.name}] [${item.tag}]: ${item.message}")
            if (!item.details.isNullOrBlank()) {
                sb.appendLine("   -> ${item.details}")
            }
        }
        sb.appendLine("-------------------------------------------------------")
        return sb.toString()
    }
}
