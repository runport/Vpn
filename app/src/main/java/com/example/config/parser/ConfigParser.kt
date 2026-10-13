package com.example.config.parser

import android.net.Uri
import android.util.Base64
import com.example.core.model.ConfigItem
import com.example.core.model.ProxyItem
import com.example.core.model.ProxyProtocol
import com.example.core.model.ProxyType
import com.example.core.model.TestStatus
import org.json.JSONObject
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

object ConfigParser {

    data class CountryInfo(val code: String, val name: String, val flag: String)

    /**
     * Parses a multi-line raw payload, which can be plain text lines,
     * or a Base64-encoded subscription bundle.
     */
    fun parseSubscriptionContent(content: String): List<ConfigItem> {
        val trimmed = content.trim()
        if (trimmed.isEmpty()) return emptyList()

        val decodedContent = if (!trimmed.startsWith("vless://") &&
            !trimmed.startsWith("vmess://") &&
            !trimmed.startsWith("trojan://") &&
            !trimmed.startsWith("ss://") &&
            !trimmed.startsWith("socks") &&
            !trimmed.startsWith("http")
        ) {
            tryDecodeBase64(trimmed) ?: trimmed
        } else {
            trimmed
        }

        val lines = decodedContent.lines()
        val items = mutableListOf<ConfigItem>()

        for (line in lines) {
            val trimmedLine = line.trim()
            if (trimmedLine.isNotEmpty()) {
                parseSingleUri(trimmedLine)?.let { items.add(it) }
            }
        }
        return items
    }

    fun parseSingleUri(rawUri: String): ConfigItem? {
        val trimmed = rawUri.trim()
        return try {
            when {
                trimmed.startsWith("vless://", ignoreCase = true) -> parseVless(trimmed)
                trimmed.startsWith("vmess://", ignoreCase = true) -> parseVmess(trimmed)
                trimmed.startsWith("trojan://", ignoreCase = true) -> parseTrojan(trimmed)
                trimmed.startsWith("ss://", ignoreCase = true) -> parseShadowsocks(trimmed)
                trimmed.startsWith("socks5://", ignoreCase = true) ||
                        trimmed.startsWith("socks://", ignoreCase = true) -> parseSocks(trimmed)
                trimmed.startsWith("http://", ignoreCase = true) ||
                        trimmed.startsWith("https://", ignoreCase = true) -> parseHttpProxy(trimmed)
                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun parseVless(rawUri: String): ConfigItem? {
        val uri = Uri.parse(rawUri)
        val userInfo = uri.userInfo ?: return null
        val host = uri.host ?: return null
        val port = if (uri.port > 0) uri.port else 443
        val remarks = decodeFragment(uri.fragment, "VLESS-$host:$port")

        val security = uri.getQueryParameter("security") ?: "none"
        val sni = uri.getQueryParameter("sni") ?: uri.getQueryParameter("peer")
        val network = uri.getQueryParameter("type") ?: "tcp"
        val path = uri.getQueryParameter("path")
        val flow = uri.getQueryParameter("flow")

        val hash = calculateNormalizedHash(ProxyProtocol.VLESS, host, port, userInfo, sni, path)
        val country = detectCountry(remarks, host)
        val isBypass = detectBypassNationalNet(security, network, port, sni, remarks)
        val isHalfPrice = detectHalfPriceDomestic(remarks, host, sni)

        return ConfigItem(
            normalizedHash = hash,
            remarks = remarks,
            protocol = ProxyProtocol.VLESS,
            server = host,
            port = port,
            uuidOrPassword = userInfo,
            network = network,
            security = security,
            sni = sni,
            path = path,
            flow = flow,
            rawUri = rawUri,
            countryCode = country.code,
            countryName = country.name,
            countryFlag = country.flag,
            isBypassNationalNet = isBypass,
            isHalfPriceDomestic = isHalfPrice,
            testStatus = TestStatus.STRUCTURALLY_VALID
        )
    }

    private fun parseVmess(rawUri: String): ConfigItem? {
        val base64Data = rawUri.substringAfter("vmess://", "").trim()
        if (base64Data.isEmpty()) return null

        val jsonStr = tryDecodeBase64(base64Data) ?: return null
        val json = JSONObject(jsonStr)

        val server = json.optString("add").ifEmpty { json.optString("host") }
        if (server.isEmpty()) return null
        val port = json.optInt("port", 443)
        val id = json.optString("id")
        if (id.isEmpty()) return null

        val remarks = json.optString("ps", "VMess-$server:$port")
        val network = json.optString("net", "tcp")
        val security = json.optString("tls", "none")
        val sni = json.optString("sni").ifEmpty { json.optString("host") }
        val path = json.optString("path")

        val hash = calculateNormalizedHash(ProxyProtocol.VMESS, server, port, id, sni, path)
        val country = detectCountry(remarks, server)
        val isBypass = detectBypassNationalNet(security, network, port, sni, remarks)
        val isHalfPrice = detectHalfPriceDomestic(remarks, server, sni)

        return ConfigItem(
            normalizedHash = hash,
            remarks = remarks,
            protocol = ProxyProtocol.VMESS,
            server = server,
            port = port,
            uuidOrPassword = id,
            network = network,
            security = if (security.equals("tls", ignoreCase = true)) "tls" else "none",
            sni = sni.ifEmpty { null },
            path = path.ifEmpty { null },
            rawUri = rawUri,
            countryCode = country.code,
            countryName = country.name,
            countryFlag = country.flag,
            isBypassNationalNet = isBypass,
            isHalfPriceDomestic = isHalfPrice,
            testStatus = TestStatus.STRUCTURALLY_VALID
        )
    }

    private fun parseTrojan(rawUri: String): ConfigItem? {
        val uri = Uri.parse(rawUri)
        val password = uri.userInfo ?: return null
        val host = uri.host ?: return null
        val port = if (uri.port > 0) uri.port else 443
        val remarks = decodeFragment(uri.fragment, "Trojan-$host:$port")

        val sni = uri.getQueryParameter("sni") ?: uri.getQueryParameter("peer")
        val network = uri.getQueryParameter("type") ?: "tcp"
        val path = uri.getQueryParameter("path")

        val hash = calculateNormalizedHash(ProxyProtocol.TROJAN, host, port, password, sni, path)
        val country = detectCountry(remarks, host)
        val isBypass = detectBypassNationalNet("tls", network, port, sni, remarks)
        val isHalfPrice = detectHalfPriceDomestic(remarks, host, sni)

        return ConfigItem(
            normalizedHash = hash,
            remarks = remarks,
            protocol = ProxyProtocol.TROJAN,
            server = host,
            port = port,
            uuidOrPassword = password,
            network = network,
            security = "tls",
            sni = sni,
            path = path,
            rawUri = rawUri,
            countryCode = country.code,
            countryName = country.name,
            countryFlag = country.flag,
            isBypassNationalNet = isBypass,
            isHalfPriceDomestic = isHalfPrice,
            testStatus = TestStatus.STRUCTURALLY_VALID
        )
    }

    private fun parseShadowsocks(rawUri: String): ConfigItem? {
        val afterScheme = rawUri.substringAfter("ss://")
        val parts = afterScheme.split("#", limit = 2)
        val remarks = if (parts.size > 1) decodeFragment(parts[1], "Shadowsocks") else "Shadowsocks"
        val mainPart = parts[0]

        val host: String
        val port: Int
        val cipher: String
        val password: String

        if (mainPart.contains("@")) {
            val atParts = mainPart.split("@", limit = 2)
            val authPart = atParts[0]
            val hostPort = atParts[1]

            val decodedAuth = tryDecodeBase64(authPart) ?: authPart
            val authSplit = decodedAuth.split(":", limit = 2)
            if (authSplit.size != 2) return null
            cipher = authSplit[0]
            password = authSplit[1]

            val hp = hostPort.split(":", limit = 2)
            host = hp[0]
            port = hp.getOrNull(1)?.toIntOrNull() ?: 8388
        } else {
            val decoded = tryDecodeBase64(mainPart) ?: return null
            val atParts = decoded.split("@", limit = 2)
            if (atParts.size != 2) return null

            val authSplit = atParts[0].split(":", limit = 2)
            if (authSplit.size != 2) return null
            cipher = authSplit[0]
            password = authSplit[1]

            val hp = atParts[1].split(":", limit = 2)
            host = hp[0]
            port = hp.getOrNull(1)?.toIntOrNull() ?: 8388
        }

        val hash = calculateNormalizedHash(ProxyProtocol.SHADOWSOCKS, host, port, password, cipher, null)
        val country = detectCountry(remarks, host)
        val isHalfPrice = detectHalfPriceDomestic(remarks, host, null)

        return ConfigItem(
            normalizedHash = hash,
            remarks = remarks,
            protocol = ProxyProtocol.SHADOWSOCKS,
            server = host,
            port = port,
            uuidOrPassword = password,
            cipher = cipher,
            rawUri = rawUri,
            countryCode = country.code,
            countryName = country.name,
            countryFlag = country.flag,
            isBypassNationalNet = false,
            isHalfPriceDomestic = isHalfPrice,
            testStatus = TestStatus.STRUCTURALLY_VALID
        )
    }

    private fun parseSocks(rawUri: String): ConfigItem? {
        val uri = Uri.parse(rawUri)
        val host = uri.host ?: return null
        val port = if (uri.port > 0) uri.port else 1080
        val remarks = decodeFragment(uri.fragment, "SOCKS5-$host:$port")
        val userInfo = uri.userInfo ?: ""

        val hash = calculateNormalizedHash(ProxyProtocol.SOCKS5, host, port, userInfo, null, null)
        val country = detectCountry(remarks, host)

        return ConfigItem(
            normalizedHash = hash,
            remarks = remarks,
            protocol = ProxyProtocol.SOCKS5,
            server = host,
            port = port,
            uuidOrPassword = userInfo,
            rawUri = rawUri,
            countryCode = country.code,
            countryName = country.name,
            countryFlag = country.flag,
            testStatus = TestStatus.STRUCTURALLY_VALID
        )
    }

    private fun parseHttpProxy(rawUri: String): ConfigItem? {
        val uri = Uri.parse(rawUri)
        val host = uri.host ?: return null
        val port = if (uri.port > 0) uri.port else 8080
        val remarks = decodeFragment(uri.fragment, "HTTP-$host:$port")
        val userInfo = uri.userInfo ?: ""

        val hash = calculateNormalizedHash(ProxyProtocol.HTTP, host, port, userInfo, null, null)
        val country = detectCountry(remarks, host)

        return ConfigItem(
            normalizedHash = hash,
            remarks = remarks,
            protocol = ProxyProtocol.HTTP,
            server = host,
            port = port,
            uuidOrPassword = userInfo,
            rawUri = rawUri,
            countryCode = country.code,
            countryName = country.name,
            countryFlag = country.flag,
            testStatus = TestStatus.STRUCTURALLY_VALID
        )
    }

    /**
     * Parses raw proxy lists (SOCKS5, HTTP, MTProto) from text sources.
     * Supports formats:
     * - IP:Port
     * - socks5://host:port
     * - http://host:port
     * - tg://proxy?server=...&port=...&secret=...
     */
    fun parseProxiesContent(content: String, sourceName: String = "عمومی"): List<ProxyItem> {
        val proxies = mutableListOf<ProxyItem>()
        for (line in content.lines()) {
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#")) continue

            try {
                when {
                    trimmed.startsWith("tg://proxy", ignoreCase = true) ||
                            trimmed.startsWith("https://t.me/proxy", ignoreCase = true) -> {
                        val uri = Uri.parse(trimmed)
                        val server = uri.getQueryParameter("server") ?: uri.getQueryParameter("host")
                        val port = uri.getQueryParameter("port")?.toIntOrNull() ?: 443
                        val secret = uri.getQueryParameter("secret")
                        if (server != null) {
                            val country = detectCountry(server, server)
                            proxies.add(
                                ProxyItem(
                                    host = server,
                                    port = port,
                                    type = ProxyType.MTPROTO,
                                    secretOrAuth = secret,
                                    countryCode = country.code,
                                    countryName = country.name,
                                    countryFlag = country.flag,
                                    isDomestic = detectHalfPriceDomestic("", server, null),
                                    sourceName = sourceName
                                )
                            )
                        }
                    }
                    trimmed.startsWith("socks5://", ignoreCase = true) -> {
                        val uri = Uri.parse(trimmed)
                        val host = uri.host ?: continue
                        val port = if (uri.port > 0) uri.port else 1080
                        val country = detectCountry(host, host)
                        proxies.add(
                            ProxyItem(
                                host = host,
                                port = port,
                                type = ProxyType.SOCKS5,
                                countryCode = country.code,
                                countryName = country.name,
                                countryFlag = country.flag,
                                isDomestic = detectHalfPriceDomestic("", host, null),
                                sourceName = sourceName
                            )
                        )
                    }
                    trimmed.startsWith("http://", ignoreCase = true) ||
                            trimmed.startsWith("https://", ignoreCase = true) -> {
                        val uri = Uri.parse(trimmed)
                        val host = uri.host ?: continue
                        val port = if (uri.port > 0) uri.port else 8080
                        val country = detectCountry(host, host)
                        proxies.add(
                            ProxyItem(
                                host = host,
                                port = port,
                                type = ProxyType.HTTP,
                                countryCode = country.code,
                                countryName = country.name,
                                countryFlag = country.flag,
                                isDomestic = detectHalfPriceDomestic("", host, null),
                                sourceName = sourceName
                            )
                        )
                    }
                    trimmed.contains(":") && !trimmed.contains(" ") -> {
                        val parts = trimmed.split(":")
                        if (parts.size >= 2) {
                            val host = parts[0].trim()
                            val port = parts[1].trim().toIntOrNull() ?: continue
                            val country = detectCountry(host, host)
                            proxies.add(
                                ProxyItem(
                                    host = host,
                                    port = port,
                                    type = ProxyType.SOCKS5,
                                    countryCode = country.code,
                                    countryName = country.name,
                                    countryFlag = country.flag,
                                    isDomestic = detectHalfPriceDomestic("", host, null),
                                    sourceName = sourceName
                                )
                            )
                        }
                    }
                }
            } catch (_: Exception) {}
        }
        return proxies
    }

    fun detectCountry(remarks: String, host: String): CountryInfo {
        val text = "$remarks $host".lowercase()

        val keywordMatch = when {
            text.contains("🇩🇪") || text.contains("germany") || text.contains("آلمان") || text.contains("-de") || text.contains(".de") || text.contains("hetzner") ->
                CountryInfo("DE", "آلمان", "🇩🇪")
            text.contains("🇺🇸") || text.contains("united states") || text.contains("آمریکا") || text.contains("usa") || text.contains("-us") ->
                CountryInfo("US", "آمریکا", "🇺🇸")
            text.contains("🇫🇷") || text.contains("france") || text.contains("فرانسه") || text.contains("-fr") || text.contains(".fr") || text.contains("ovh") ->
                CountryInfo("FR", "فرانسه", "🇫🇷")
            text.contains("🇳🇱") || text.contains("netherlands") || text.contains("هلند") || text.contains("-nl") || text.contains(".nl") || text.contains("amsterdam") ->
                CountryInfo("NL", "هلند", "🇳🇱")
            text.contains("🇹🇷") || text.contains("turkey") || text.contains("ترکیه") || text.contains("-tr") || text.contains(".tr") || text.contains("istanbul") ->
                CountryInfo("TR", "ترکیه", "🇹🇷")
            text.contains("🇫🇮") || text.contains("finland") || text.contains("فنلاند") || text.contains("-fi") || text.contains(".fi") || text.contains("helsinki") ->
                CountryInfo("FI", "فنلاند", "🇫🇮")
            text.contains("🇬🇧") || text.contains("uk") || text.contains("انگلیس") || text.contains("united kingdom") || text.contains("-gb") || text.contains("london") ->
                CountryInfo("GB", "انگلستان", "🇬🇧")
            text.contains("🇮🇷") || text.contains("iran") || text.contains("ایران") || text.contains("-ir") || text.contains(".ir") || text.contains("mci") || text.contains("irancell") || text.contains("arvan") ->
                CountryInfo("IR", "ایران", "🇮🇷")
            text.contains("🇸🇬") || text.contains("singapore") || text.contains("سنگاپور") || text.contains("-sg") ->
                CountryInfo("SG", "سنگاپور", "🇸🇬")
            text.contains("🇨🇦") || text.contains("canada") || text.contains("کانادا") || text.contains("-ca") ->
                CountryInfo("CA", "کانادا", "🇨🇦")
            text.contains("🇸🇪") || text.contains("sweden") || text.contains("سوئد") || text.contains("-se") ->
                CountryInfo("SE", "سوئد", "🇸🇪")
            text.contains("🇨🇭") || text.contains("switzerland") || text.contains("سوئیس") || text.contains("-ch") ->
                CountryInfo("CH", "سوئیس", "🇨🇭")
            text.contains("🇮🇹") || text.contains("italy") || text.contains("ایتالیا") || text.contains("-it") ->
                CountryInfo("IT", "ایتالیا", "🇮🇹")
            text.contains("🇷🇺") || text.contains("russia") || text.contains("روسیه") || text.contains("-ru") ->
                CountryInfo("RU", "روسیه", "🇷🇺")
            text.contains("🇯🇵") || text.contains("japan") || text.contains("ژاپن") || text.contains("-jp") ->
                CountryInfo("JP", "ژاپن", "🇯🇵")
            else -> null
        }

        if (keywordMatch != null) return keywordMatch

        // Heuristic IP Subnet Detection for Proxies and raw IP Nodes
        val cleanHost = host.trim()
        val firstOctet = cleanHost.substringBefore(".").toIntOrNull()
        if (firstOctet != null) {
            when {
                cleanHost.startsWith("194.225.") || cleanHost.startsWith("185.143.") || cleanHost.startsWith("5.160.") ||
                        cleanHost.startsWith("91.98.") || cleanHost.startsWith("94.182.") || cleanHost.startsWith("2.144.") ->
                    return CountryInfo("IR", "ایران", "🇮🇷")
                cleanHost.startsWith("185.190.") || cleanHost.startsWith("116.202.") || cleanHost.startsWith("159.69.") ||
                        cleanHost.startsWith("138.201.") || cleanHost.startsWith("88.198.") || cleanHost.startsWith("78.46.") ->
                    return CountryInfo("DE", "آلمان", "🇩🇪")
                cleanHost.startsWith("51.15.") || cleanHost.startsWith("141.94.") || cleanHost.startsWith("141.95.") || cleanHost.startsWith("54.36.") ->
                    return CountryInfo("FR", "فرانسه", "🇫🇷")
                cleanHost.startsWith("45.14.") || cleanHost.startsWith("185.220.") || cleanHost.startsWith("193.106.") ->
                    return CountryInfo("NL", "هلند", "🇳🇱")
                cleanHost.startsWith("176.223.") || cleanHost.startsWith("88.255.") || cleanHost.startsWith("212.156.") ->
                    return CountryInfo("TR", "ترکیه", "🇹🇷")
                cleanHost.startsWith("95.216.") || cleanHost.startsWith("65.21.") || cleanHost.startsWith("65.108.") ->
                    return CountryInfo("FI", "فنلاند", "🇫🇮")
                cleanHost.startsWith("104.") || cleanHost.startsWith("172.") || cleanHost.startsWith("198.") || cleanHost.startsWith("23.") ->
                    return CountryInfo("US", "آمریکا", "🇺🇸")
            }

            // Deterministic hash assignment across world hubs so no proxy is an unknown orphan
            val hubList = listOf(
                CountryInfo("DE", "آلمان", "🇩🇪"),
                CountryInfo("NL", "هلند", "🇳🇱"),
                CountryInfo("US", "آمریکا", "🇺🇸"),
                CountryInfo("FR", "فرانسه", "🇫🇷"),
                CountryInfo("TR", "ترکیه", "🇹🇷"),
                CountryInfo("FI", "فنلاند", "🇫🇮"),
                CountryInfo("GB", "انگلستان", "🇬🇧")
            )
            val idx = kotlin.math.abs(cleanHost.hashCode()) % hubList.size
            return hubList[idx]
        }

        return CountryInfo("DE", "آلمان", "🇩🇪")
    }

    fun detectBypassNationalNet(security: String, network: String, port: Int, sni: String?, remarks: String): Boolean {
        val s = security.lowercase()
        val net = network.lowercase()
        val sn = (sni ?: "").lowercase()
        val rem = remarks.lowercase()

        // Reality protocol natively evades national DPI
        if (s == "reality" || rem.contains("reality")) return true

        // Clean CDN WebSocket or gRPC on standard HTTPS ports
        if ((net == "ws" || net == "grpc") && port in listOf(443, 8443, 2053, 2083, 2087, 2096)) {
            if (s == "tls" || s == "reality") return true
        }

        // Recognized clean domains / fronting or bypass tags
        if (sn.contains("cloudflare") || sn.contains("workers.dev") || sn.contains("cloudfront") ||
            rem.contains("ملی") || rem.contains("bypass") || rem.contains("تمیز") || rem.contains("clean")) {
            return true
        }

        return false
    }

    fun detectHalfPriceDomestic(remarks: String, host: String, sni: String?): Boolean {
        val rem = remarks.lowercase()
        val h = host.lowercase()
        val sn = (sni ?: "").lowercase()

        if (rem.contains("نیم بها") || rem.contains("نیم‌بها") || rem.contains("نیمبها") ||
            rem.contains("داخلی") || rem.contains("iran") || rem.contains("ایران") || rem.contains("ir")) {
            return true
        }

        if (h.endsWith(".ir") || sn.endsWith(".ir") ||
            h.contains("arvan") || sn.contains("arvan") ||
            h.contains("derak") || sn.contains("derak") ||
            h.contains("asiatech") || h.contains("shatel") || h.contains("mci") || h.contains("irancell")) {
            return true
        }

        return false
    }

    fun calculateNormalizedHash(
        protocol: ProxyProtocol,
        server: String,
        port: Int,
        auth: String,
        sniOrCipher: String?,
        path: String?
    ): String {
        val normalized = "${protocol.name.lowercase()}://${server.lowercase()}:$port/$auth/${sniOrCipher ?: ""}/${path ?: ""}"
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(normalized.toByteArray(StandardCharsets.UTF_8))
        return hashBytes.joinToString("") { "%02x".format(it) }
    }

    private fun tryDecodeBase64(input: String): String? {
        val clean = input.trim().replace("\n", "").replace("\r", "")
        val padded = when (clean.length % 4) {
            2 -> "$clean=="
            3 -> "$clean="
            else -> clean
        }
        return try {
            val bytes = Base64.decode(padded, Base64.URL_SAFE or Base64.NO_WRAP)
            String(bytes, StandardCharsets.UTF_8)
        } catch (_: Exception) {
            try {
                val bytes = Base64.decode(padded, Base64.DEFAULT)
                String(bytes, StandardCharsets.UTF_8)
            } catch (_: Exception) {
                null
            }
        }
    }

    private fun decodeFragment(fragment: String?, defaultRemarks: String): String {
        if (fragment.isNullOrBlank()) return defaultRemarks
        return try {
            URLDecoder.decode(fragment, StandardCharsets.UTF_8.name())
        } catch (_: Exception) {
            fragment
        }
    }
}
