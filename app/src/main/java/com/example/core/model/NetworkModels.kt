package com.example.core.model

sealed class ConnectionState {
    object Disconnected : ConnectionState()
    data class Connecting(val configRemarks: String, val step: String) : ConnectionState()
    data class Connected(
        val config: ConfigItem? = null,
        val proxy: ProxyItem? = null,
        val connectedAt: Long = System.currentTimeMillis(),
        val externalAccessVerified: Boolean = true,
        val pingMs: Long = 0,
        val bytesIn: Long = 0,
        val bytesOut: Long = 0,
        val downloadSpeedBps: Long = 0,
        val uploadSpeedBps: Long = 0
    ) : ConnectionState() {
        val title: String get() = proxy?.let { "${it.type.displayName} - ${it.host}:${it.port}" } ?: config?.remarks ?: "اتصال فعال"
        val countryName: String get() = proxy?.countryName ?: config?.countryName ?: "نامشخص"
        val countryFlag: String get() = proxy?.countryFlag ?: config?.countryFlag ?: "🌐"
        val countryCode: String get() = proxy?.countryCode ?: config?.countryCode ?: "XX"
        val locationCity: String get() = proxy?.locationCity ?: ""
        val host: String get() = proxy?.host ?: config?.server ?: ""
        val port: Int get() = proxy?.port ?: config?.port ?: 0
        val protocolOrType: String get() = proxy?.type?.displayName ?: config?.protocol?.displayName ?: "VPN"
        val isProxy: Boolean get() = proxy != null
        val rawUriOrShareable: String get() = config?.rawUri ?: (proxy?.let { 
            if (it.type == ProxyType.MTPROTO) {
                "tg://proxy?server=${it.host}&port=${it.port}&secret=${it.secretOrAuth ?: ""}"
            } else {
                "${it.type.name.lowercase()}://${it.host}:${it.port}"
            }
        } ?: "")
    }
    data class Reconnecting(val attempts: Int, val reason: String) : ConnectionState()
    data class Error(val message: String, val canAutoRecover: Boolean = true) : ConnectionState()
}

enum class DiagnosticLevel {
    SUCCESS,
    WARNING,
    ERROR,
    INFO
}

data class DiagnosticStep(
    val title: String,
    val detail: String,
    val level: DiagnosticLevel,
    val timestamp: Long = System.currentTimeMillis()
)

data class DiagnosticReport(
    val networkType: String, // Wi-Fi, Mobile Data, None
    val localIp: String?,
    val hasDnsResolution: Boolean,
    val isCaptivePortal: Boolean,
    val isDirectGlobalReachable: Boolean,
    val isCurrentEndpointReachable: Boolean,
    val isTunnelActive: Boolean,
    val isTunnelGlobalVerified: Boolean,
    val isNationalIntranet: Boolean = false,
    val overallStatusPersian: String,
    val steps: List<DiagnosticStep>
)

data class DnsTunnelConfig(
    val domain: String = "",
    val resolverIp: String = "8.8.8.8",
    val pubKey: String = "",
    val isEnabled: Boolean = false,
    val serverStatus: String = "نیازمند سرور اختصاصی dnstt"
)

data class BridgeConfig(
    val bridgeHost: String = "",
    val bridgePort: Int = 443,
    val fakeSni: String = "",
    val enableFragmentation: Boolean = false,
    val fragmentLengthMin: Int = 10,
    val fragmentLengthMax: Int = 50,
    val fragmentIntervalMs: Int = 10,
    val isEnabled: Boolean = false
)

enum class RoutingMode(val titlePersian: String, val descPersian: String) {
    BYPASS_IRAN_LAN("دور زدن سایت‌های ایرانی و محلی (Bypass Iran & LAN)", "ترافیک داخلی و بانک‌ها بدون فیلترشکن و پرسرعت باز می‌شوند"),
    GLOBAL("مسیریابی سراسری (Global)", "تمام ترافیک دستگاه بدون استثنا از تونل امن عبور می‌کند"),
    PROXY_ONLY("فقط سایت‌های فیلتر شده (Proxy Only)", "سایت‌های تحریم‌شده و فیلترشده از تونل عبور می‌کنند")
}

enum class TestMode(val titlePersian: String, val descPersian: String) {
    REAL_DELAY("تست تاخیر واقعی (Real Delay)", "تست ارسال درخواست به generate_204 و بررسی رمزنگاری پروتکل"),
    TCPING("تست اتصال پورت (TCPing)", "بررسی باز بودن پورت TCP سرور")
}

data class AppPackageInfo(
    val packageName: String,
    val appName: String,
    val isSystem: Boolean = false
)

data class V2raySettings(
    // 1. Routing & Apps
    val routingMode: RoutingMode = RoutingMode.BYPASS_IRAN_LAN,
    val perAppProxyEnabled: Boolean = false,
    val perAppProxyBypassMode: Boolean = false, // false = only selected apps use VPN; true = selected apps bypass VPN
    val selectedApps: Set<String> = emptySet(),

    // 2. Testing & Automation
    val testMode: TestMode = TestMode.REAL_DELAY,
    val realDelayTestUrl: String = "https://www.gstatic.com/generate_204",
    val autoDeleteInvalidAfterTest: Boolean = true,
    val autoSortAfterTest: Boolean = true,

    // 3. DNS Configuration
    val remoteDns: String = "1.1.1.1",
    val domesticDns: String = "223.5.5.5",
    val enableFakeDns: Boolean = true,

    // 4. Tunnel & Network
    val mtu: Int = 1400,
    val enableUdp: Boolean = true,
    val allowLanSharing: Boolean = false,
    val localSocksPort: Int = 10808,
    val localHttpPort: Int = 10809,
    val autoReconnect: Boolean = true
)

