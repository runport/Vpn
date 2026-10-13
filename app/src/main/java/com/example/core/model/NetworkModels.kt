package com.example.core.model

sealed class ConnectionState {
    object Disconnected : ConnectionState()
    data class Connecting(val configRemarks: String, val step: String) : ConnectionState()
    data class Connected(
        val config: ConfigItem,
        val connectedAt: Long,
        val externalAccessVerified: Boolean,
        val pingMs: Long,
        val bytesIn: Long = 0,
        val bytesOut: Long = 0
    ) : ConnectionState()
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
