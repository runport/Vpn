package com.example.core.model

enum class ProxyProtocol(val displayName: String, val scheme: String) {
    VLESS("VLESS", "vless"),
    VMESS("VMess", "vmess"),
    TROJAN("Trojan", "trojan"),
    SHADOWSOCKS("Shadowsocks", "ss"),
    SOCKS5("SOCKS5", "socks5"),
    HTTP("HTTP", "http");

    companion object {
        fun fromScheme(scheme: String): ProxyProtocol? {
            return when (scheme.lowercase()) {
                "vless" -> VLESS
                "vmess" -> VMESS
                "trojan" -> TROJAN
                "ss" -> SHADOWSOCKS
                "socks", "socks5" -> SOCKS5
                "http", "https" -> HTTP
                else -> null
            }
        }
    }
}

enum class TestStatus(val persianTitle: String) {
    UNTESTED("آزمایش‌نشده"),
    STRUCTURALLY_VALID("معتبر ساختاری"),
    REACHABLE_ENDPOINT("سرور قابل‌دسترس"),
    TUNNEL_CONNECTED("تونل برقرار شد"),
    GLOBAL_ACCESS_CONFIRMED("دسترسی بین‌المللی تأیید شد"),
    FAILED("ناموفق"),
    INDETERMINATE("نتیجه نامشخص")
}

data class ConfigItem(
    val id: Long = 0,
    val normalizedHash: String,
    val remarks: String,
    val protocol: ProxyProtocol,
    val server: String,
    val port: Int,
    val uuidOrPassword: String,
    val cipher: String? = null,
    val network: String = "tcp", // tcp, ws, grpc, h2
    val security: String = "none", // none, tls, reality
    val sni: String? = null,
    val path: String? = null,
    val flow: String? = null,
    val rawUri: String,
    val countryCode: String = "XX",
    val countryName: String = "نامشخص",
    val countryFlag: String = "🌐",
    val isBypassNationalNet: Boolean = false,
    val isHalfPriceDomestic: Boolean = false,
    val sourceIds: List<Long> = emptyList(),
    val sourceCount: Int = 1,
    val pingMs: Long = -1,
    val testStatus: TestStatus = TestStatus.UNTESTED,
    val qualityScore: Int = 0,
    val lastTestTime: Long = 0,
    val lastSuccessTime: Long = 0,
    val lastErrorMessage: String? = null,
    val successCount: Int = 0,
    val failureCount: Int = 0,
    val isFavorite: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

data class ConfigSource(
    val id: Long = 0,
    val name: String,
    val url: String,
    val isEnabled: Boolean = true,
    val isUserCustom: Boolean = false,
    val lastFetchTime: Long = 0,
    val lastFetchStatus: String = "در انتظار دریافت",
    val configCount: Int = 0,
    val sharePercent: Float = 0f,
    val description: String = ""
)

enum class ProxyType(val displayName: String) {
    SOCKS5("SOCKS5"),
    HTTP("HTTP"),
    HTTPS("HTTPS"),
    MTPROTO("MTProto"),
    SOCKS4("SOCKS4")
}

data class ProxyItem(
    val id: Long = 0,
    val host: String,
    val port: Int,
    val type: ProxyType = ProxyType.SOCKS5,
    val secretOrAuth: String? = null,
    val countryCode: String = "XX",
    val countryName: String = "نامشخص",
    val countryFlag: String = "🌐",
    val locationCity: String = "",
    val pingMs: Long = -1,
    val isHealthy: Boolean = false,
    val isDomestic: Boolean = false,
    val sourceName: String = "عمومی",
    val lastChecked: Long = 0
)

data class CountryStats(
    val countryCode: String,
    val countryName: String,
    val flag: String,
    val count: Int,
    val healthyCount: Int,
    val avgPing: Long,
    val mapXRatio: Float,
    val mapYRatio: Float
)
