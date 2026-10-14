package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.AzadiYarApp
import com.example.config.collector.FetchProgress
import com.example.config.collector.ProxyFetchProgress
import com.example.config.parser.ConfigParser
import com.example.core.model.BridgeConfig
import com.example.core.model.ConfigItem
import com.example.core.model.ConfigSource
import com.example.core.model.ConnectionState
import com.example.core.model.CountryStats
import com.example.core.model.DiagnosticReport
import com.example.core.model.DnsTunnelConfig
import com.example.core.model.ProxyItem
import com.example.core.model.ProxyProtocol
import com.example.core.model.ProxyType
import com.example.core.model.TestStatus
import com.example.storage.entities.ConfigSourceEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class ConfigSortOption(val titlePersian: String) {
    BEST_SCORE("بهترین امتیاز"),
    LOWEST_PING("کمترین پینگ"),
    NEWEST("جدیدترین"),
    STATUS("وضعیت سلامت")
}

class MainViewModel : ViewModel() {

    private val app = AzadiYarApp.instance
    private val repository = app.repository
    private val collector = app.configSourceManager
    private val tester = app.configTester
    private val connectionManager = app.connectionManager
    private val diagnostics = app.networkDiagnostics
    private val proxyManager = app.proxyManager

    val connectionState: StateFlow<ConnectionState> = connectionManager.connectionState

    val allSources: StateFlow<List<ConfigSource>> = repository.getAllSourcesFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val proxies: StateFlow<List<ProxyItem>> = repository.getAllProxiesFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val countryDistribution: StateFlow<List<CountryStats>> = repository.getCountryDistributionFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val proxyCountryDistribution: StateFlow<List<CountryStats>> = repository.getProxyCountryDistributionFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _fetchProgress = MutableStateFlow(FetchProgress())
    val fetchProgress: StateFlow<FetchProgress> = _fetchProgress.asStateFlow()

    private val _proxyFetchProgress = MutableStateFlow(ProxyFetchProgress())
    val proxyFetchProgress: StateFlow<ProxyFetchProgress> = _proxyFetchProgress.asStateFlow()

    private val _isTestingAll = MutableStateFlow(false)
    val isTestingAll: StateFlow<Boolean> = _isTestingAll.asStateFlow()

    private val _testProgress = MutableStateFlow(Pair(0, 0))
    val testProgress: StateFlow<Pair<Int, Int>> = _testProgress.asStateFlow()

    private val _isTestingProxies = MutableStateFlow(false)
    val isTestingProxies: StateFlow<Boolean> = _isTestingProxies.asStateFlow()

    private val _proxyTestProgress = MutableStateFlow(Pair(0, 0))
    val proxyTestProgress: StateFlow<Pair<Int, Int>> = _proxyTestProgress.asStateFlow()

    private val _diagnosticReport = MutableStateFlow<DiagnosticReport?>(null)
    val diagnosticReport: StateFlow<DiagnosticReport?> = _diagnosticReport.asStateFlow()

    private val _isDiagnosing = MutableStateFlow(false)
    val isDiagnosing: StateFlow<Boolean> = _isDiagnosing.asStateFlow()

    // Filters and Search for Configs Screen
    val searchQuery = MutableStateFlow("")
    val selectedProtocol = MutableStateFlow<ProxyProtocol?>(null)
    val selectedStatus = MutableStateFlow<TestStatus?>(null)
    val selectedCountry = MutableStateFlow<String?>(null)
    val filterOnlyBypassNationalNet = MutableStateFlow(false)
    val filterOnlyHalfPrice = MutableStateFlow(false)
    val sortOption = MutableStateFlow(ConfigSortOption.BEST_SCORE)
    val showMapView = MutableStateFlow(false)

    // Filters and Search for Proxies Screen
    val proxySearchQuery = MutableStateFlow("")
    val selectedProxyType = MutableStateFlow<ProxyType?>(null)
    val selectedProxyCountry = MutableStateFlow<String?>(null)
    val filterDomesticProxiesOnly = MutableStateFlow(false)
    val showProxyMapView = MutableStateFlow(false)

    val dnsTunnelConfig = MutableStateFlow(DnsTunnelConfig())
    val bridgeConfig = MutableStateFlow(BridgeConfig())
    val autoRecovery = MutableStateFlow(true)

    val filteredConfigs: StateFlow<List<ConfigItem>> = combine(
        repository.getAllConfigsFlow(),
        searchQuery,
        selectedProtocol,
        selectedStatus,
        selectedCountry,
        filterOnlyBypassNationalNet,
        filterOnlyHalfPrice,
        sortOption
    ) { args: Array<Any?> ->
        @Suppress("UNCHECKED_CAST")
        val configs = args[0] as List<ConfigItem>
        val query = args[1] as String
        val protocol = args[2] as ProxyProtocol?
        val status = args[3] as TestStatus?
        val country = args[4] as String?
        val onlyBypass = args[5] as Boolean
        val onlyHalfPrice = args[6] as Boolean
        val sort = args[7] as ConfigSortOption

        configs.filter { item ->
            val matchesQuery = query.isBlank() ||
                    item.remarks.contains(query, ignoreCase = true) ||
                    item.server.contains(query, ignoreCase = true) ||
                    item.countryName.contains(query, ignoreCase = true)
            val matchesProtocol = protocol == null || item.protocol == protocol
            val matchesStatus = status == null || item.testStatus == status
            val matchesCountry = country == null || item.countryCode.equals(country, ignoreCase = true)
            val matchesBypass = !onlyBypass || item.isBypassNationalNet
            val matchesHalfPrice = !onlyHalfPrice || item.isHalfPriceDomestic

            matchesQuery && matchesProtocol && matchesStatus && matchesCountry && matchesBypass && matchesHalfPrice
        }.sortedWith { a, b ->
            when (sort) {
                ConfigSortOption.BEST_SCORE -> b.qualityScore.compareTo(a.qualityScore)
                ConfigSortOption.LOWEST_PING -> {
                    val pingA = if (a.pingMs > 0) a.pingMs else Long.MAX_VALUE
                    val pingB = if (b.pingMs > 0) b.pingMs else Long.MAX_VALUE
                    pingA.compareTo(pingB)
                }
                ConfigSortOption.NEWEST -> b.id.compareTo(a.id)
                ConfigSortOption.STATUS -> {
                    val rankA = if (a.testStatus == TestStatus.GLOBAL_ACCESS_CONFIRMED || a.testStatus == TestStatus.TUNNEL_CONNECTED) 1 else 0
                    val rankB = if (b.testStatus == TestStatus.GLOBAL_ACCESS_CONFIRMED || b.testStatus == TestStatus.TUNNEL_CONNECTED) 1 else 0
                    rankB.compareTo(rankA)
                }
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val filteredProxies: StateFlow<List<ProxyItem>> = combine(
        repository.getAllProxiesFlow(),
        proxySearchQuery,
        selectedProxyType,
        selectedProxyCountry,
        filterDomesticProxiesOnly
    ) { args: Array<Any?> ->
        @Suppress("UNCHECKED_CAST")
        val proxyList = args[0] as List<ProxyItem>
        val query = args[1] as String
        val type = args[2] as ProxyType?
        val country = args[3] as String?
        val domesticOnly = args[4] as Boolean

        proxyList.filter { proxy ->
            val matchesQuery = query.isBlank() ||
                    proxy.host.contains(query, ignoreCase = true) ||
                    proxy.port.toString().contains(query) ||
                    proxy.countryName.contains(query, ignoreCase = true) ||
                    proxy.sourceName.contains(query, ignoreCase = true)
            val matchesType = type == null || proxy.type == type
            val matchesCountry = country == null || proxy.countryCode.equals(country, ignoreCase = true)
            val matchesDomestic = !domesticOnly || proxy.isDomestic

            matchesQuery && matchesType && matchesCountry && matchesDomestic
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val systemLogs: StateFlow<List<com.example.diagnostics.LogItem>> = com.example.diagnostics.AppLogger.logsFlow

    fun connect(config: ConfigItem) {
        connectionManager.connect(config)
    }

    fun connectProxy(proxy: ProxyItem) {
        connectionManager.connectProxy(proxy)
    }

    fun connectSmart() {
        connectionManager.connectSmart()
    }

    fun disconnect() {
        connectionManager.disconnect()
    }

    fun fetchAllSources() {
        viewModelScope.launch {
            collector.fetchAllSourcesFlow().collect { progress ->
                _fetchProgress.value = progress
            }
        }
    }

    fun fetchSingleSource(sourceId: Long) {
        viewModelScope.launch {
            collector.fetchSingleSource(sourceId)
        }
    }

    fun fetchAllProxies() {
        viewModelScope.launch {
            proxyManager.fetchAllProxiesFlow().collect { progress ->
                _proxyFetchProgress.value = progress
            }
        }
    }

    fun addCustomSource(name: String, url: String, desc: String) {
        viewModelScope.launch {
            repository.addSource(name, url, desc)
        }
    }

    fun deleteSource(sourceId: Long) {
        viewModelScope.launch {
            repository.deleteSource(sourceId)
        }
    }

    fun toggleSource(source: ConfigSource) {
        viewModelScope.launch {
            repository.updateSource(
                ConfigSourceEntity(
                    id = source.id,
                    name = source.name,
                    url = source.url,
                    isEnabled = !source.isEnabled,
                    isUserCustom = source.isUserCustom,
                    lastFetchTime = source.lastFetchTime,
                    lastFetchStatus = source.lastFetchStatus,
                    configCount = source.configCount,
                    description = source.description
                )
            )
        }
    }

    fun testSingleConfig(config: ConfigItem) {
        viewModelScope.launch {
            tester.testConfig(config)
        }
    }

    fun testAllConfigs() {
        viewModelScope.launch {
            val list = repository.getAllConfigs()
            if (list.isEmpty()) return@launch

            _isTestingAll.value = true
            tester.testBatchHighSpeedFlow(list, concurrency = 40).collect { pair ->
                _testProgress.value = pair
            }
            _isTestingAll.value = false
        }
    }

    fun testAllProxies() {
        viewModelScope.launch {
            val list = repository.getAllProxies()
            if (list.isEmpty()) return@launch

            _isTestingProxies.value = true
            tester.testProxiesBatchFlow(list, concurrency = 40).collect { pair ->
                _proxyTestProgress.value = pair
            }
            _isTestingProxies.value = false
        }
    }

    fun testSingleProxy(proxy: ProxyItem) {
        viewModelScope.launch {
            val start = System.currentTimeMillis()
            var socket: java.net.Socket? = null
            try {
                socket = java.net.Socket()
                socket.soTimeout = 1500
                socket.connect(java.net.InetSocketAddress(proxy.host, proxy.port), 1500)
                val ping = System.currentTimeMillis() - start
                repository.updateProxy(proxy.copy(pingMs = ping, isHealthy = true, lastChecked = System.currentTimeMillis()))
            } catch (_: Exception) {
                repository.updateProxy(proxy.copy(pingMs = -1, isHealthy = false, lastChecked = System.currentTimeMillis()))
            } finally {
                try { socket?.close() } catch (_: Exception) {}
            }
        }
    }

    fun importFromRawText(rawText: String): Int {
        var added = 0
        viewModelScope.launch {
            val parsedConfigs = ConfigParser.parseSubscriptionContent(rawText)
            for (cfg in parsedConfigs) {
                val id = repository.insertOrUpdateConfig(cfg, null)
                if (id > 0) added++
            }

            val parsedProxies = ConfigParser.parseProxiesContent(rawText, "ورودی دستی")
            if (parsedProxies.isNotEmpty()) {
                repository.insertProxies(parsedProxies)
                added += parsedProxies.size
            }
        }
        return added
    }

    fun deleteConfig(config: ConfigItem) {
        viewModelScope.launch {
            repository.deleteConfig(config)
        }
    }

    fun deleteFailedConfigs(onDone: (Int) -> Unit = {}) {
        viewModelScope.launch {
            val count = repository.deleteFailedConfigs()
            onDone(count)
        }
    }

    fun deleteFailedProxies(onDone: (Int) -> Unit = {}) {
        viewModelScope.launch {
            val count = repository.deleteFailedProxies()
            onDone(count)
        }
    }

    fun deleteFailedConfigsAndProxies(onDone: (Int, Int) -> Unit = { _, _ -> }) {
        viewModelScope.launch {
            val (cfgCount, prxCount) = repository.deleteFailedConfigsAndProxies()
            onDone(cfgCount, prxCount)
        }
    }

    fun clearLogs() {
        com.example.diagnostics.AppLogger.clear()
    }

    fun exportLogs(): String {
        return com.example.diagnostics.AppLogger.exportAsText()
    }

    fun restoreDefaultSources() {
        viewModelScope.launch {
            repository.restoreDefaultSources()
            repository.ensureBaseConfigsAndProxiesPopulated()
        }
    }

    fun clearAllConfigs() {
        viewModelScope.launch {
            repository.clearAllConfigs()
        }
    }

    fun clearAllProxies() {
        viewModelScope.launch {
            repository.clearAllProxies()
        }
    }

    fun toggleFavorite(config: ConfigItem) {
        viewModelScope.launch {
            repository.toggleFavorite(config.id, !config.isFavorite)
        }
    }

    fun runDiagnostics() {
        viewModelScope.launch {
            _isDiagnosing.value = true
            val report = diagnostics.runFullDiagnostics()
            _diagnosticReport.value = report
            _isDiagnosing.value = false
        }
    }

    fun setAutoRecovery(enabled: Boolean) {
        autoRecovery.value = enabled
        connectionManager.autoRecoveryEnabled = enabled
    }

    fun updateDnsTunnel(cfg: DnsTunnelConfig) {
        dnsTunnelConfig.value = cfg
    }

    fun updateBridge(cfg: BridgeConfig) {
        bridgeConfig.value = cfg
    }
}
