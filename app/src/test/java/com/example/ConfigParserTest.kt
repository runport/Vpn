package com.example

import com.example.config.parser.ConfigParser
import com.example.core.model.ProxyProtocol
import com.example.core.model.ProxyType
import com.example.core.model.TestStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ConfigParserTest {

    @Test
    fun parseVlessUri_validInput_returnsParsedConfig() {
        val uri = "vless://a1b2c3d4-e5f6-7890-abcd-ef1234567890@198.51.100.1:443?security=tls&sni=fast.example.com&type=ws&path=%2Fws#Germany-Fast"
        val item = ConfigParser.parseSingleUri(uri)

        assertNotNull(item)
        item?.let {
            assertEquals(ProxyProtocol.VLESS, it.protocol)
            assertEquals("198.51.100.1", it.server)
            assertEquals(443, it.port)
            assertEquals("a1b2c3d4-e5f6-7890-abcd-ef1234567890", it.uuidOrPassword)
            assertEquals("tls", it.security)
            assertEquals("fast.example.com", it.sni)
            assertEquals("Germany-Fast", it.remarks)
            assertEquals("DE", it.countryCode)
            assertEquals(TestStatus.STRUCTURALLY_VALID, it.testStatus)
            assertTrue(it.normalizedHash.isNotBlank())
        }
    }

    @Test
    fun parseTrojanUri_validInput_returnsParsedConfig() {
        val uri = "trojan://mypassword123@203.0.113.5:443?sni=trojan.example.com#Trojan-US"
        val item = ConfigParser.parseSingleUri(uri)

        assertNotNull(item)
        item?.let {
            assertEquals(ProxyProtocol.TROJAN, it.protocol)
            assertEquals("203.0.113.5", it.server)
            assertEquals(443, it.port)
            assertEquals("mypassword123", it.uuidOrPassword)
            assertEquals("Trojan-US", it.remarks)
            assertEquals("tls", it.security)
            assertEquals("US", it.countryCode)
        }
    }

    @Test
    fun detectBypassNationalNet_realitySecurity_returnsTrue() {
        val uri = "vless://uuid-123@1.2.3.4:443?security=reality&sni=yahoo.com&type=tcp#BypassNode"
        val item = ConfigParser.parseSingleUri(uri)

        assertNotNull(item)
        assertTrue(item?.isBypassNationalNet == true)
    }

    @Test
    fun detectHalfPrice_irDomainOrKeyword_returnsTrue() {
        val uri = "vless://uuid-123@speed.arvancloud.ir:443?security=tls#Iran-HalfPrice"
        val item = ConfigParser.parseSingleUri(uri)

        assertNotNull(item)
        assertTrue(item?.isHalfPriceDomestic == true)
    }

    @Test
    fun parseProxiesContent_validList_returnsParsedProxies() {
        val raw = """
            socks5://192.168.1.1:1080
            http://192.168.1.2:8080
            tg://proxy?server=192.168.1.3&port=443&secret=dd00112233
            1.2.3.4:8888
        """.trimIndent()

        val proxies = ConfigParser.parseProxiesContent(raw, "تست")
        assertEquals(4, proxies.size)
        assertEquals(ProxyType.SOCKS5, proxies[0].type)
        assertEquals(ProxyType.HTTP, proxies[1].type)
        assertEquals(ProxyType.MTPROTO, proxies[2].type)
    }

    @Test
    fun normalizedHash_sameEndpointDifferentRemarks_producesSameHash() {
        val uri1 = "vless://uuid-1@example.com:443?security=tls#Mirror1"
        val uri2 = "vless://uuid-1@example.com:443?security=tls#Mirror2"

        val item1 = ConfigParser.parseSingleUri(uri1)
        val item2 = ConfigParser.parseSingleUri(uri2)

        assertNotNull(item1)
        assertNotNull(item2)
        assertEquals(item1?.normalizedHash, item2?.normalizedHash)
    }

    @Test
    fun parseInvalidScheme_returnsNull() {
        val uri = "invalid://something_broken"
        val item = ConfigParser.parseSingleUri(uri)
        assertNull(item)
    }
}
