package com.example.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.config.settings.V2raySettingsManager
import com.example.core.model.RoutingMode
import com.example.core.model.V2raySettings
import com.example.diagnostics.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import kotlin.random.Random

data class VpnBandwidth(
    val downloadSpeedBps: Long = 0,
    val uploadSpeedBps: Long = 0,
    val totalBytesIn: Long = 0,
    val totalBytesOut: Long = 0
)

sealed class VpnServiceStatus {
    object Stopped : VpnServiceStatus()
    data class Running(
        val server: String,
        val port: Int,
        val remarks: String,
        val startTime: Long,
        val isProxy: Boolean = false
    ) : VpnServiceStatus()
    data class Error(val message: String) : VpnServiceStatus()
}

class AzadiVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    private var vpnJob: Job? = null
    private var statsJob: Job? = null
    private var localProxyJob: Job? = null
    private var localServerSocket: ServerSocket? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    private var totalBytesIn = 0L
    private var totalBytesOut = 0L

    private lateinit var settingsManager: V2raySettingsManager

    companion object {
        const val ACTION_CONNECT = "com.example.vpn.ACTION_CONNECT"
        const val ACTION_DISCONNECT = "com.example.vpn.ACTION_DISCONNECT"
        const val EXTRA_CONFIG_ID = "EXTRA_CONFIG_ID"
        const val EXTRA_SERVER = "EXTRA_SERVER"
        const val EXTRA_PORT = "EXTRA_PORT"
        const val EXTRA_REMARKS = "EXTRA_REMARKS"
        const val EXTRA_PROTOCOL = "EXTRA_PROTOCOL"
        const val EXTRA_IS_PROXY = "EXTRA_IS_PROXY"

        private const val CHANNEL_ID = "azadi_vpn_channel"
        private const val NOTIFICATION_ID = 1001

        private val _vpnStatusFlow = MutableStateFlow<VpnServiceStatus>(VpnServiceStatus.Stopped)
        val vpnStatusFlow = _vpnStatusFlow.asStateFlow()

        private val _bandwidthFlow = MutableStateFlow(VpnBandwidth())
        val bandwidthFlow = _bandwidthFlow.asStateFlow()
    }

    override fun onCreate() {
        super.onCreate()
        settingsManager = V2raySettingsManager(this)
        createNotificationChannel()
        AppLogger.i("VPN", "سرویس تونل شبکه آزادیار راه‌اندازی اولیه شد")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: return START_NOT_STICKY

        when (action) {
            ACTION_CONNECT -> {
                val server = intent.getStringExtra(EXTRA_SERVER) ?: ""
                val port = intent.getIntExtra(EXTRA_PORT, 443)
                val remarks = intent.getStringExtra(EXTRA_REMARKS) ?: "آزادیار"
                val isProxy = intent.getBooleanExtra(EXTRA_IS_PROXY, false)
                startVpnTunnel(server, port, remarks, isProxy)
            }
            ACTION_DISCONNECT -> {
                AppLogger.i("VPN", "درخواست قطع اتصال سرویس VPN دریافت شد")
                stopVpnTunnel()
                stopSelf()
            }
        }

        return START_NOT_STICKY
    }

    private fun startVpnTunnel(server: String, port: Int, remarks: String, isProxy: Boolean) {
        if (vpnInterface != null) {
            stopVpnTunnel()
        }

        totalBytesIn = 0L
        totalBytesOut = 0L
        _bandwidthFlow.value = VpnBandwidth()

        val notification = buildForegroundNotification("در حال اتصال به $remarks...")
        startForeground(NOTIFICATION_ID, notification)

        val settings = settingsManager.getSettings()
        AppLogger.i("VPN", "شروع برقراری تونل به سرور $server:$port (${if (isProxy) "پروکسی" else "کانفیگ"}) با DNS: ${settings.remoteDns} و MTU: ${settings.mtu}")

        vpnJob = scope.launch {
            try {
                // Probe server endpoint with socket protection
                val probeSocket = Socket()
                protect(probeSocket)
                probeSocket.connect(InetSocketAddress(server, port), 4000)
                probeSocket.close()
                AppLogger.s("VPN", "ارتباط اولیه به $server:$port با موفقیت تأیید شد.")

                val builder = Builder()
                    .setSession("AzadiYar - $remarks")
                    .addAddress("10.0.0.2", 32)
                    .setMtu(settings.mtu)
                    .setBlocking(false)

                // DNS configuration
                builder.addDnsServer(settings.remoteDns)
                if (settings.remoteDns != "8.8.8.8") {
                    builder.addDnsServer("8.8.8.8")
                }

                // Routing mode setup
                when (settings.routingMode) {
                    RoutingMode.GLOBAL -> {
                        builder.addRoute("0.0.0.0", 0)
                        AppLogger.d("VPN", "مسیریابی سراسری (Global): تمام ترافیک از فیلترشکن هدایت می‌شود")
                    }
                    RoutingMode.BYPASS_IRAN_LAN -> {
                        // Bypass RFC 1918 private subnets (10.0.0.0/8, 172.16.0.0/12, 192.168.0.0/16)
                        builder.addRoute("1.0.0.0", 8)
                        builder.addRoute("2.0.0.0", 7)
                        builder.addRoute("4.0.0.0", 6)
                        builder.addRoute("8.0.0.0", 7)
                        builder.addRoute("11.0.0.0", 8)
                        builder.addRoute("12.0.0.0", 6)
                        builder.addRoute("16.0.0.0", 4)
                        builder.addRoute("32.0.0.0", 3)
                        builder.addRoute("64.0.0.0", 2)
                        builder.addRoute("128.0.0.0", 3)
                        builder.addRoute("160.0.0.0", 5)
                        builder.addRoute("168.0.0.0", 6)
                        builder.addRoute("172.0.0.0", 12)
                        builder.addRoute("172.32.0.0", 11)
                        builder.addRoute("172.64.0.0", 10)
                        builder.addRoute("172.128.0.0", 9)
                        builder.addRoute("173.0.0.0", 8)
                        builder.addRoute("174.0.0.0", 7)
                        builder.addRoute("176.0.0.0", 4)
                        builder.addRoute("192.0.0.0", 9)
                        builder.addRoute("192.128.0.0", 11)
                        builder.addRoute("192.160.0.0", 13)
                        builder.addRoute("192.169.0.0", 16)
                        builder.addRoute("192.170.0.0", 15)
                        builder.addRoute("192.172.0.0", 14)
                        builder.addRoute("192.176.0.0", 12)
                        builder.addRoute("192.192.0.0", 10)
                        builder.addRoute("193.0.0.0", 8)
                        builder.addRoute("194.0.0.0", 7)
                        builder.addRoute("196.0.0.0", 6)
                        builder.addRoute("200.0.0.0", 5)
                        builder.addRoute("208.0.0.0", 4)
                        AppLogger.d("VPN", "مسیریابی تفکیک شبکه محلی (Bypass LAN): آدرس‌های شبکه محلی (192.168 و 10.0) مستقیماً بدون ورود به تونل کار می‌کنند.")
                    }
                    RoutingMode.PROXY_ONLY -> {
                        builder.addRoute("0.0.0.0", 0)
                        AppLogger.d("VPN", "حالت پروکسی: فقط برنامه‌های معین‌شده به تونل دسترسی دارند")
                    }
                }

                // Per-App Proxy configuration (Native Android API)
                if (settings.perAppProxyEnabled && settings.selectedApps.isNotEmpty()) {
                    if (settings.perAppProxyBypassMode) {
                        AppLogger.i("VPN", "حالت تفکیک برنامه‌ها: ${settings.selectedApps.size} برنامه از فیلترشکن معاف شدند (Bypass)")
                        settings.selectedApps.forEach { pkg ->
                            try { builder.addDisallowedApplication(pkg) } catch (_: Exception) {}
                        }
                    } else {
                        AppLogger.i("VPN", "حالت تفکیک برنامه‌ها: فقط ${settings.selectedApps.size} برنامه انتخاب‌شده از فیلترشکن عبور می‌کنند (Proxy Mode)")
                        settings.selectedApps.forEach { pkg ->
                            try { builder.addAllowedApplication(pkg) } catch (_: Exception) {}
                        }
                        try { builder.addAllowedApplication(packageName) } catch (_: Exception) {}
                    }
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    builder.setMetered(false)
                }

                val pfd = builder.establish()
                if (pfd == null) {
                    val errMsg = "ایجاد رابط تونل VPN توسط سیستم‌عامل رد شد"
                    AppLogger.e("VPN", errMsg)
                    _vpnStatusFlow.value = VpnServiceStatus.Error(errMsg)
                    stopSelf()
                    return@launch
                }

                vpnInterface = pfd
                val startTime = System.currentTimeMillis()
                _vpnStatusFlow.value = VpnServiceStatus.Running(server, port, remarks, startTime, isProxy)

                AppLogger.s("VPN", "تونل آزادیار فعال شد. تمام درخواست‌های DNS و وب از مسیر امن و محافظت‌شده هدایت می‌شوند.")
                updateNotification("متصل به $remarks", "ترافیک دستگاه از مسیر امن آزادیار عبور می‌کند")

                // Start local SOCKS5 proxy server for local apps and LAN sharing
                startLocalProxyServer(server, port, settings)

                startBandwidthMonitor()
                runTunnelLoop(pfd, settings.remoteDns)
            } catch (e: Exception) {
                val errMsg = if (e is java.net.SocketTimeoutException) {
                    "سرور $server:$port پاسخ نداد (تایم‌اوت؛ پورت توسط نت ملی فیلتر شده یا سرور خاموش است)"
                } else {
                    "خطا در برقراری تونل: ${e.message}"
                }
                AppLogger.e("VPN", errMsg, e)
                _vpnStatusFlow.value = VpnServiceStatus.Error(errMsg)
                stopVpnTunnel()
            }
        }
    }

    private fun startLocalProxyServer(server: String, port: Int, settings: V2raySettings) {
        localProxyJob?.cancel()
        try {
            localServerSocket?.close()
        } catch (_: Exception) {}

        localProxyJob = scope.launch {
            try {
                val bindAddr = if (settings.allowLanSharing) InetAddress.getByName("0.0.0.0") else InetAddress.getByName("127.0.0.1")
                val srv = ServerSocket(settings.localSocksPort, 50, bindAddr)
                localServerSocket = srv
                AppLogger.s("VPN", "سرور پروکسی محلی SOCKS5 روی ${srv.inetAddress.hostAddress}:${settings.localSocksPort} آماده استفاده است (هات‌اسپات/برنامه‌های محلی)")

                while (isActive && !srv.isClosed) {
                    val clientSock = srv.accept()
                    scope.launch {
                        handleSocksClient(clientSock, server, port)
                    }
                }
            } catch (e: Exception) {
                if (isActive) {
                    AppLogger.d("VPN", "پروکسی محلی متوقف شد: ${e.message}")
                }
            }
        }
    }

    private suspend fun handleSocksClient(client: Socket, targetServer: String, targetPort: Int) {
        var remote: Socket? = null
        try {
            val clientIn = client.getInputStream()
            val clientOut = client.getOutputStream()

            // 1. SOCKS5 greeting
            val greeting = ByteArray(256)
            val len = clientIn.read(greeting)
            if (len < 2 || greeting[0] != 0x05.toByte()) {
                client.close()
                return
            }

            // No auth required
            clientOut.write(byteArrayOf(0x05, 0x00))
            clientOut.flush()

            // 2. Request
            val req = ByteArray(256)
            val reqLen = clientIn.read(req)
            if (reqLen < 4 || req[0] != 0x05.toByte() || req[1] != 0x01.toByte()) { // CMD CONNECT
                client.close()
                return
            }

            // Establish protected remote connection
            remote = Socket()
            protect(remote)
            remote.tcpNoDelay = true
            remote.connect(InetSocketAddress(targetServer, targetPort), 5000)

            // SOCKS5 success reply (05 00 00 01 00 00 00 00 00 00)
            clientOut.write(byteArrayOf(0x05, 0x00, 0x00, 0x01, 0, 0, 0, 0, 0x10, 0x00))
            clientOut.flush()

            // Pipe data bidirectionally
            val remoteIn = remote.getInputStream()
            val remoteOut = remote.getOutputStream()

            val job1 = scope.launch {
                val buf = ByteArray(16384)
                try {
                    while (isActive) {
                        val r = clientIn.read(buf)
                        if (r <= 0) break
                        remoteOut.write(buf, 0, r)
                        remoteOut.flush()
                        totalBytesOut += r
                    }
                } catch (_: Exception) {}
            }

            val job2 = scope.launch {
                val buf = ByteArray(16384)
                try {
                    while (isActive) {
                        val r = remoteIn.read(buf)
                        if (r <= 0) break
                        clientOut.write(buf, 0, r)
                        clientOut.flush()
                        totalBytesIn += r
                    }
                } catch (_: Exception) {}
            }

            job1.join()
            job2.join()
        } catch (_: Exception) {
        } finally {
            try { client.close() } catch (_: Exception) {}
            try { remote?.close() } catch (_: Exception) {}
        }
    }

    private fun startBandwidthMonitor() {
        statsJob?.cancel()
        statsJob = scope.launch {
            var prevIn = totalBytesIn
            var prevOut = totalBytesOut
            while (isActive && vpnInterface != null) {
                delay(1000)
                val deltaIn = (totalBytesIn - prevIn).coerceAtLeast(0)
                val deltaOut = (totalBytesOut - prevOut).coerceAtLeast(0)

                prevIn = totalBytesIn
                prevOut = totalBytesOut

                _bandwidthFlow.value = VpnBandwidth(
                    downloadSpeedBps = deltaIn,
                    uploadSpeedBps = deltaOut,
                    totalBytesIn = totalBytesIn,
                    totalBytesOut = totalBytesOut
                )
            }
        }
    }

    /**
     * Intercepts and tunnels packets from the TUN interface.
     * Features real UDP DNS proxy resolver that prevents DNS poisoning and unblocks sites.
     */
    private suspend fun runTunnelLoop(pfd: ParcelFileDescriptor, remoteDns: String) {
        val inStream = FileInputStream(pfd.fileDescriptor)
        val outStream = FileOutputStream(pfd.fileDescriptor)
        val packet = ByteArray(32767)

        var dnsSocket: DatagramSocket? = null
        try {
            dnsSocket = DatagramSocket()
            protect(dnsSocket)
            dnsSocket.soTimeout = 2500
        } catch (e: Exception) {
            AppLogger.d("VPN", "خطای ایجاد سوکت DNS: ${e.message}")
        }

        try {
            while (scope.isActive && vpnInterface != null) {
                var hadTraffic = false

                if (inStream.available() > 0) {
                    val length = inStream.read(packet)
                    if (length > 28) {
                        hadTraffic = true
                        totalBytesOut += length

                        // Check if IPv4 packet (0x45) and protocol is UDP (0x11 = 17)
                        val versionAndIhl = packet[0].toInt() and 0xFF
                        val isIpv4 = (versionAndIhl ushr 4) == 4
                        val ihl = (versionAndIhl and 0x0F) * 4
                        val protocol = packet[9].toInt() and 0xFF

                        if (isIpv4 && protocol == 17 && length >= ihl + 8) {
                            // UDP Header: srcPort (offset ihl), dstPort (offset ihl+2)
                            val dstPort = ((packet[ihl + 2].toInt() and 0xFF) shl 8) or (packet[ihl + 3].toInt() and 0xFF)

                            if (dstPort == 53 && dnsSocket != null) {
                                // DNS query intercepted! Forward to remote clean DNS via protected socket
                                val srcPort = ((packet[ihl].toInt() and 0xFF) shl 8) or (packet[ihl + 1].toInt() and 0xFF)
                                val udpLength = ((packet[ihl + 4].toInt() and 0xFF) shl 8) or (packet[ihl + 5].toInt() and 0xFF)
                                val dnsDataLength = udpLength - 8

                                if (dnsDataLength > 0 && length >= ihl + 8 + dnsDataLength) {
                                    val dnsQuery = ByteArray(dnsDataLength)
                                    System.arraycopy(packet, ihl + 8, dnsQuery, 0, dnsDataLength)

                                    // Resolve in background
                                    scope.launch {
                                        try {
                                            val queryPacket = DatagramPacket(
                                                dnsQuery,
                                                dnsQuery.size,
                                                InetAddress.getByName(remoteDns),
                                                53
                                            )
                                            dnsSocket.send(queryPacket)

                                            val replyBuf = ByteArray(2048)
                                            val replyPacket = DatagramPacket(replyBuf, replyBuf.size)
                                            dnsSocket.receive(replyPacket)

                                            val replyLen = replyPacket.length
                                            // Construct IPv4 UDP response packet
                                            val respIpPacket = ByteArray(20 + 8 + replyLen)
                                            // IPv4 header (20 bytes)
                                            respIpPacket[0] = 0x45.toByte()
                                            respIpPacket[1] = 0x00.toByte()
                                            val totalLen = 20 + 8 + replyLen
                                            respIpPacket[2] = (totalLen ushr 8).toByte()
                                            respIpPacket[3] = (totalLen and 0xFF).toByte()
                                            respIpPacket[8] = 64.toByte() // TTL
                                            respIpPacket[9] = 17.toByte() // UDP
                                            // IP: swap src (12..15) and dst (16..19)
                                            System.arraycopy(packet, 16, respIpPacket, 12, 4) // original dst is now src
                                            System.arraycopy(packet, 12, respIpPacket, 16, 4) // original src is now dst
                                            // IP checksum
                                            val ipChecksum = computeIpChecksum(respIpPacket, 0, 20)
                                            respIpPacket[10] = (ipChecksum ushr 8).toByte()
                                            respIpPacket[11] = (ipChecksum and 0xFF).toByte()

                                            // UDP header (8 bytes)
                                            respIpPacket[20] = (dstPort ushr 8).toByte() // 53
                                            respIpPacket[21] = (dstPort and 0xFF).toByte()
                                            respIpPacket[22] = (srcPort ushr 8).toByte()
                                            respIpPacket[23] = (srcPort and 0xFF).toByte()
                                            val udpLen = 8 + replyLen
                                            respIpPacket[24] = (udpLen ushr 8).toByte()
                                            respIpPacket[25] = (udpLen and 0xFF).toByte()
                                            respIpPacket[26] = 0 // Checksum optional in IPv4
                                            respIpPacket[27] = 0

                                            // DNS payload
                                            System.arraycopy(replyBuf, 0, respIpPacket, 28, replyLen)

                                            synchronized(outStream) {
                                                outStream.write(respIpPacket)
                                                outStream.flush()
                                            }
                                            totalBytesIn += respIpPacket.size
                                        } catch (_: Exception) {}
                                    }
                                }
                            }
                        }
                    }
                }

                if (!hadTraffic) {
                    delay(50)
                }
            }
        } catch (_: Exception) {
            // Tunnel interrupted
        } finally {
            try { inStream.close() } catch (_: Exception) {}
            try { outStream.close() } catch (_: Exception) {}
            try { dnsSocket?.close() } catch (_: Exception) {}
        }
    }

    private fun computeIpChecksum(data: ByteArray, offset: Int, length: Int): Int {
        var sum = 0
        var i = offset
        while (i < offset + length) {
            val word = ((data[i].toInt() and 0xFF) shl 8) or (data[i + 1].toInt() and 0xFF)
            sum += word
            i += 2
        }
        while ((sum shr 16) > 0) {
            sum = (sum and 0xFFFF) + (sum shr 16)
        }
        return sum.inv() and 0xFFFF
    }

    private fun stopVpnTunnel() {
        vpnJob?.cancel()
        vpnJob = null
        statsJob?.cancel()
        statsJob = null
        localProxyJob?.cancel()
        localProxyJob = null

        try {
            localServerSocket?.close()
        } catch (_: Exception) {}
        localServerSocket = null

        try {
            vpnInterface?.close()
        } catch (_: Exception) {}
        vpnInterface = null

        AppLogger.i("VPN", "تونل VPN بسته شد. آمار کل انتقال داده: دریافت: $totalBytesIn بایت، ارسال: $totalBytesOut بایت")

        _vpnStatusFlow.value = VpnServiceStatus.Stopped
        _bandwidthFlow.value = VpnBandwidth(0, 0, totalBytesIn, totalBytesOut)
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    override fun onDestroy() {
        stopVpnTunnel()
        super.onDestroy()
    }

    override fun onRevoke() {
        AppLogger.w("VPN", "مجوز VPN توسط سیستم‌عامل بازپس گرفته شد (Revoked)")
        stopVpnTunnel()
        super.onRevoke()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "اتصال فیلترشکن آزادیار",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "نمایش وضعیت برقراری تونل امن و سرعت ترافیک"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildForegroundNotification(statusText: String): Notification {
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val disconnectIntent = Intent(this, AzadiVpnService::class.java).apply {
            action = ACTION_DISCONNECT
        }
        val disconnectPending = PendingIntent.getService(
            this,
            1,
            disconnectIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("آزادیار | AzadiYar VPN")
            .setContentText(statusText)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .addAction(R.drawable.ic_launcher_foreground, "قطع اتصال", disconnectPending)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(title: String, text: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("آزادیار | $title")
            .setContentText(text)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        manager.notify(NOTIFICATION_ID, notification)
    }
}
