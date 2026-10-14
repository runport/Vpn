package com.example.connection

import android.content.Context
import android.content.Intent
import com.example.core.model.ConfigItem
import com.example.core.model.ConnectionState
import com.example.core.model.ProxyItem
import com.example.diagnostics.AppLogger
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
import java.net.InetSocketAddress
import java.net.Socket

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
    private var activeProxy: ProxyItem? = null
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
                        if (vpnStatus.isProxy && activeProxy != null) {
                            val prx = activeProxy!!
                            _connectionState.value = ConnectionState.Connected(
                                proxy = prx,
                                connectedAt = vpnStatus.startTime,
                                externalAccessVerified = true,
                                pingMs = prx.pingMs.coerceAtLeast(35)
                            )
                            AppLogger.s("CONN", "اتصال به پروکسی ${prx.type.displayName} (${prx.countryFlag} ${prx.countryName}) برقرار و تثبیت شد")
                            startProxyHealthMonitor(prx)
                        } else if (activeConfig != null) {
                            val cfg = activeConfig!!
                            _connectionState.value = ConnectionState.Connected(
                                config = cfg,
                                connectedAt = vpnStatus.startTime,
                                externalAccessVerified = true,
                                pingMs = cfg.pingMs.coerceAtLeast(45)
                            )
                            AppLogger.s("CONN", "اتصال به کانفیگ ${cfg.remarks} (${cfg.countryFlag} ${cfg.countryName}) برقرار و تثبیت شد")
                            startHealthMonitor(cfg)
                        }
                    }
                    is VpnServiceStatus.Error -> {
                        AppLogger.e("CONN", "خطای سرویس VPN: ${vpnStatus.message}")
                        handleConnectionDrop(vpnStatus.message)
                    }
                    VpnServiceStatus.Stopped -> {
                        if (_connectionState.value is ConnectionState.Connected) {
                            AppLogger.i("CONN", "سرویس تونل متوقف شد")
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

        // Observe bandwidth flow to update live throughput on the dashboard
        scope.launch {
            AzadiVpnService.bandwidthFlow.collect { bw ->
                val current = _connectionState.value
                if (current is ConnectionState.Connected) {
                    _connectionState.value = current.copy(
                        bytesIn = bw.totalBytesIn,
                        bytesOut = bw.totalBytesOut,
                        downloadSpeedBps = bw.downloadSpeedBps,
                        uploadSpeedBps = bw.uploadSpeedBps
                    )
                }
            }
        }
    }

    fun connect(config: ConfigItem) {
        scope.launch {
            reconnectAttempts = 0
            failedConfigIdsInSession.clear()
            activeConfig = config
            activeProxy = null
            AppLogger.i("CONN", "درخواست اتصال به کانفیگ: ${config.remarks} [${config.server}:${config.port}]")
            _connectionState.value = ConnectionState.Connecting(config.remarks, "در حال بررسی دسترسی به سرور...")

            // Test endpoint reachability before launching VPN
            val testResult = tester.testConfig(config)
            if (testResult.status == com.example.core.model.TestStatus.FAILED) {
                AppLogger.w("CONN", "سرور انتخابی پاسخ نداد: ${testResult.errorMessage}")
                if (autoRecoveryEnabled) {
                    failedConfigIdsInSession.add(config.id)
                    triggerAutoRecovery("سرور انتخابی پاسخ نداد، جستجوی مسیر جایگزین...")
                } else {
                    _connectionState.value = ConnectionState.Error("سرور در دسترس نیست: ${testResult.errorMessage}")
                }
                return@launch
            }

            AppLogger.i("CONN", "پاسخ اولیه سرور موفقیت‌آمیز بود (پینگ: ${testResult.pingMs}ms). ایجاد تونل امنیتی...")
            _connectionState.value = ConnectionState.Connecting(config.remarks, "در حال ایجاد رابط شبکه امن...")
            dispatchVpnIntent(config.id, config.server, config.port, config.remarks, config.protocol.name, isProxy = false)
        }
    }

    fun connectProxy(proxy: ProxyItem) {
        scope.launch {
            reconnectAttempts = 0
            failedConfigIdsInSession.clear()
            activeProxy = proxy
            activeConfig = null
            val remarks = "${proxy.type.displayName} - ${proxy.host}:${proxy.port}"
            AppLogger.i("CONN", "درخواست اتصال مستقیم به پروکسی: $remarks")
            _connectionState.value = ConnectionState.Connecting(remarks, "در حال بررسی دسترسی به پروکسی...")

            // Test socket reachability
            var reachable = false
            var ping = -1L
            val start = System.currentTimeMillis()
            var sock: Socket? = null
            try {
                sock = Socket()
                sock.soTimeout = 2500
                sock.connect(InetSocketAddress(proxy.host, proxy.port), 2500)
                ping = System.currentTimeMillis() - start
                reachable = true
                AppLogger.s("CONN", "سوکت پروکسی ${proxy.host}:${proxy.port} پاسخ داد ($ping ms)")
            } catch (e: Exception) {
                AppLogger.e("CONN", "عدم پاسخ سوکت پروکسی: ${e.message}")
            } finally {
                try { sock?.close() } catch (_: Exception) {}
            }

            if (!reachable) {
                _connectionState.value = ConnectionState.Error("پروکسی در دسترس نیست یا ارتباط مسدود شده است")
                return@launch
            }

            _connectionState.value = ConnectionState.Connecting(remarks, "در حال ایجاد تونل پروکسی...")
            dispatchVpnIntent(proxy.id, proxy.host, proxy.port, remarks, proxy.type.name, isProxy = true)
        }
    }

    fun connectSmart() {
        scope.launch {
            AppLogger.i("CONN", "آغاز الگوریتم انتخاب هوشمند بهترین کانفیگ...")
            _connectionState.value = ConnectionState.Connecting("انتخاب هوشمند", "در حال بررسی بهترین مسیر...")
            val result = selector.selectBestConfig()
            val best = result.selectedConfig
            if (best == null) {
                AppLogger.w("CONN", "انتخاب هوشمند کانفیگی پیدا نکرد: ${result.reasonPersian}")
                _connectionState.value = ConnectionState.Error(result.reasonPersian)
            } else {
                AppLogger.s("CONN", "بهترین کانفیگ انتخاب شد: ${best.remarks} با امتیاز ${best.qualityScore}")
                connect(best)
            }
        }
    }

    fun disconnect() {
        AppLogger.i("CONN", "قطع اتصال دستی توسط کاربر")
        healthCheckJob?.cancel()
        healthCheckJob = null
        activeConfig = null
        activeProxy = null
        val intent = Intent(context, AzadiVpnService::class.java).apply {
            action = AzadiVpnService.ACTION_DISCONNECT
        }
        context.startService(intent)
        _connectionState.value = ConnectionState.Disconnected
    }

    private fun dispatchVpnIntent(id: Long, server: String, port: Int, remarks: String, protocol: String, isProxy: Boolean) {
        val intent = Intent(context, AzadiVpnService::class.java).apply {
            action = AzadiVpnService.ACTION_CONNECT
            putExtra(AzadiVpnService.EXTRA_CONFIG_ID, id)
            putExtra(AzadiVpnService.EXTRA_SERVER, server)
            putExtra(AzadiVpnService.EXTRA_PORT, port)
            putExtra(AzadiVpnService.EXTRA_REMARKS, remarks)
            putExtra(AzadiVpnService.EXTRA_PROTOCOL, protocol)
            putExtra(AzadiVpnService.EXTRA_IS_PROXY, isProxy)
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    private fun handleConnectionDrop(reason: String) {
        stopHealthMonitor()
        if (activeConfig != null && autoRecoveryEnabled && reconnectAttempts < maxReconnectAttempts) {
            triggerAutoRecovery(reason)
        } else {
            _connectionState.value = ConnectionState.Error(
                message = "اتصال قطع شد: $reason",
                canAutoRecover = false
            )
        }
    }

    private fun triggerAutoRecovery(reason: String) {
        activeConfig?.let { failedConfigIdsInSession.add(it.id) }
        reconnectAttempts++
        AppLogger.w("CONN", "تلاش برای بازیابی خودکار اتصال ($reconnectAttempts از $maxReconnectAttempts): $reason")

        scope.launch {
            _connectionState.value = ConnectionState.Reconnecting(
                attempts = reconnectAttempts,
                reason = "تلاش مجدد ($reconnectAttempts از $maxReconnectAttempts): $reason"
            )
            delay(1500)

            val nextChoice = selector.selectBestConfig(failedConfigIdsInSession)
            val fallback = nextChoice.selectedConfig
            if (fallback != null) {
                AppLogger.s("CONN", "مسیر جایگزین یافت شد: ${fallback.remarks}. شروع انتقال تونل...")
                activeConfig = fallback
                dispatchVpnIntent(fallback.id, fallback.server, fallback.port, fallback.remarks, fallback.protocol.name, isProxy = false)
            } else {
                AppLogger.e("CONN", "هیچ مسیر سالمی برای بازیابی خودکار یافت نشد")
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
                    AppLogger.w("CONN", "هشدار: سرور در بررسی دوره‌ای پاسخ نداد")
                    handleConnectionDrop("عدم پاسخگویی سرور در بررسی دوره‌ای")
                    break
                }
            }
        }
    }

    private fun startProxyHealthMonitor(proxy: ProxyItem) {
        healthCheckJob?.cancel()
        healthCheckJob = scope.launch {
            while (isActive) {
                delay(30000)
                var socket: Socket? = null
                try {
                    socket = Socket()
                    socket.soTimeout = 2000
                    socket.connect(InetSocketAddress(proxy.host, proxy.port), 2000)
                } catch (_: Exception) {
                    AppLogger.w("CONN", "پروکسی در بررسی دوره‌ای پاسخ نداد")
                    handleConnectionDrop("عدم پاسخگویی پروکسی در بررسی دوره‌ای")
                    break
                } finally {
                    try { socket?.close() } catch (_: Exception) {}
                }
            }
        }
    }

    private fun stopHealthMonitor() {
        healthCheckJob?.cancel()
        healthCheckJob = null
    }
}
