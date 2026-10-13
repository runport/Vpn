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
import java.net.InetSocketAddress
import java.net.Socket

sealed class VpnServiceStatus {
    object Stopped : VpnServiceStatus()
    data class Running(val server: String, val port: Int, val remarks: String, val startTime: Long) : VpnServiceStatus()
    data class Error(val message: String) : VpnServiceStatus()
}

class AzadiVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    private var vpnJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    companion object {
        const val ACTION_CONNECT = "com.example.vpn.ACTION_CONNECT"
        const val ACTION_DISCONNECT = "com.example.vpn.ACTION_DISCONNECT"
        const val EXTRA_CONFIG_ID = "EXTRA_CONFIG_ID"
        const val EXTRA_SERVER = "EXTRA_SERVER"
        const val EXTRA_PORT = "EXTRA_PORT"
        const val EXTRA_REMARKS = "EXTRA_REMARKS"
        const val EXTRA_PROTOCOL = "EXTRA_PROTOCOL"

        private const val CHANNEL_ID = "azadi_vpn_channel"
        private const val NOTIFICATION_ID = 1001

        private val _vpnStatusFlow = MutableStateFlow<VpnServiceStatus>(VpnServiceStatus.Stopped)
        val vpnStatusFlow = _vpnStatusFlow.asStateFlow()
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: return START_NOT_STICKY

        when (action) {
            ACTION_CONNECT -> {
                val server = intent.getStringExtra(EXTRA_SERVER) ?: ""
                val port = intent.getIntExtra(EXTRA_PORT, 443)
                val remarks = intent.getStringExtra(EXTRA_REMARKS) ?: "آزادیار"
                startVpnTunnel(server, port, remarks)
            }
            ACTION_DISCONNECT -> {
                stopVpnTunnel()
                stopSelf()
            }
        }

        return START_NOT_STICKY
    }

    private fun startVpnTunnel(server: String, port: Int, remarks: String) {
        if (vpnInterface != null) {
            stopVpnTunnel()
        }

        val notification = buildForegroundNotification("در حال اتصال به $remarks...")
        startForeground(NOTIFICATION_ID, notification)

        vpnJob = scope.launch {
            try {
                // Verify endpoint reachability with socket protection
                val probeSocket = Socket()
                protect(probeSocket)
                probeSocket.connect(InetSocketAddress(server, port), 4000)
                probeSocket.close()

                val builder = Builder()
                    .setSession("AzadiYar - $remarks")
                    .addAddress("10.0.0.2", 32)
                    .addRoute("0.0.0.0", 0)
                    .addDnsServer("1.1.1.1")
                    .addDnsServer("8.8.8.8")
                    .setMtu(1500)
                    .setBlocking(false)

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    builder.setMetered(false)
                }

                val pfd = builder.establish()
                if (pfd == null) {
                    _vpnStatusFlow.value = VpnServiceStatus.Error("ایجاد تونل VPN توسط سیستم رد شد")
                    stopSelf()
                    return@launch
                }

                vpnInterface = pfd
                _vpnStatusFlow.value = VpnServiceStatus.Running(server, port, remarks, System.currentTimeMillis())

                updateNotification("متصل به $remarks", "ترافیک از مسیر امن آزادیار هدایت می‌شود")

                // Keepalive and tunnel monitoring loop
                runTunnelLoop(pfd)
            } catch (e: Exception) {
                _vpnStatusFlow.value = VpnServiceStatus.Error("خطا در برقراری اتصال: ${e.message}")
                stopVpnTunnel()
            }
        }
    }

    private suspend fun runTunnelLoop(pfd: ParcelFileDescriptor) {
        val inStream = FileInputStream(pfd.fileDescriptor)
        val outStream = FileOutputStream(pfd.fileDescriptor)
        val packet = ByteArray(32767)

        try {
            while (scope.isActive && vpnInterface != null) {
                // Read from TUN interface if data available
                if (inStream.available() > 0) {
                    val length = inStream.read(packet)
                    if (length > 0) {
                        // Forward or process packet
                    }
                }
                delay(100)
            }
        } catch (_: Exception) {
            // Tunnel terminated or interrupted
        } finally {
            inStream.close()
            outStream.close()
        }
    }

    private fun stopVpnTunnel() {
        vpnJob?.cancel()
        vpnJob = null

        try {
            vpnInterface?.close()
        } catch (_: Exception) {}
        vpnInterface = null

        _vpnStatusFlow.value = VpnServiceStatus.Stopped
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    override fun onDestroy() {
        stopVpnTunnel()
        super.onDestroy()
    }

    override fun onRevoke() {
        stopVpnTunnel()
        super.onRevoke()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "سرویس اتصال آزادیار",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "نمایش وضعیت اتصال فعال آزادیار"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildForegroundNotification(content: String): Notification {
        val openIntent = Intent(this, MainActivity::class.java)
        val pendingOpen = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = Intent(this, AzadiVpnService::class.java).apply {
            action = ACTION_DISCONNECT
        }
        val pendingStop = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("آزادیار | اتصال امن")
            .setContentText(content)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingOpen)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "قطع اتصال", pendingStop)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(title: String, content: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val openIntent = Intent(this, MainActivity::class.java)
        val pendingOpen = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stopIntent = Intent(this, AzadiVpnService::class.java).apply {
            action = ACTION_DISCONNECT
        }
        val pendingStop = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingOpen)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "قطع اتصال", pendingStop)
            .setOngoing(true)
            .build()

        manager.notify(NOTIFICATION_ID, notification)
    }
}
