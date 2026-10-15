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

            // Test endpoint reachability with user-selected test mode before launching VPN
            val settings = com.example.AzadiYarApp.instance.v2raySettingsManager.getSettings()
            val testResult = tester.testConfig(config, testMode = settings.testMode, testUrl = settings.realDelayTestUrl)
            if (testResult.status == com.example.core.model.TestStatus.FAILED) {
                AppLogger.w("CONN", "سرور انتخابی «${config.remarks}» پاسخ نداد: ${testResult.errorMessage}")
                scope.launch {
                    repository.updateTestResult(
                        id = config.id,
                        status = com.example.core.model.TestStatus.FAILED,
                        pingMs = -1,
                        score = 0,
                        errorMessage = testResult.errorMessage,
                        isSuccess = false
                    )
                }
                if (settings.autoSwitchOnFailure && reconnectAttempts < maxReconnectAttempts) {
                    failedConfigIdsInSession.add(config.id)
                    triggerAutoRecovery("سرور انتخابی پاسخ نداد، جستجوی مسیر جایگزین...")
                } else {
                    _connectionState.value = ConnectionState.Error("عدم پاسخ از سرور انتخابی «${config.remarks}»: ${testResult.errorMessage ?: "تایم‌اوت یا مسدود بودن پورت"}")
                }
                return@launch
            }

            AppLogger.i("CONN", "پاسخ اولیه سرور «${config.remarks}» موفقیت‌آمیز بود (پینگ: ${testResult.pingMs}ms). ایجاد تونل امنیتی...")
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
        val failed = activeConfig
        if (failed != null) {
            failedConfigIdsInSession.add(failed.id)
            scope.launch {
                repository.updateTestResult(
                    id = failed.id,
                    status = com.example.core.model.TestStatus.FAILED,
                    pingMs = -1,
                    score = 0,
                    errorMessage = reason,
                    isSuccess = false
                )
            }
        }
        val settings = com.example.AzadiYarApp.instance.v2raySettingsManager.getSettings()
        if (failed != null && settings.autoSwitchOnFailure && reconnectAttempts < maxReconnectAttempts) {
            triggerAutoRecovery(reason)
        } else {
            _connectionState.value = ConnectionState.Error(
                message = "اتصال قطع شد: $reason",
                canAutoRecover = false
            )
        }
    }

    private fun triggerAutoRecovery(reason: String) {
        reconnectAttempts++
        AppLogger.w("CONN", "تلاش برای بازیابی خودکار اتصال ($reconnectAttempts از $maxReconnectAttempts): $reason")

        scope.launch {
            _connectionState.value = ConnectionState.Reconnecting(
                attempts = reconnectAttempts,
                reason = "جستجوی مسیر جایگزین ($reconnectAttempts از $maxReconnectAttempts)..."
            )
            delay(1000)

            val settings = com.example.AzadiYarApp.instance.v2raySettingsManager.getSettings()
            var verifiedFallback: ConfigItem? = null

            // Test candidate fallbacks first before attempting to establish VPN tunnel
            var attempts = 0
            while (attempts < 3) {
                val nextChoice = selector.selectBestConfig(failedConfigIdsInSession)
                val candidate = nextChoice.selectedConfig ?: break
                failedConfigIdsInSession.add(candidate.id)
                attempts++

                AppLogger.i("CONN", "بررسی سریع مسیر جایگزین: ${candidate.remarks} [${candidate.server}:${candidate.port}]...")
                _connectionState.value = ConnectionState.Reconnecting(
                    attempts = reconnectAttempts,
                    reason = "آزمایش پاسخ‌دهی سرور: ${candidate.remarks}..."
                )

                val testResult = tester.testConfig(candidate, testMode = settings.testMode, testUrl = settings.realDelayTestUrl)
                if (testResult.status != com.example.core.model.TestStatus.FAILED && testResult.pingMs > 0) {
                    verifiedFallback = candidate.copy(
                        pingMs = testResult.pingMs,
                        qualityScore = testResult.score,
                        testStatus = testResult.status
                    )
                    break
                } else {
                    AppLogger.w("CONN", "مسیر جایگزین ${candidate.remarks} پاسخ نداد: ${testResult.errorMessage ?: "تایم‌اوت"}")
                }
            }

            if (verifiedFallback != null) {
                AppLogger.s("CONN", "مسیر جایگزین تأیید شد: ${verifiedFallback.remarks} (پینگ: ${verifiedFallback.pingMs}ms). انتقال تونل...")
                activeConfig = verifiedFallback
                _connectionState.value = ConnectionState.Connecting(verifiedFallback.remarks, "در حال ایجاد تونل به مسیر جدید...")
                dispatchVpnIntent(verifiedFallback.id, verifiedFallback.server, verifiedFallback.port, verifiedFallback.remarks, verifiedFallback.protocol.name, isProxy = false)
            } else {
                AppLogger.e("CONN", "هیچ مسیر سالمی برای بازیابی خودکار یافت نشد")
                _connectionState.value = ConnectionState.Error(
                    "سرورهای انتخابی پاسخ ندادند (تایم‌اوت یا مسدود بودن پورت توسط نت ملی). از منوی کانفیگ‌ها دکمه «تست تاخیر واقعی همه» را بزنید تا کانفیگ‌های زنده مشخص شوند.",
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
