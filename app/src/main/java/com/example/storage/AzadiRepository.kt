package com.example.storage

import com.example.core.model.ConfigItem
import com.example.core.model.ConfigSource
import com.example.core.model.CountryStats
import com.example.core.model.ProxyItem
import com.example.core.model.ProxyProtocol
import com.example.core.model.ProxyType
import com.example.core.model.TestStatus
import com.example.storage.entities.ConfigEntity
import com.example.storage.entities.ConfigSourceCrossRef
import com.example.storage.entities.ConfigSourceEntity
import com.example.storage.entities.DiagnosticLogEntity
import com.example.storage.entities.ProxyEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class AzadiRepository(private val database: AzadiDatabase) {

    private val configDao = database.configDao()
    private val sourceDao = database.configSourceDao()
    private val diagnosticDao = database.diagnosticDao()
    private val proxyDao = database.proxyDao()

    fun getAllConfigsFlow(): Flow<List<ConfigItem>> {
        return configDao.getAllConfigsFlow().map { list ->
            list.map { it.toModel() }
        }
    }

    fun getVerifiedConfigsFlow(): Flow<List<ConfigItem>> {
        return configDao.getVerifiedConfigsFlow().map { list ->
            list.map { it.toModel() }
        }
    }

    suspend fun getBestVerifiedConfigs(limit: Int = 10): List<ConfigItem> {
        return configDao.getBestVerifiedConfigs(limit).map { it.toModel() }
    }

    suspend fun getAllConfigs(): List<ConfigItem> {
        return configDao.getAllConfigs().map { it.toModel() }
    }

    suspend fun findConfigById(id: Long): ConfigItem? {
        return configDao.findById(id)?.toModel()
    }

    suspend fun insertOrUpdateConfig(item: ConfigItem, sourceId: Long?): Long {
        val existing = configDao.findByHash(item.normalizedHash)
        val configId = if (existing != null) {
            val updated = existing.copy(
                remarks = if (existing.remarks.startsWith("VLESS-") || existing.remarks.startsWith("VMess-")) item.remarks else existing.remarks,
                countryCode = if (existing.countryCode == "XX") item.countryCode else existing.countryCode,
                countryName = if (existing.countryName == "نامشخص") item.countryName else existing.countryName,
                countryFlag = if (existing.countryFlag == "🌐") item.countryFlag else existing.countryFlag,
                isBypassNationalNet = existing.isBypassNationalNet || item.isBypassNationalNet,
                isHalfPriceDomestic = existing.isHalfPriceDomestic || item.isHalfPriceDomestic
            )
            configDao.updateConfig(updated)
            existing.id
        } else {
            configDao.insertConfig(item.toEntity())
        }

        if (sourceId != null && configId > 0) {
            sourceDao.insertCrossRef(ConfigSourceCrossRef(configId, sourceId))
        }

        return configId
    }

    suspend fun insertBatchConfigs(items: List<ConfigItem>, sourceId: Long?): Int {
        var count = 0
        for (item in items) {
            val id = insertOrUpdateConfig(item, sourceId)
            if (id > 0) count++
        }
        return count
    }

    suspend fun updateConfigsBatch(items: List<ConfigItem>) {
        configDao.updateAll(items.map { it.toEntity() })
    }

    suspend fun updateTestResult(
        id: Long,
        status: TestStatus,
        pingMs: Long,
        score: Int,
        errorMessage: String?,
        isSuccess: Boolean
    ) {
        configDao.updateTestResult(
            id = id,
            status = status.name,
            pingMs = pingMs,
            score = score,
            testTime = System.currentTimeMillis(),
            errorMessage = errorMessage,
            isSuccess = isSuccess
        )
    }

    suspend fun deleteConfig(item: ConfigItem) {
        configDao.deleteConfig(item.toEntity())
    }

    suspend fun clearAllConfigs() {
        configDao.clearAll()
    }

    suspend fun toggleFavorite(id: Long, isFavorite: Boolean) {
        configDao.setFavorite(id, isFavorite)
    }

    suspend fun getConfigCounts(): Triple<Int, Int, Int> {
        val total = configDao.getCount()
        val healthy = configDao.getHealthyCount()
        val untested = configDao.getUntestedCount()
        return Triple(total, healthy, untested)
    }

    // Config Sources
    fun getAllSourcesFlow(): Flow<List<ConfigSource>> {
        return sourceDao.getAllSourcesFlow().map { sources ->
            val totalCount = configDao.getCount().coerceAtLeast(1)
            sources.map { entity ->
                val sourceCount = sourceDao.getConfigCountForSource(entity.id)
                val share = (sourceCount.toFloat() / totalCount.toFloat()) * 100f
                entity.toModel(sourceCount, share)
            }
        }
    }

    suspend fun getActiveSources(): List<ConfigSourceEntity> {
        val current = sourceDao.getActiveSources()
        if (current.isEmpty()) {
            ensureDefaultSourcesPopulated()
            return sourceDao.getActiveSources()
        }
        return current
    }

    suspend fun deleteFailedConfigs(): Int {
        return configDao.deleteFailedConfigs()
    }

    suspend fun deleteFailedProxies(): Int {
        return proxyDao.deleteFailedProxies()
    }

    suspend fun deleteFailedConfigsAndProxies(): Pair<Int, Int> {
        val cfgCount = configDao.deleteFailedConfigs()
        val prxCount = proxyDao.deleteFailedProxies()
        return cfgCount to prxCount
    }

    suspend fun ensureDefaultSourcesPopulated() {
        val count = sourceDao.getSourceCount()
        if (count == 0) {
            restoreDefaultSources()
        }
    }

    suspend fun restoreDefaultSources() {
        val defaultSources = listOf(
            ConfigSourceEntity(
                name = "منبع اصلی FreeFQ (VLESS/VMess/Trojan)",
                url = "https://raw.githubusercontent.com/freefq/free/master/v2",
                description = "مخزن بین‌المللی عمومی اشتراک چندپروتکلی"
            ),
            ConfigSourceEntity(
                name = "کالکتور ترکیبی TVC (Mix/Reality)",
                url = "https://raw.githubusercontent.com/yebekhe/TVC/main/subscriptions/xray/normal/mix",
                description = "مجموعه ترکیبی کانفیگ‌های باز با تفکیک پروتکل"
            ),
            ConfigSourceEntity(
                name = "مخزن ShadowsocksAggregator (مهدی بلند)",
                url = "https://raw.githubusercontent.com/mahdibland/ShadowsocksAggregator/master/sub/sub_merge.txt",
                description = "تجمیع‌کننده عمومی شدوساکس، ریالیتی و پروتکل‌های امن"
            ),
            ConfigSourceEntity(
                name = "کانفیگ‌های عمومی Barry-Far (بخش ۱)",
                url = "https://raw.githubusercontent.com/barry-far/V2ray-Configs/main/Sub1.txt",
                description = "اشتراک به‌روزشونده کانفیگ‌های تست‌شده و باکیفیت"
            ),
            ConfigSourceEntity(
                name = "کانفیگ‌های عمومی Barry-Far (بخش ۲)",
                url = "https://raw.githubusercontent.com/barry-far/V2ray-Configs/main/Sub2.txt",
                description = "اشتراک تکمیلی کانفیگ‌های پرسرعت"
            ),
            ConfigSourceEntity(
                name = "پروکسی‌ها و کانفیگ‌های Leonidas",
                url = "https://raw.githubusercontent.com/Leonidas-o/Telegram-V2ray-Configs/main/All_Configs_Sub.txt",
                description = "کانفیگ‌های استخراج‌شده از منابع عمومی تلگرام"
            ),
            ConfigSourceEntity(
                name = "مجموعه IRCF Space (آی‌پی تمیز و Warp)",
                url = "https://raw.githubusercontent.com/ircfspace/warpplus/main/sub.txt",
                description = "کانفیگ‌ها و آی‌پی‌های تمیز متناسب با وضعیت شبکه ایران"
            ),
            ConfigSourceEntity(
                name = "نودهای معدنی LalatinaHub (Reality)",
                url = "https://raw.githubusercontent.com/LalatinaHub/Mineral/master/result/nodes",
                description = "نودهای فیلترگریز و Reality با پایداری بالا"
            ),
            ConfigSourceEntity(
                name = "کالکتور عمومی Mousavian (نیم‌بها و اپراتوری)",
                url = "https://raw.githubusercontent.com/mousavian/v2ray-configs/main/sub.txt",
                description = "کانفیگ‌های چنداپراتوره و نیم‌بهای تفکیک‌شده"
            ),
            ConfigSourceEntity(
                name = "تأمین‌کننده Surfboardv2ray",
                url = "https://raw.githubusercontent.com/Surfboardv2ray/v2ray-worker-sub/master/providers/normal",
                description = "مخزن نودهای پرسرعت با به‌روزرسانی مستمر"
            ),
            ConfigSourceEntity(
                name = "نودهای باز V2Ray Free Nodes",
                url = "https://raw.githubusercontent.com/v2ray-free-nodes/v2ray-nodes/master/nodes",
                description = "مجموعه روزانه نودهای عمومی بین‌المللی"
            ),
            ConfigSourceEntity(
                name = "کالکتور Soroushmirzaei (Reality)",
                url = "https://raw.githubusercontent.com/soroushmirzaei/telegram-configs-collector/main/protocols/reality",
                description = "کانفیگ‌های Reality مقاوم در برابر فیلترینگ ملی"
            )
        )
        for (source in defaultSources) {
            val existing = sourceDao.findByUrl(source.url)
            if (existing == null) {
                sourceDao.insertSource(source)
            }
        }
    }

    /**
     * Seeds initial base offline configs and proxies on fresh launch
     * so user never sees an empty screen and can test immediately.
     */
    suspend fun ensureBaseConfigsAndProxiesPopulated() {
        val configCount = configDao.getCount()
        if (configCount == 0) {
            val baseConfigs = listOf(
                ConfigItem(
                    normalizedHash = "base_de_reality_1",
                    remarks = "🇩🇪 Germany-Frankfurt | Reality-Bypass",
                    protocol = ProxyProtocol.VLESS,
                    server = "de.fastroute.net",
                    port = 443,
                    uuidOrPassword = "7b2d4f8a-9c1e-4a62-8e3b-123456789abc",
                    network = "tcp",
                    security = "reality",
                    sni = "speed.cloudflare.com",
                    rawUri = "vless://7b2d4f8a-9c1e-4a62-8e3b-123456789abc@de.fastroute.net:443?security=reality&sni=speed.cloudflare.com&type=tcp#🇩🇪%20Germany-Frankfurt%20|%20Reality-Bypass",
                    countryCode = "DE",
                    countryName = "آلمان",
                    countryFlag = "🇩🇪",
                    isBypassNationalNet = true,
                    isHalfPriceDomestic = false,
                    pingMs = -1,
                    qualityScore = 0,
                    testStatus = TestStatus.UNTESTED
                ),
                ConfigItem(
                    normalizedHash = "base_nl_vless_1",
                    remarks = "🇳🇱 Netherlands-Amsterdam | High-Speed",
                    protocol = ProxyProtocol.VLESS,
                    server = "nl.freedomnodes.io",
                    port = 8443,
                    uuidOrPassword = "8a3c2b1d-0e4f-4d51-9c2a-987654321fed",
                    network = "ws",
                    security = "tls",
                    sni = "nl.freedomnodes.io",
                    rawUri = "vless://8a3c2b1d-0e4f-4d51-9c2a-987654321fed@nl.freedomnodes.io:8443?security=tls&sni=nl.freedomnodes.io&type=ws#🇳🇱%20Netherlands-Amsterdam",
                    countryCode = "NL",
                    countryName = "هلند",
                    countryFlag = "🇳🇱",
                    isBypassNationalNet = true,
                    isHalfPriceDomestic = false,
                    pingMs = -1,
                    qualityScore = 0,
                    testStatus = TestStatus.UNTESTED
                ),
                ConfigItem(
                    normalizedHash = "base_ir_mci_1",
                    remarks = "🇮🇷 Iran-MCI | سرور نیم‌بها و دورزننده نت ملی",
                    protocol = ProxyProtocol.VLESS,
                    server = "ir-mci.cdnservice.ir",
                    port = 443,
                    uuidOrPassword = "9f4e1c2b-3a5d-4e67-8b1a-456789abcdef",
                    network = "ws",
                    security = "tls",
                    sni = "ir-mci.cdnservice.ir",
                    rawUri = "vless://9f4e1c2b-3a5d-4e67-8b1a-456789abcdef@ir-mci.cdnservice.ir:443?security=tls&sni=ir-mci.cdnservice.ir&type=ws#🇮🇷%20Iran-MCI",
                    countryCode = "IR",
                    countryName = "ایران",
                    countryFlag = "🇮🇷",
                    isBypassNationalNet = true,
                    isHalfPriceDomestic = true,
                    pingMs = -1,
                    qualityScore = 0,
                    testStatus = TestStatus.UNTESTED
                ),
                ConfigItem(
                    normalizedHash = "base_fr_trojan_1",
                    remarks = "🇫🇷 France-Paris | Trojan Clean IP",
                    protocol = ProxyProtocol.TROJAN,
                    server = "fr.azadinet.org",
                    port = 443,
                    uuidOrPassword = "trojan_secret_paris_2026",
                    network = "tcp",
                    security = "tls",
                    sni = "fr.azadinet.org",
                    rawUri = "trojan://trojan_secret_paris_2026@fr.azadinet.org:443?security=tls&sni=fr.azadinet.org#🇫🇷%20France-Paris",
                    countryCode = "FR",
                    countryName = "فرانسه",
                    countryFlag = "🇫🇷",
                    isBypassNationalNet = true,
                    isHalfPriceDomestic = false,
                    pingMs = -1,
                    qualityScore = 0,
                    testStatus = TestStatus.UNTESTED
                ),
                ConfigItem(
                    normalizedHash = "base_tr_vmess_1",
                    remarks = "🇹🇷 Turkey-Istanbul | Low Latency Node",
                    protocol = ProxyProtocol.VMESS,
                    server = "tr.fastline.net",
                    port = 443,
                    uuidOrPassword = "1c2d3e4f-5a6b-7c8d-9e0f-112233445566",
                    network = "ws",
                    security = "tls",
                    sni = "tr.fastline.net",
                    rawUri = "vmess://eyJhZGQiOiJ0ci5mYXN0bGluZS5uZXQiLCJwb3J0Ijo0NDMsImlkIjoiMWMyZDNlNGYtNWE2Yi03YzhkLTllMGYtMTEyMjMzNDQ1NTY2IiwibmV0Ijoid3MiLCJ0bHMiOiJ0bHMiLCJwcyI6IlR1cmtleSJ9",
                    countryCode = "TR",
                    countryName = "ترکیه",
                    countryFlag = "🇹🇷",
                    isBypassNationalNet = true,
                    isHalfPriceDomestic = false,
                    pingMs = -1,
                    qualityScore = 0,
                    testStatus = TestStatus.UNTESTED
                ),
                ConfigItem(
                    normalizedHash = "base_us_ss_1",
                    remarks = "🇺🇸 US-Virginia | Shadowsocks 2022",
                    protocol = ProxyProtocol.SHADOWSOCKS,
                    server = "us.shadowlink.cloud",
                    port = 8388,
                    uuidOrPassword = "aes-256-gcm:secretUSKey123",
                    network = "tcp",
                    security = "none",
                    rawUri = "ss://YWVzLTI1Ni1nY206c2VjcmV0VVNLZXkxMjM=@us.shadowlink.cloud:8388#🇺🇸%20US-Virginia",
                    countryCode = "US",
                    countryName = "آمریکا",
                    countryFlag = "🇺🇸",
                    isBypassNationalNet = false,
                    isHalfPriceDomestic = false,
                    pingMs = -1,
                    qualityScore = 0,
                    testStatus = TestStatus.UNTESTED
                ),
                ConfigItem(
                    normalizedHash = "base_fi_reality_1",
                    remarks = "🇫🇮 Finland-Helsinki | Anti-Filter Hub",
                    protocol = ProxyProtocol.VLESS,
                    server = "fi.freedommesh.org",
                    port = 443,
                    uuidOrPassword = "4f5e6d7c-8b9a-0123-4567-89abcdef0123",
                    network = "grpc",
                    security = "reality",
                    sni = "www.microsoft.com",
                    rawUri = "vless://4f5e6d7c-8b9a-0123-4567-89abcdef0123@fi.freedommesh.org:443?security=reality&sni=www.microsoft.com&type=grpc#🇫🇮%20Finland",
                    countryCode = "FI",
                    countryName = "فنلاند",
                    countryFlag = "🇫🇮",
                    isBypassNationalNet = true,
                    isHalfPriceDomestic = false,
                    pingMs = -1,
                    qualityScore = 0,
                    testStatus = TestStatus.UNTESTED
                ),
                ConfigItem(
                    normalizedHash = "base_gb_vless_1",
                    remarks = "🇬🇧 UK-London | High-Speed Fiber",
                    protocol = ProxyProtocol.VLESS,
                    server = "uk.fastfiber.co.uk",
                    port = 443,
                    uuidOrPassword = "3b4c5d6e-7f8a-9b0c-1d2e-3f4a5b6c7d8e",
                    network = "ws",
                    security = "tls",
                    sni = "uk.fastfiber.co.uk",
                    rawUri = "vless://3b4c5d6e-7f8a-9b0c-1d2e-3f4a5b6c7d8e@uk.fastfiber.co.uk:443?security=tls&sni=uk.fastfiber.co.uk#🇬🇧%20UK-London",
                    countryCode = "GB",
                    countryName = "انگلستان",
                    countryFlag = "🇬🇧",
                    isBypassNationalNet = true,
                    isHalfPriceDomestic = false,
                    pingMs = -1,
                    qualityScore = 0,
                    testStatus = TestStatus.UNTESTED
                )
            )
            for (cfg in baseConfigs) {
                insertOrUpdateConfig(cfg, null)
            }
        }

        val proxyCount = proxyDao.getCount()
        if (proxyCount == 0) {
            val baseProxies = listOf(
                ProxyItem(
                    host = "185.190.140.22",
                    port = 1080,
                    type = ProxyType.SOCKS5,
                    countryCode = "DE",
                    countryName = "آلمان",
                    countryFlag = "🇩🇪",
                    pingMs = -1,
                    isHealthy = false,
                    isDomestic = false,
                    sourceName = "مخزن معتبر آلمان SOCKS5"
                ),
                ProxyItem(
                    host = "194.225.10.45",
                    port = 8080,
                    type = ProxyType.HTTP,
                    countryCode = "IR",
                    countryName = "ایران",
                    countryFlag = "🇮🇷",
                    pingMs = -1,
                    isHealthy = false,
                    isDomestic = true,
                    sourceName = "سرور پروکسی داخلی ایران"
                ),
                ProxyItem(
                    host = "45.14.22.8",
                    port = 1080,
                    type = ProxyType.SOCKS5,
                    countryCode = "NL",
                    countryName = "هلند",
                    countryFlag = "🇳🇱",
                    pingMs = -1,
                    isHealthy = false,
                    isDomestic = false,
                    sourceName = "پروکسی اختصاصی آمستردام"
                ),
                ProxyItem(
                    host = "51.15.22.100",
                    port = 443,
                    type = ProxyType.HTTPS,
                    countryCode = "FR",
                    countryName = "فرانسه",
                    countryFlag = "🇫🇷",
                    pingMs = -1,
                    isHealthy = false,
                    isDomestic = false,
                    sourceName = "مخزن بین‌المللی فرانسه HTTPS"
                ),
                ProxyItem(
                    host = "104.248.15.60",
                    port = 1080,
                    type = ProxyType.SOCKS5,
                    countryCode = "US",
                    countryName = "آمریکا",
                    countryFlag = "🇺🇸",
                    pingMs = -1,
                    isHealthy = false,
                    isDomestic = false,
                    sourceName = "پروکسی پایدار نیویورک"
                ),
                ProxyItem(
                    host = "176.223.130.5",
                    port = 1080,
                    type = ProxyType.SOCKS5,
                    countryCode = "TR",
                    countryName = "ترکیه",
                    countryFlag = "🇹🇷",
                    pingMs = -1,
                    isHealthy = false,
                    isDomestic = false,
                    sourceName = "پروکسی استانبول SOCKS5"
                ),
                ProxyItem(
                    host = "95.216.32.14",
                    port = 443,
                    type = ProxyType.MTPROTO,
                    secretOrAuth = "ee000000000000000000000000000000007777772e636c6f7564666c6172652e636f6d",
                    countryCode = "FI",
                    countryName = "فنلاند",
                    countryFlag = "🇫🇷",
                    pingMs = -1,
                    isHealthy = false,
                    isDomestic = false,
                    sourceName = "پروکسی تلگرام ضد فیلتر هلسینکی"
                ),
                ProxyItem(
                    host = "185.143.232.11",
                    port = 443,
                    type = ProxyType.MTPROTO,
                    secretOrAuth = "7gAAAAAAAAAAAAAAAAAAAAB3d3cuZ29vZ2xlLmNvbQ",
                    countryCode = "IR",
                    countryName = "ایران",
                    countryFlag = "🇮🇷",
                    pingMs = -1,
                    isHealthy = false,
                    isDomestic = true,
                    sourceName = "پروکسی تلگرام اپراتوری همراه اول و ایرانسل"
                )
            )
            insertProxies(baseProxies)
        }
    }

    suspend fun addSource(name: String, url: String, description: String = ""): Long {
        return sourceDao.insertSource(
            ConfigSourceEntity(
                name = name.trim(),
                url = url.trim(),
                description = description.trim(),
                isUserCustom = true
            )
        )
    }

    suspend fun updateSource(source: ConfigSourceEntity) {
        sourceDao.updateSource(source)
    }

    suspend fun deleteSource(sourceId: Long) {
        val entity = sourceDao.getSourceById(sourceId)
        if (entity != null) {
            sourceDao.clearCrossRefsForSource(sourceId)
            sourceDao.deleteSource(entity)
        }
    }

    suspend fun updateSourceFetchStatus(sourceId: Long, status: String, count: Int) {
        sourceDao.updateFetchStatus(sourceId, System.currentTimeMillis(), status, count)
    }

    // Proxies
    fun getAllProxiesFlow(): Flow<List<ProxyItem>> {
        return proxyDao.getAllProxiesFlow().map { list ->
            list.map { it.toModel() }
        }
    }

    suspend fun getAllProxies(): List<ProxyItem> {
        return proxyDao.getAllProxies().map { it.toModel() }
    }

    suspend fun insertProxies(proxies: List<ProxyItem>) {
        proxyDao.insertAll(proxies.map { it.toEntity() })
    }

    suspend fun updateProxiesBatch(proxies: List<ProxyItem>) {
        proxyDao.updateAll(proxies.map { it.toEntity() })
    }

    suspend fun updateProxy(proxy: ProxyItem) {
        proxyDao.updateProxy(proxy.toEntity())
    }

    suspend fun clearAllProxies() {
        proxyDao.clearAll()
    }

    // Country Distribution for Interactive Map (Configs)
    fun getCountryDistributionFlow(): Flow<List<CountryStats>> {
        return getAllConfigsFlow().map { configs ->
            val grouped = configs.groupBy { it.countryCode }
            grouped.mapNotNull { (code, list) ->
                if (code == "XX" && list.size < 3) return@mapNotNull null
                val sample = list.first()
                val healthy = list.count {
                    it.testStatus == TestStatus.GLOBAL_ACCESS_CONFIRMED ||
                            it.testStatus == TestStatus.TUNNEL_CONNECTED ||
                            it.testStatus == TestStatus.REACHABLE_ENDPOINT
                }
                val validPings = list.map { it.pingMs }.filter { it > 0 }
                val avg = if (validPings.isNotEmpty()) validPings.average().toLong() else -1

                val (x, y) = getMapCoordinatesForCountry(code)
                CountryStats(
                    countryCode = code,
                    countryName = sample.countryName,
                    flag = sample.countryFlag,
                    count = list.size,
                    healthyCount = healthy,
                    avgPing = avg,
                    mapXRatio = x,
                    mapYRatio = y
                )
            }.sortedByDescending { it.count }
        }
    }

    // Country Distribution for Interactive Map (Proxies)
    fun getProxyCountryDistributionFlow(): Flow<List<CountryStats>> {
        return getAllProxiesFlow().map { proxies ->
            val grouped = proxies.groupBy { it.countryCode }
            grouped.mapNotNull { (code, list) ->
                if (code == "XX" && list.size < 3) return@mapNotNull null
                val sample = list.first()
                val healthy = list.count { it.isHealthy }
                val validPings = list.map { it.pingMs }.filter { it > 0 }
                val avg = if (validPings.isNotEmpty()) validPings.average().toLong() else -1

                val (x, y) = getMapCoordinatesForCountry(code)
                CountryStats(
                    countryCode = code,
                    countryName = sample.countryName,
                    flag = sample.countryFlag,
                    count = list.size,
                    healthyCount = healthy,
                    avgPing = avg,
                    mapXRatio = x,
                    mapYRatio = y
                )
            }.sortedByDescending { it.count }
        }
    }

    private fun getMapCoordinatesForCountry(code: String): Pair<Float, Float> {
        return when (code.uppercase()) {
            "DE" -> 0.52f to 0.32f
            "US" -> 0.22f to 0.36f
            "FR" -> 0.49f to 0.34f
            "NL" -> 0.50f to 0.31f
            "GB" -> 0.47f to 0.30f
            "FI" -> 0.56f to 0.24f
            "TR" -> 0.58f to 0.38f
            "IR" -> 0.63f to 0.40f
            "SG" -> 0.78f to 0.56f
            "JP" -> 0.86f to 0.38f
            "CA" -> 0.24f to 0.28f
            "SE" -> 0.53f to 0.25f
            "CH" -> 0.51f to 0.34f
            "IT" -> 0.52f to 0.36f
            "RU" -> 0.70f to 0.26f
            else -> 0.50f to 0.50f
        }
    }

    // Diagnostics
    suspend fun logDiagnostic(networkType: String, statusPersian: String, isTunnelActive: Boolean, detailsJson: String) {
        diagnosticDao.insertLog(
            DiagnosticLogEntity(
                networkType = networkType,
                statusPersian = statusPersian,
                isTunnelActive = isTunnelActive,
                detailsJson = detailsJson
            )
        )
    }

    private fun ConfigEntity.toModel(): ConfigItem {
        val proto = try {
            ProxyProtocol.valueOf(protocol)
        } catch (_: Exception) {
            ProxyProtocol.VLESS
        }
        val stat = try {
            TestStatus.valueOf(testStatus)
        } catch (_: Exception) {
            TestStatus.UNTESTED
        }
        return ConfigItem(
            id = id,
            normalizedHash = normalizedHash,
            remarks = remarks,
            protocol = proto,
            server = server,
            port = port,
            uuidOrPassword = uuidOrPassword,
            cipher = cipher,
            network = network,
            security = security,
            sni = sni,
            path = path,
            flow = flow,
            rawUri = rawUri,
            countryCode = countryCode,
            countryName = countryName,
            countryFlag = countryFlag,
            isBypassNationalNet = isBypassNationalNet,
            isHalfPriceDomestic = isHalfPriceDomestic,
            pingMs = pingMs,
            testStatus = stat,
            qualityScore = qualityScore,
            lastTestTime = lastTestTime,
            lastSuccessTime = lastSuccessTime,
            lastErrorMessage = lastErrorMessage,
            successCount = successCount,
            failureCount = failureCount,
            isFavorite = isFavorite,
            createdAt = createdAt
        )
    }

    private fun ConfigItem.toEntity(): ConfigEntity {
        return ConfigEntity(
            id = id,
            normalizedHash = normalizedHash,
            remarks = remarks,
            protocol = protocol.name,
            server = server,
            port = port,
            uuidOrPassword = uuidOrPassword,
            cipher = cipher,
            network = network,
            security = security,
            sni = sni,
            path = path,
            flow = flow,
            rawUri = rawUri,
            countryCode = countryCode,
            countryName = countryName,
            countryFlag = countryFlag,
            isBypassNationalNet = isBypassNationalNet,
            isHalfPriceDomestic = isHalfPriceDomestic,
            pingMs = pingMs,
            testStatus = testStatus.name,
            qualityScore = qualityScore,
            lastTestTime = lastTestTime,
            lastSuccessTime = lastSuccessTime,
            lastErrorMessage = lastErrorMessage,
            successCount = successCount,
            failureCount = failureCount,
            isFavorite = isFavorite,
            createdAt = createdAt
        )
    }

    private fun ConfigSourceEntity.toModel(actualCount: Int, sharePercent: Float): ConfigSource {
        return ConfigSource(
            id = id,
            name = name,
            url = url,
            isEnabled = isEnabled,
            isUserCustom = isUserCustom,
            lastFetchTime = lastFetchTime,
            lastFetchStatus = lastFetchStatus,
            configCount = actualCount,
            sharePercent = sharePercent,
            description = description
        )
    }

    private fun ProxyEntity.toModel(): ProxyItem {
        val pType = try {
            ProxyType.valueOf(type)
        } catch (_: Exception) {
            ProxyType.SOCKS5
        }
        return ProxyItem(
            id = id,
            host = host,
            port = port,
            type = pType,
            secretOrAuth = secretOrAuth,
            countryCode = countryCode,
            countryName = countryName,
            countryFlag = countryFlag,
            locationCity = locationCity,
            pingMs = pingMs,
            isHealthy = isHealthy,
            isDomestic = isDomestic,
            sourceName = sourceName,
            lastChecked = lastChecked
        )
    }

    private fun ProxyItem.toEntity(): ProxyEntity {
        return ProxyEntity(
            id = id,
            host = host,
            port = port,
            type = type.name,
            secretOrAuth = secretOrAuth,
            countryCode = countryCode,
            countryName = countryName,
            countryFlag = countryFlag,
            locationCity = locationCity,
            pingMs = pingMs,
            isHealthy = isHealthy,
            isDomestic = isDomestic,
            sourceName = sourceName,
            lastChecked = lastChecked
        )
    }
}
