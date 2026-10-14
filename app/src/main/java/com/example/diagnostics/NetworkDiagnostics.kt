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

/**
 * سیستم عیب‌یابی و پایش پیشرفته شبکه آزادیار
 * با توانایی تفکیک هوشمند شبکه ملی (نت ملی / اینترانت داخلی) از اینترنت جهانی آزاد
 */
class NetworkDiagnostics(
    private val context: Context,
    private val repository: AzadiRepository
) {

    suspend fun runFullDiagnostics(): DiagnosticReport = withContext(Dispatchers.IO) {
        val steps = mutableListOf<DiagnosticStep>()

        // 1. برقراری اتصال محلی لایه فیزیکی (وای‌فای / دیتا همراه)
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
            AppLogger.e("DIAGNOSTIC", "خطای شبکه محلی: هیچ رابط شبکه‌ای فعال نیست.")
            return@withContext DiagnosticReport(
                networkType = "قطع",
                localIp = null,
                hasDnsResolution = false,
                isCaptivePortal = false,
                isDirectGlobalReachable = false,
                isCurrentEndpointReachable = false,
                isTunnelActive = false,
                isTunnelGlobalVerified = false,
                isNationalIntranet = false,
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

        // 2. آزمون دسترسی به شبکه داخلی کشور (سایت‌ها و سرورهای ملی)
        var domesticReachable = false
        var domesticDetails = ""
        try {
            val domesticHosts = listOf("aparat.com", "varzesh3.com", "shaparak.ir")
            for (host in domesticHosts) {
                try {
                    val addrs = InetAddress.getAllByName(host)
                    if (addrs.isNotEmpty()) {
                        val sock = Socket()
                        sock.connect(InetSocketAddress(addrs.first(), 80), 2000)
                        sock.close()
                        domesticReachable = true
                        domesticDetails = "ارتباط مستقیم با سرور داخلی ($host - ${addrs.first().hostAddress}) برقرار شد."
                        break
                    }
                } catch (_: Exception) {
                    // Try next domestic host
                }
            }

            if (domesticReachable) {
                steps.add(
                    DiagnosticStep(
                        title = "شبکه داخلی کشور (اینترانت ملی)",
                        detail = domesticDetails,
                        level = DiagnosticLevel.SUCCESS
                    )
                )
            } else {
                steps.add(
                    DiagnosticStep(
                        title = "شبکه داخلی کشور (اینترانت ملی)",
                        detail = "سرورهای داخلی نیز در دسترس نیستند یا شبکه با قطعی کامل روبروست.",
                        level = DiagnosticLevel.WARNING
                    )
                )
            }
        } catch (e: Exception) {
            steps.add(
                DiagnosticStep(
                    title = "شبکه داخلی کشور",
                    detail = "خطا در تست سرورهای داخلی: ${e.message}",
                    level = DiagnosticLevel.WARNING
                )
            )
        }

        // 3. آزمون تفکیک دامنه بین‌المللی (DNS Resolution)
        var dnsWorking = false
        var dnsPoisoned = false
        val poisonedIps = setOf("10.10.34.35", "127.0.0.1", "0.0.0.0")
        try {
            val addresses = InetAddress.getAllByName("cloudflare.com")
            if (addresses.isNotEmpty()) {
                val resolvedIp = addresses.first().hostAddress ?: ""
                if (poisonedIps.contains(resolvedIp) || resolvedIp.startsWith("10.10.")) {
                    dnsPoisoned = true
                    steps.add(
                        DiagnosticStep(
                            title = "سیستم نام دامنه (DNS)",
                            detail = "هشدار: مسموم‌سازی DNS تشخیص داده شد! دامنه بین‌المللی به آی‌پی سامانه مسدودسازی ($resolvedIp) هدایت شده است.",
                            level = DiagnosticLevel.ERROR
                        )
                    )
                } else {
                    dnsWorking = true
                    steps.add(
                        DiagnosticStep(
                            title = "سیستم نام دامنه (DNS)",
                            detail = "تفکیک نام دامنه cloudflare.com با موفقیت انجام شد ($resolvedIp).",
                            level = DiagnosticLevel.SUCCESS
                        )
                    )
                }
            }
        } catch (e: Exception) {
            steps.add(
                DiagnosticStep(
                    title = "سیستم نام دامنه (DNS بین‌الملل)",
                    detail = "تفکیک نام دامنه‌های خارجی مسدود یا ناموفق است (${e.message ?: "عدم پاسخگویی"}).",
                    level = DiagnosticLevel.WARNING
                )
            )
        }

        // 4. بررسی لاگین پرتال شبکه (Captive Portal)
        var isCaptive = false
        if (capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL)) {
            isCaptive = true
            steps.add(
                DiagnosticStep(
                    title = "پرتال لاگین شبکه (Captive Portal)",
                    detail = "شناسایی شد! برای دسترسی به اینترنت باید ابتدا در پرتال لاگین کنید.",
                    level = DiagnosticLevel.WARNING
                )
            )
        }

        // 5. آزمون اتصال مستقیم TCP به سرور بین‌المللی (خارج از کشور)
        var directTcpSuccess = false
        try {
            val probeSocket = Socket()
            probeSocket.connect(InetSocketAddress("1.1.1.1", 53), 2500)
            probeSocket.close()
            directTcpSuccess = true
            steps.add(
                DiagnosticStep(
                    title = "اتصال مستقیم TCP به سرور خارجی (1.1.1.1)",
                    detail = "ارتباط مستقیم با گیت‌وی و سرور بین‌المللی برقرار است.",
                    level = DiagnosticLevel.SUCCESS
                )
            )
        } catch (e: Exception) {
            steps.add(
                DiagnosticStep(
                    title = "اتصال مستقیم TCP به سرور خارجی (1.1.1.1)",
                    detail = "پکت‌های خروجی به سمت آی‌پی خارجی دراپ می‌شوند (گیت‌وی بین‌الملل زیرساخت مسدود است).",
                    level = DiagnosticLevel.INFO
                )
            )
        }

        // 6. آزمون پروتکل HTTPS بین‌المللی
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
                        detail = "دسترسی آزاد به وب بین‌الملل بدون فیلتر فعال است.",
                        level = DiagnosticLevel.SUCCESS
                    )
                )
            }
            conn.disconnect()
        } catch (_: Exception) {
            steps.add(
                DiagnosticStep(
                    title = "آزمون وب بین‌المللی (HTTPS)",
                    detail = "دسترسی مستقیم به وب آزاد بین‌الملل مسدود است (پاسخ HTTP 204 دریافت نشد).",
                    level = DiagnosticLevel.INFO
                )
            )
        }

        // 7. وضعیت تونل فعال وی‌پی‌ان
        val isTunnelActive = AzadiVpnService.vpnStatusFlow.value is VpnServiceStatus.Running
        steps.add(
            DiagnosticStep(
                title = "وضعیت رابط تونل VPN",
                detail = if (isTunnelActive) "تونل آزادیار فعال است و ترافیک را دریافت می‌کند." else "تونل آزادیار در حال حاضر غیرفعال است.",
                level = if (isTunnelActive) DiagnosticLevel.SUCCESS else DiagnosticLevel.INFO
            )
        )

        // 8. ماتریس هوشمند تشخیص «نت ملی»
        // اگر ترافیک یا سرورهای داخلی در دسترس هستند اما اتصال TCP/HTTPS بین‌الملل مسدود است -> نت ملی
        val isNationalIntranet = domesticReachable && !directTcpSuccess && !directGlobalVerified

        val overallStatus = when {
            isTunnelActive && directGlobalVerified -> "دسترسی خارجی از مسیر تونل امن تأیید شده است."
            isTunnelActive -> "تونل فعال است، اما ارتباط با تمام مقاصد خارجی تأیید نشده است."
            isNationalIntranet -> "وضعیت شبکه ملی (نت ملی): دسترسی به اینترنت بین‌المللی قطع است و فقط شبکه داخلی فعال می‌باشد."
            directGlobalVerified -> "اینترنت مستقیم بدون محدودیت در دسترس است."
            dnsPoisoned -> "شبکه تحت اختلال و مسموم‌سازی شدید DNS قرار دارد."
            dnsWorking && !directTcpSuccess -> "اینترنت محدود یا ناپایدار است (فیلترینگ ترافیک مستقیم)."
            !dnsWorking && !directTcpSuccess -> "مسیر خارجی در دسترس نیست یا شبکه کاملاً قطع است."
            else -> "نتیجه آزمون قطعی نیست. لطفاً مجدداً بررسی نمایید."
        }

        if (isNationalIntranet) {
            AppLogger.w("DIAGNOSTIC", "تشخیص حالت نت ملی (شبکه ملی اطلاعات): سرورهای داخلی باز ولی اینترنت جهانی مسدود است.")
        } else {
            AppLogger.i("DIAGNOSTIC", "نتیجه پایش شبکه: $overallStatus")
        }

        // ثبت گزارش در دیتابیس
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
            isNationalIntranet = isNationalIntranet,
            overallStatusPersian = overallStatus,
            steps = steps
        )
    }
}
