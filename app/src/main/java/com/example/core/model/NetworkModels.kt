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
