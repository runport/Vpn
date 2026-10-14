package com.example.config.settings

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import com.example.core.model.AppPackageInfo
import com.example.core.model.RoutingMode
import com.example.core.model.TestMode
import com.example.core.model.V2raySettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

class V2raySettingsManager(private val context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("azadi_v2ray_settings", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(loadSettings())
    val settings = _settings.asStateFlow()

    fun getSettings(): V2raySettings = _settings.value

    fun updateSettings(newSettings: V2raySettings) {
        _settings.value = newSettings
        saveSettings(newSettings)
    }

    private fun loadSettings(): V2raySettings {
        val routingModeStr = prefs.getString("routing_mode", RoutingMode.BYPASS_IRAN_LAN.name) ?: RoutingMode.BYPASS_IRAN_LAN.name
        val routingMode = try { RoutingMode.valueOf(routingModeStr) } catch (_: Exception) { RoutingMode.BYPASS_IRAN_LAN }

        val testModeStr = prefs.getString("test_mode", TestMode.REAL_DELAY.name) ?: TestMode.REAL_DELAY.name
        val testMode = try { TestMode.valueOf(testModeStr) } catch (_: Exception) { TestMode.REAL_DELAY }

        val perAppEnabled = prefs.getBoolean("per_app_enabled", false)
        val perAppBypass = prefs.getBoolean("per_app_bypass", false)
        val selectedApps = prefs.getStringSet("selected_apps", emptySet()) ?: emptySet()

        val realDelayUrl = prefs.getString("real_delay_url", "https://www.gstatic.com/generate_204") ?: "https://www.gstatic.com/generate_204"
        val autoDelete = prefs.getBoolean("auto_delete_invalid", true)
        val autoSort = prefs.getBoolean("auto_sort_ping", true)

        val remoteDns = prefs.getString("remote_dns", "1.1.1.1") ?: "1.1.1.1"
        val domesticDns = prefs.getString("domestic_dns", "223.5.5.5") ?: "223.5.5.5"
        val enableFakeDns = prefs.getBoolean("enable_fake_dns", true)

        val mtu = prefs.getInt("mtu", 1400)
        val enableUdp = prefs.getBoolean("enable_udp", true)
        val allowLanSharing = prefs.getBoolean("allow_lan_sharing", false)
        val localSocksPort = prefs.getInt("local_socks_port", 10808)
        val localHttpPort = prefs.getInt("local_http_port", 10809)
        val autoReconnect = prefs.getBoolean("auto_reconnect", true)

        return V2raySettings(
            routingMode = routingMode,
            perAppProxyEnabled = perAppEnabled,
            perAppProxyBypassMode = perAppBypass,
            selectedApps = selectedApps,
            testMode = testMode,
            realDelayTestUrl = realDelayUrl,
            autoDeleteInvalidAfterTest = autoDelete,
            autoSortAfterTest = autoSort,
            remoteDns = remoteDns,
            domesticDns = domesticDns,
            enableFakeDns = enableFakeDns,
            mtu = mtu,
            enableUdp = enableUdp,
            allowLanSharing = allowLanSharing,
            localSocksPort = localSocksPort,
            localHttpPort = localHttpPort,
            autoReconnect = autoReconnect
        )
    }

    private fun saveSettings(s: V2raySettings) {
        prefs.edit().apply {
            putString("routing_mode", s.routingMode.name)
            putString("test_mode", s.testMode.name)
            putBoolean("per_app_enabled", s.perAppProxyEnabled)
            putBoolean("per_app_bypass", s.perAppProxyBypassMode)
            putStringSet("selected_apps", s.selectedApps)
            putString("real_delay_url", s.realDelayTestUrl)
            putBoolean("auto_delete_invalid", s.autoDeleteInvalidAfterTest)
            putBoolean("auto_sort_ping", s.autoSortAfterTest)
            putString("remote_dns", s.remoteDns)
            putString("domestic_dns", s.domesticDns)
            putBoolean("enable_fake_dns", s.enableFakeDns)
            putInt("mtu", s.mtu)
            putBoolean("enable_udp", s.enableUdp)
            putBoolean("allow_lan_sharing", s.allowLanSharing)
            putInt("local_socks_port", s.localSocksPort)
            putInt("local_http_port", s.localHttpPort)
            putBoolean("auto_reconnect", s.autoReconnect)
            apply()
        }
    }

    suspend fun getInstalledApplications(): List<AppPackageInfo> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val myPackageName = context.packageName
        try {
            val packages = pm.getInstalledPackages(0)
            packages.mapNotNull { pkg ->
                val appInfo = pkg.applicationInfo ?: return@mapNotNull null
                if (pkg.packageName == myPackageName) return@mapNotNull null
                val appName = try {
                    pm.getApplicationLabel(appInfo).toString()
                } catch (_: Exception) {
                    pkg.packageName
                }
                val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                AppPackageInfo(
                    packageName = pkg.packageName,
                    appName = if (appName.isNotBlank()) appName else pkg.packageName,
                    isSystem = isSystem
                )
            }.sortedWith(compareBy({ it.isSystem }, { it.appName.lowercase() }))
        } catch (_: Exception) {
            emptyList()
        }
    }
}
