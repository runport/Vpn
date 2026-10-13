package com.example.connection

import com.example.core.model.ConfigItem
import com.example.core.model.TestStatus
import com.example.storage.AzadiRepository

data class SelectionResult(
    val selectedConfig: ConfigItem?,
    val reasonPersian: String
)

class SmartConfigSelector(private val repository: AzadiRepository) {

    /**
     * Intelligently selects the best available configuration based on real metrics:
     * 1. Verified reachability (TUNNEL_CONNECTED or REACHABLE_ENDPOINT)
     * 2. Lowest latency (pingMs)
     * 3. Highest quality score
     * 4. Excludes currently failing configs
     */
    suspend fun selectBestConfig(excludeConfigIds: Set<Long> = emptySet()): SelectionResult {
        val allConfigs = repository.getAllConfigs()
        if (allConfigs.isEmpty()) {
            return SelectionResult(
                selectedConfig = null,
                reasonPersian = "هیچ کانفیگی در برنامه ذخیره نشده است. لطفاً ابتدا از بخش منابع کانفیگ دریافت کنید."
            )
        }

        // 1. Look for verified healthy configs not excluded
        val candidates = allConfigs
            .filter { it.id !in excludeConfigIds }
            .filter { it.testStatus != TestStatus.FAILED }

        if (candidates.isEmpty()) {
            // If all verified failed, pick an untested config as fallback
            val untested = allConfigs.filter { it.id !in excludeConfigIds && it.testStatus == TestStatus.UNTESTED }
            if (untested.isNotEmpty()) {
                val chosen = untested.first()
                return SelectionResult(
                    selectedConfig = chosen,
                    reasonPersian = "انتخاب مسیر جایگزین آزمایش‌نشده به دلیل عدم پاسخگویی گزینه‌های قبلی"
                )
            }

            return SelectionResult(
                selectedConfig = null,
                reasonPersian = "تمام کانفیگ‌های موجود ناموفق بودند. لطفاً تست مجدد انجام دهید یا منابع را به‌روزرسانی کنید."
            )
        }

        // Sort by verified status first, then highest score, then lowest ping
        val sorted = candidates.sortedWith(
            compareByDescending<ConfigItem> {
                it.testStatus == TestStatus.GLOBAL_ACCESS_CONFIRMED || it.testStatus == TestStatus.TUNNEL_CONNECTED
            }.thenByDescending {
                it.qualityScore
            }.thenBy {
                if (it.pingMs > 0) it.pingMs else Long.MAX_VALUE
            }
        )

        val best = sorted.first()
        val reason = if (best.pingMs > 0) {
            "انتخاب هوشمند: پینگ ${best.pingMs} میلی‌ثانیه | امتیاز کیفیت: ${best.qualityScore}/100 (${best.protocol.displayName})"
        } else {
            "انتخاب خودکار بر اساس اعتبار ساختاری پروتکل ${best.protocol.displayName}"
        }

        return SelectionResult(
            selectedConfig = best,
            reasonPersian = reason
        )
    }
}
