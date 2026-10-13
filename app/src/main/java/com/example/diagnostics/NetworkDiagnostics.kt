package com.example.diagnostics

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.example.core.model.DiagnosticLevel
import com.example.core.model.DiagnosticReport
import com.example.core.model.DiagnosticStep
import com.example.storage.AzadiRepository
import com.example.vpn.AzadiVpnService
import com.example.vpn.VpnServiceStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL

class NetworkDiagnostics(
    private val context: Context,
    private val repository: AzadiRepository
) {

    suspend fun runFullDiagnostics(): DiagnosticReport = withContext(Dispatchers.IO) {
        val steps = mutableListOf<DiagnosticStep>()

        // 1. Device Local Network Check
        val connManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = connManager.activeNetwork
        val capabilities = connManager.getNetworkCapabilities(activeNetwork)

        val networkType = when {
            capabilities == null -> "قطع / بدون اتصال"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "وای‌فای (Wi-Fi)"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "داده همراه (Cellular)"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "شبکه سیمی (Ethernet)"
            else -> "سایر اتصالات"
        }

        if (capabilities == null) {
            steps.add(
                DiagnosticStep(
                    title = "اتصال به شبکه محلی",
                    detail = "هیچ رابط شبکه‌ای روی دستگاه فعال نیست.",
                    level = DiagnosticLevel.ERROR
                )
            )
            return@withContext DiagnosticReport(
                networkType = "قطع",
                localIp = null,
                hasDnsResolution = false,
                isCaptivePortal = false,
                isDirectGlobalReachable = false,
                isCurrentEndpointReachable = false,
                isTunnelActive = false,
                isTunnelGlobalVerified = false,
                overallStatusPersian = "شبکه دستگاه کاملاً قطع است.",
                steps = steps
            )
        } else {
            steps.add(
                DiagnosticStep(
                    title = "نوع شبکه فیزیکی",
                    detail = "دستگاه به $networkType متصل است.",
                    level = DiagnosticLevel.SUCCESS
                )
            )
        }

        // 2. DNS Resolution Test
        var dnsWorking = false
        try {
            val addresses = InetAddress.getAllByName("cloudflare.com")
            if (addresses.isNotEmpty()) {
                dnsWorking = true
                steps.add(
                    DiagnosticStep(
                        title = "سیستم نام دامنه (DNS)",
                        detail = "تفکیک نام دامنه cloudflare.com با موفقیت انجام شد (${addresses.first().hostAddress}).",
                        level = DiagnosticLevel.SUCCESS
                    )
                )
            }
        } catch (e: Exception) {
            steps.add(
                DiagnosticStep(
                    title = "سیستم نام دامنه (DNS)",
                    detail = "تفکیک نام دامنه با خطا مواجه شد: ${e.message ?: "پاسخی دریافت نشد"}. احتمال اختلال DNS وجود دارد.",
                    level = DiagnosticLevel.WARNING
                )
            )
        }

        // 3. Captive Portal Detection
        var isCaptive = false
        if (capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL)) {
            isCaptive = true
            steps.add(
                DiagnosticStep(
                    title = "پرتال لاگین شبکه (Captive Portal)",
                    detail = "شناسایی شد! برای دسترسی به اینترنت باید ابتدا در شبکه لاگین کنید.",
                    level = DiagnosticLevel.WARNING
                )
            )
        }

        // 4. Direct Global TCP Connection Probe
        var directTcpSuccess = false
        try {
            val probeSocket = Socket()
            probeSocket.connect(InetSocketAddress("1.1.1.1", 53), 2500)
            probeSocket.close()
            directTcpSuccess = true
            steps.add(
                DiagnosticStep(
                    title = "اتصال مستقیم TCP به سرور بین‌المللی",
                    detail = "ارتباط مستقیم با آدرس 1.1.1.1:53 با موفقیت برقرار شد.",
                    level = DiagnosticLevel.SUCCESS
                )
            )
        } catch (e: Exception) {
            steps.add(
                DiagnosticStep(
                    title = "اتصال مستقیم TCP به سرور بین‌المللی",
                    detail = "ارتباط مستقیم ناموفق بود. ترافیک بین‌المللی مستقیم مسدود یا فیلتر است.",
                    level = DiagnosticLevel.INFO
                )
            )
        }

        // 5. Direct HTTPS External Probe
        var directGlobalVerified = false
        try {
            val url = URL("https://cp.cloudflare.com/generate_204")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 3000
            conn.readTimeout = 3000
            conn.instanceFollowRedirects = false
            val responseCode = conn.responseCode
            if (responseCode == 204 || responseCode == 200) {
                directGlobalVerified = true
                steps.add(
                    DiagnosticStep(
                        title = "آزمون وب بین‌المللی (HTTPS)",
                        detail = "دسترسی مستقیم به اینترنت بین‌المللی بدون فیلتر فعال است.",
                        level = DiagnosticLevel.SUCCESS
                    )
                )
            }
            conn.disconnect()
        } catch (_: Exception) {
            steps.add(
                DiagnosticStep(
                    title = "آزمون وب بین‌المللی (HTTPS)",
                    detail = "دسترسی مستقیم وب مسدود است (پاسخ HTTP 204 دریافت نشد).",
                    level = DiagnosticLevel.INFO
                )
            )
        }

        // 6. VPN Tunnel Interface Status
        val isTunnelActive = AzadiVpnService.vpnStatusFlow.value is VpnServiceStatus.Running
        steps.add(
            DiagnosticStep(
                title = "وضعیت رابط تونل VPN",
                detail = if (isTunnelActive) "تونل آزادیار فعال است و ترافیک را دریافت می‌کند." else "تونل آزادیار در حال حاضر غیرفعال است.",
                level = if (isTunnelActive) DiagnosticLevel.SUCCESS else DiagnosticLevel.INFO
            )
        )

        // Overall status computation
        val overallStatus = when {
            isTunnelActive && directGlobalVerified -> "دسترسی خارجی از مسیر تونل تأیید شده است."
            isTunnelActive -> "تونل فعال است، اما دسترسی خارجی به تمام مقاصد تأیید نشده است."
            directGlobalVerified -> "اینترنت مستقیم بدون محدودیت در دسترس است."
            dnsWorking && !directTcpSuccess -> "اینترنت محدود یا ناپایدار است (فیلترینگ ترافیک مستقیم)."
            !dnsWorking && !directTcpSuccess -> "مسیر خارجی در دسترس نیست یا شبکه کاملاً محدود است."
            else -> "نتیجه قطعی نیست. لطفاً آزمایش مجدد انجام دهید."
        }

        // Log result to database
        repository.logDiagnostic(
            networkType = networkType,
            statusPersian = overallStatus,
            isTunnelActive = isTunnelActive,
            detailsJson = steps.joinToString(" | ") { "${it.title}: ${it.detail}" }
        )

        DiagnosticReport(
            networkType = networkType,
            localIp = null,
            hasDnsResolution = dnsWorking,
            isCaptivePortal = isCaptive,
            isDirectGlobalReachable = directGlobalVerified,
            isCurrentEndpointReachable = directTcpSuccess,
            isTunnelActive = isTunnelActive,
            isTunnelGlobalVerified = isTunnelActive && directGlobalVerified,
            overallStatusPersian = overallStatus,
            steps = steps
        )
    }
}
