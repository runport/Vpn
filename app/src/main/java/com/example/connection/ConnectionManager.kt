package com.example.connection

import android.content.Context
import android.content.Intent
import com.example.core.model.ConfigItem
import com.example.core.model.ConnectionState
import com.example.storage.AzadiRepository
import com.example.testing.ConfigTester
import com.example.vpn.AzadiVpnService
import com.example.vpn.VpnServiceStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class ConnectionManager(
    private val context: Context,
    private val repository: AzadiRepository,
    private val selector: SmartConfigSelector,
    private val tester: ConfigTester
) {
    private val scope = CoroutineScope(Dispatchers.IO)
    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState = _connectionState.asStateFlow()

    private var activeConfig: ConfigItem? = null
    private var healthCheckJob: Job? = null
    private var reconnectAttempts = 0
    private val maxReconnectAttempts = 3
    private val failedConfigIdsInSession = mutableSetOf<Long>()

    var autoRecoveryEnabled: Boolean = true

    init {
        // Observe low-level VPN service status
        scope.launch {
            AzadiVpnService.vpnStatusFlow.collect { vpnStatus ->
                when (vpnStatus) {
                    is VpnServiceStatus.Running -> {
                        activeConfig?.let { cfg ->
                            _connectionState.value = ConnectionState.Connected(
                                config = cfg,
                                connectedAt = vpnStatus.startTime,
                                externalAccessVerified = true,
                                pingMs = cfg.pingMs.coerceAtLeast(45)
                            )
                            startHealthMonitor(cfg)
                        }
                    }
                    is VpnServiceStatus.Error -> {
                        handleConnectionDrop(vpnStatus.message)
                    }
                    VpnServiceStatus.Stopped -> {
                        if (_connectionState.value is ConnectionState.Connected) {
                            handleConnectionDrop("تونل قطع شد")
                        } else if (_connectionState.value !is ConnectionState.Connecting &&
                            _connectionState.value !is ConnectionState.Reconnecting) {
                            _connectionState.value = ConnectionState.Disconnected
                        }
                        stopHealthMonitor()
                    }
                }
            }
        }
    }

    fun connect(config: ConfigItem) {
        scope.launch {
            reconnectAttempts = 0
            failedConfigIdsInSession.clear()
            activeConfig = config
            _connectionState.value = ConnectionState.Connecting(config.remarks, "در حال بررسی اولیه سرور...")

            // Test endpoint reachability before launching VPN
            val testResult = tester.testConfig(config)
            if (testResult.status == com.example.core.model.TestStatus.FAILED) {
                if (autoRecoveryEnabled) {
                    failedConfigIdsInSession.add(config.id)
                    triggerAutoRecovery("سرور انتخابی پاسخ نداد، جستجوی مسیر جایگزین...")
                } else {
                    _connectionState.value = ConnectionState.Error("سرور در دسترس نیست: ${testResult.errorMessage}")
                }
                return@launch
            }

            _connectionState.value = ConnectionState.Connecting(config.remarks, "در حال ایجاد رابط شبکه امن...")
            dispatchVpnIntent(config)
        }
    }

    fun connectSmart() {
        scope.launch {
            _connectionState.value = ConnectionState.Connecting("انتخاب هوشمند", "در حال بررسی بهترین مسیر...")
            val result = selector.selectBestConfig()
            val best = result.selectedConfig
            if (best == null) {
                _connectionState.value = ConnectionState.Error(result.reasonPersian)
            } else {
                connect(best)
            }
        }
    }

    fun disconnect() {
        healthCheckJob?.cancel()
        healthCheckJob = null
        activeConfig = null
        val intent = Intent(context, AzadiVpnService::class.java).apply {
            action = AzadiVpnService.ACTION_DISCONNECT
        }
        context.startService(intent)
        _connectionState.value = ConnectionState.Disconnected
    }

    private fun dispatchVpnIntent(config: ConfigItem) {
        val intent = Intent(context, AzadiVpnService::class.java).apply {
            action = AzadiVpnService.ACTION_CONNECT
            putExtra(AzadiVpnService.EXTRA_CONFIG_ID, config.id)
            putExtra(AzadiVpnService.EXTRA_SERVER, config.server)
            putExtra(AzadiVpnService.EXTRA_PORT, config.port)
            putExtra(AzadiVpnService.EXTRA_REMARKS, config.remarks)
            putExtra(AzadiVpnService.EXTRA_PROTOCOL, config.protocol.name)
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    private fun handleConnectionDrop(reason: String) {
        stopHealthMonitor()
        if (autoRecoveryEnabled && reconnectAttempts < maxReconnectAttempts) {
            triggerAutoRecovery(reason)
        } else {
            _connectionState.value = ConnectionState.Error(
                message = "اتصال قطع شد: $reason. به حد مجاز تلاش مجدد رسیدید.",
                canAutoRecover = false
            )
        }
    }

    private fun triggerAutoRecovery(reason: String) {
        activeConfig?.let { failedConfigIdsInSession.add(it.id) }
        reconnectAttempts++

        scope.launch {
            _connectionState.value = ConnectionState.Reconnecting(
                attempts = reconnectAttempts,
                reason = "تلاش مجدد ($reconnectAttempts از $maxReconnectAttempts): $reason"
            )
            delay(1500)

            val nextChoice = selector.selectBestConfig(failedConfigIdsInSession)
            val fallback = nextChoice.selectedConfig
            if (fallback != null) {
                activeConfig = fallback
                dispatchVpnIntent(fallback)
            } else {
                _connectionState.value = ConnectionState.Error(
                    "هیچ مسیر جایگزین سالمی برای بازیابی یافت نشد.",
                    canAutoRecover = false
                )
            }
        }
    }

    private fun startHealthMonitor(config: ConfigItem) {
        healthCheckJob?.cancel()
        healthCheckJob = scope.launch {
            while (isActive) {
                delay(30000) // check every 30 seconds
                val checkResult = tester.testConfig(config)
                if (checkResult.status == com.example.core.model.TestStatus.FAILED) {
                    handleConnectionDrop("عدم پاسخگویی سرور در بررسی دوره‌ای")
                    break
                }
            }
        }
    }

    private fun stopHealthMonitor() {
        healthCheckJob?.cancel()
        healthCheckJob = null
    }
}
