package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.VpnLock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.model.ConnectionState
import com.example.core.model.TestStatus
import com.example.ui.components.PingBadge
import com.example.ui.components.RtlProvider
import com.example.ui.components.ShareQrDialog
import com.example.ui.viewmodel.MainViewModel
import kotlinx.coroutines.delay
import java.util.Locale

@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    onNavigateToConfigs: () -> Unit,
    onNavigateToDiagnostics: () -> Unit,
    onNavigateToSources: () -> Unit,
    onNavigateToLogs: () -> Unit = {},
    onConnectRequest: (action: () -> Unit) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val connectionState by viewModel.connectionState.collectAsState()
    val configs by viewModel.filteredConfigs.collectAsState()
    val proxies by viewModel.proxies.collectAsState()
    val fetchProgress by viewModel.fetchProgress.collectAsState()
    val proxyFetchProgress by viewModel.proxyFetchProgress.collectAsState()
    val v2raySettings by viewModel.v2raySettings.collectAsState()
    val selectedConfig by viewModel.selectedConfig.collectAsState()
    val selectedProxy by viewModel.selectedProxy.collectAsState()
    val selectedTargetType by viewModel.selectedTargetType.collectAsState()

    var showShareQrDialog by remember { mutableStateOf(false) }

    val totalConfigs = configs.size
    val totalProxies = proxies.size
    val healthyConfigs = configs.count {
        it.testStatus == TestStatus.GLOBAL_ACCESS_CONFIRMED ||
                it.testStatus == TestStatus.TUNNEL_CONNECTED ||
                it.testStatus == TestStatus.REACHABLE_ENDPOINT
    }
    val bypassConfigs = configs.count { it.isBypassNationalNet }
    val halfPriceConfigs = configs.count { it.isHalfPriceDomestic }

    val isConnected = connectionState is ConnectionState.Connected
    val isConnecting = connectionState is ConnectionState.Connecting || connectionState is ConnectionState.Reconnecting

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isConnecting || isConnected) 1.08f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    // Live connection timer
    var durationSeconds by remember { mutableLongStateOf(0L) }
    LaunchedEffect(connectionState) {
        if (connectionState is ConnectionState.Connected) {
            val startTime = (connectionState as ConnectionState.Connected).connectedAt
            while (true) {
                durationSeconds = ((System.currentTimeMillis() - startTime) / 1000).coerceAtLeast(0)
                delay(1000)
            }
        } else {
            durationSeconds = 0L
        }
    }

    RtlProvider {
        Column(
            modifier = modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header with App Title & Top Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "آزادیار | AzadiYar",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "دسترسی آزاد، ایمن و خودکار به اینترنت بین‌المللی",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Logs Terminal Button
                    IconButton(
                        onClick = onNavigateToLogs,
                        modifier = Modifier.testTag("top_logs_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Terminal,
                            contentDescription = "لاگ‌های سیستم",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    // Security Badge
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = "امنیت",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "رمزنگاری تونل",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Selected Target Profile Card (Config or Proxy)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigateToConfigs() }
                    .testTag("selected_profile_card"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    if (selectedTargetType == "PROXY" && selectedProxy != null) {
                        val prx = selectedProxy!!
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Text(prx.countryFlag, fontSize = 22.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "پروکسی انتخابی: ${prx.type.displayName}",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "(${prx.countryName})",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    text = "${prx.host}:${prx.port}",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                        PingBadge(prx.pingMs)
                    } else if (selectedConfig != null) {
                        val cfg = selectedConfig!!
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Text(cfg.countryFlag, fontSize = 22.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = cfg.remarks,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = cfg.protocol.displayName,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = "${cfg.server}:${cfg.port} • ${cfg.countryName}",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        PingBadge(cfg.pingMs)
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.Speed, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text("هیچ کانفیگی انتخاب نشده", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text("برای انتخاب کانفیگ روی این کارت ضربه بزنید", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedButton(
                        onClick = onNavigateToConfigs,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text("تغییر سرور", fontSize = 10.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Big Circular Connection Button
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(200.dp)
                    .testTag("connect_button_container")
            ) {
                // Pulse Ring
                if (isConnected || isConnecting) {
                    Box(
                        modifier = Modifier
                            .size(200.dp)
                            .scale(pulseScale)
                            .border(
                                width = 3.dp,
                                color = if (isConnected) Color(0xFF10B981).copy(alpha = 0.45f) else Color(0xFF06B6D4).copy(alpha = 0.45f),
                                shape = CircleShape
                            )
                    )
                }

                val buttonBg = when {
                    isConnected -> Brush.verticalGradient(listOf(Color(0xFF00E676), Color(0xFF00897B)))
                    isConnecting -> Brush.verticalGradient(listOf(Color(0xFF00BCD4), Color(0xFF0097A7)))
                    else -> Brush.verticalGradient(listOf(Color(0xFF37474F), Color(0xFF212121)))
                }

                Surface(
                    shape = CircleShape,
                    shadowElevation = 10.dp,
                    modifier = Modifier
                        .size(150.dp)
                        .clip(CircleShape)
                        .clickable {
                            if (isConnected || isConnecting) {
                                viewModel.disconnect()
                            } else {
                                onConnectRequest {
                                    viewModel.connectSelectedTarget()
                                }
                            }
                        }
                        .testTag("main_connect_button")
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(buttonBg),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            if (isConnecting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(36.dp),
                                    color = Color.White,
                                    strokeWidth = 3.dp
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.PowerSettingsNew,
                                    contentDescription = "اتصال",
                                    tint = Color.White,
                                    modifier = Modifier.size(46.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = when (connectionState) {
                                    is ConnectionState.Connected -> "قطع اتصال"
                                    is ConnectionState.Connecting -> "در حال اتصال..."
                                    is ConnectionState.Reconnecting -> "بازیابی..."
                                    is ConnectionState.Error -> "تلاش مجدد"
                                    is ConnectionState.Disconnected -> "اتصال هوشمند"
                                },
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Status Headline
            val statusText = when (val state = connectionState) {
                is ConnectionState.Connected -> "متصل به: ${state.title}"
                is ConnectionState.Connecting -> state.step
                is ConnectionState.Reconnecting -> state.reason
                is ConnectionState.Error -> state.message
                is ConnectionState.Disconnected -> "آماده برقراری تونل امن اینترنت بین‌المللی"
            }

            Text(
                text = statusText,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = when (connectionState) {
                    is ConnectionState.Connected -> Color(0xFF00E676)
                    is ConnectionState.Connecting, is ConnectionState.Reconnecting -> Color(0xFF06B6D4)
                    is ConnectionState.Error -> Color(0xFFEF4444)
                    is ConnectionState.Disconnected -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Active v2rayNG Profile Chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = when (v2raySettings.routingMode) {
                            com.example.core.model.RoutingMode.BYPASS_IRAN_LAN -> "دور زدن ایران و LAN"
                            com.example.core.model.RoutingMode.GLOBAL -> "مسیریابی سراسری"
                            com.example.core.model.RoutingMode.PROXY_ONLY -> "فقط فیلتر شده"
                        },
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = if (v2raySettings.perAppProxyEnabled) "تفکیک: ${v2raySettings.selectedApps.size} برنامه" else "تمام برنامه‌ها",
                        fontSize = 10.sp,
                        color = if (v2raySettings.perAppProxyEnabled) Color(0xFF8B5CF6) else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "DNS: ${v2raySettings.remoteDns}",
                        fontSize = 10.sp,
                        color = Color(0xFF0288D1),
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // LIVE HIGH-TECH CONNECTION STATUS CARD (When Connected)
            if (connectionState is ConnectionState.Connected) {
                val state = connectionState as ConnectionState.Connected
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFF0F2417) // Sleek emerald dark tint
                    ),
                    border = CardDefaults.outlinedCardBorder().copy(
                        brush = Brush.horizontalGradient(listOf(Color(0xFF00E676), Color(0xFF00897B)))
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        // Row 1: Country Flag, Name, City & Target Badge
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(state.countryFlag, fontSize = 24.sp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = state.countryName + if (state.locationCity.isNotBlank()) " (${state.locationCity})" else "",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = Color.White
                                    )
                                    Text(
                                        text = "${state.host}:${state.port}",
                                        fontSize = 12.sp,
                                        color = Color(0xFF81C784),
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }

                            Surface(
                                color = Color(0xFF00E676).copy(alpha = 0.2f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = state.protocolOrType,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF00E676),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Row 2: Live Download & Upload Speed Gauges
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Download Speed Box
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFF1B3B25))
                                    .padding(10.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.ArrowDownward,
                                        contentDescription = null,
                                        tint = Color(0xFF00E676),
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Column {
                                        Text(
                                            text = formatSpeed(state.downloadSpeedBps),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            color = Color.White,
                                            fontFamily = FontFamily.Monospace
                                        )
                                        Text(
                                            text = "دریافت: ${formatBytes(state.bytesIn)}",
                                            fontSize = 10.sp,
                                            color = Color(0xFFA5D6A7)
                                        )
                                    }
                                }
                            }

                            // Upload Speed Box
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFF1B3B25))
                                    .padding(10.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.ArrowUpward,
                                        contentDescription = null,
                                        tint = Color(0xFF29B6F6),
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Column {
                                        Text(
                                            text = formatSpeed(state.uploadSpeedBps),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            color = Color.White,
                                            fontFamily = FontFamily.Monospace
                                        )
                                        Text(
                                            text = "ارسال: ${formatBytes(state.bytesOut)}",
                                            fontSize = 10.sp,
                                            color = Color(0xFF81D4FA)
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Row 3: Duration Timer & Ping Latency
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Timer, contentDescription = null, tint = Color(0xFFB0BEC5), modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "مدت: ${formatDuration(durationSeconds)}",
                                    fontSize = 11.sp,
                                    color = Color(0xFFCFD8DC),
                                    fontFamily = FontFamily.Monospace
                                )
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                PingBadge(state.pingMs)
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Row 4: Quick Actions on Active Connection
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { showShareQrDialog = true },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                            ) {
                                Icon(Icons.Default.QrCode2, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("کد QR و اشتراک", fontSize = 11.sp)
                            }

                            OutlinedButton(
                                onClick = onNavigateToLogs,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.Terminal, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("لاگ زنده", fontSize = 11.sp)
                            }
                        }
                    }
                }
            }

            // Fetch Progress Banner (Configs)
            AnimatedVisibility(visible = fetchProgress.isRunning) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = "در حال دریافت کانفیگ‌ها...", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text(text = "${fetchProgress.completedSources} از ${fetchProgress.totalSources} منبع", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = {
                                if (fetchProgress.totalSources > 0)
                                    fetchProgress.completedSources.toFloat() / fetchProgress.totalSources.toFloat()
                                else 0f
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "منبع فعلی: ${fetchProgress.currentSourceName} (${fetchProgress.newConfigsFound} کانفیگ یافت شد)",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Proxy Fetch Progress Banner
            AnimatedVisibility(visible = proxyFetchProgress.isRunning) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = "در حال جمع‌آوری پروکسی‌ها...", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text(text = "${proxyFetchProgress.totalFound} پروکسی", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "منبع: ${proxyFetchProgress.currentSource}",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Quick Actions: Fetch Configs, Fetch Proxies, Delete Failed Both
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { viewModel.fetchAllSources() },
                    enabled = !fetchProgress.isRunning,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("home_fetch_configs_button")
                ) {
                    Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("دریافت کانفیگ", fontSize = 11.sp)
                }

                Button(
                    onClick = { viewModel.fetchAllProxies() },
                    enabled = !proxyFetchProgress.isRunning,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("home_fetch_proxies_button")
                ) {
                    Icon(Icons.Default.VpnLock, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("جمع‌آوری پروکسی", fontSize = 11.sp)
                }

                OutlinedButton(
                    onClick = {
                        viewModel.deleteFailedConfigsAndProxies { cfgDeleted, prxDeleted ->
                            Toast.makeText(
                                context,
                                "$cfgDeleted کانفیگ و $prxDeleted پروکسی نامعتبر حذف شدند",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.testTag("home_clean_all_failed_button")
                ) {
                    Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color(0xFFEF4444))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("حذف نامعتبرها", fontSize = 10.sp, color = Color(0xFFEF4444))
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Stats Row: Total Configs, Healthy, Total Proxies
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                StatCard(
                    title = "کل کانفیگ‌ها",
                    value = "$totalConfigs",
                    icon = Icons.Default.Speed,
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onNavigateToConfigs() }
                )
                StatCard(
                    title = "سالم و تأییدشده",
                    value = "$healthyConfigs",
                    icon = Icons.Default.HealthAndSafety,
                    tint = Color(0xFF10B981),
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onNavigateToConfigs() }
                )
                StatCard(
                    title = "کل پروکسی‌ها",
                    value = "$totalProxies",
                    icon = Icons.Default.VpnLock,
                    tint = Color(0xFF00BCD4),
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onNavigateToConfigs() }
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // National Net Bypass & Half-Price Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF059669).copy(alpha = 0.12f),
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onNavigateToConfigs() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Security, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Column {
                            Text(text = "عبور از نت ملی", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF10B981))
                            Text(text = "$bypassConfigs کانفیگ فیلترگریز", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFD97706).copy(alpha = 0.12f),
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onNavigateToConfigs() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color(0xFFF59E0B), modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Column {
                            Text(text = "ترافیک نیم‌بها", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFF59E0B))
                            Text(text = "$halfPriceConfigs سرور داخلی", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Navigation Shortcuts Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onNavigateToConfigs,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("مدیریت کانفیگ‌ها", fontSize = 11.sp)
                }

                OutlinedButton(
                    onClick = onNavigateToSources,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("مدیریت منابع", fontSize = 11.sp)
                }

                OutlinedButton(
                    onClick = onNavigateToDiagnostics,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("عیب‌یابی شبکه", fontSize = 11.sp)
                }
            }
        }

        // Active Connection Share QR Modal
        if (showShareQrDialog && connectionState is ConnectionState.Connected) {
            val state = connectionState as ConnectionState.Connected
            ShareQrDialog(
                title = state.title,
                subtitle = "${state.protocolOrType} • ${state.host}:${state.port}",
                contentToShare = state.rawUriOrShareable,
                onDismiss = { showShareQrDialog = false }
            )
        }
    }
}

@Composable
private fun StatCard(
    title: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.primary
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = value, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text(text = title, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
    }
}

private fun formatBytes(bytes: Long): String {
    return when {
        bytes >= 1024 * 1024 * 1024 -> String.format(Locale.US, "%.2f GB", bytes.toDouble() / (1024 * 1024 * 1024))
        bytes >= 1024 * 1024 -> String.format(Locale.US, "%.1f MB", bytes.toDouble() / (1024 * 1024))
        bytes >= 1024 -> String.format(Locale.US, "%.0f KB", bytes.toDouble() / 1024)
        else -> "$bytes B"
    }
}

private fun formatSpeed(bps: Long): String {
    return when {
        bps >= 1024 * 1024 -> String.format(Locale.US, "%.1f MB/s", bps.toDouble() / (1024 * 1024))
        bps >= 1024 -> String.format(Locale.US, "%.0f KB/s", bps.toDouble() / 1024)
        else -> "$bps B/s"
    }
}

private fun formatDuration(seconds: Long): String {
    val hrs = seconds / 3600
    val mins = (seconds % 3600) / 60
    val secs = seconds % 60
    return if (hrs > 0) {
        String.format(Locale.US, "%02d:%02d:%02d", hrs, mins, secs)
    } else {
        String.format(Locale.US, "%02d:%02d", mins, secs)
    }
}
