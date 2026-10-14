package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.model.ConnectionState
import com.example.core.model.ProxyItem
import com.example.core.model.ProxyType
import com.example.ui.components.PingBadge
import com.example.ui.components.RtlProvider
import com.example.ui.components.ShareQrDialog
import com.example.ui.viewmodel.MainViewModel

@Composable
fun ProxiesScreen(
    viewModel: MainViewModel,
    onRequestConnect: (action: () -> Unit) -> Unit = { it() },
    modifier: Modifier = Modifier
) {
    val proxies by viewModel.filteredProxies.collectAsState()
    val rawProxies by viewModel.proxies.collectAsState()
    val connectionState by viewModel.connectionState.collectAsState()
    val fetchProgress by viewModel.proxyFetchProgress.collectAsState()
    val isTesting by viewModel.isTestingProxies.collectAsState()
    val testProgress by viewModel.proxyTestProgress.collectAsState()
    val proxyCountryDistribution by viewModel.proxyCountryDistribution.collectAsState()

    val searchQuery by viewModel.proxySearchQuery.collectAsState()
    val selectedType by viewModel.selectedProxyType.collectAsState()
    val selectedCountry by viewModel.selectedProxyCountry.collectAsState()
    val filterDomesticOnly by viewModel.filterDomesticProxiesOnly.collectAsState()
    val showMapView by viewModel.showProxyMapView.collectAsState()

    var shareQrProxy by remember { mutableStateOf<ProxyItem?>(null) }

    val context = LocalContext.current

    val connectedProxyId = (connectionState as? ConnectionState.Connected)?.proxy?.id

    RtlProvider {
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            // Search Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { viewModel.proxySearchQuery.value = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("proxy_search_input"),
                    placeholder = { Text("جستجو در آی‌پی، پورت، کشور یا منبع...", fontSize = 12.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.proxySearchQuery.value = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "پاک کردن", modifier = Modifier.size(16.dp))
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Action Buttons: Collect, Test All, Map, Delete Failed
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = { viewModel.fetchAllProxies() },
                    enabled = !fetchProgress.isRunning,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.testTag("fetch_proxies_button")
                ) {
                    if (fetchProgress.isRunning) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), color = Color.White, strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        if (fetchProgress.isRunning) "در حال دریافت..." else "جمع‌آوری پروکسی",
                        fontSize = 11.sp
                    )
                }

                Button(
                    onClick = { viewModel.testAllProxies() },
                    enabled = !isTesting,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0288D1)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.testTag("test_all_proxies_button")
                ) {
                    if (isTesting) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), color = Color.White, strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.Speed, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        if (isTesting) "در حال تست..." else "تست همه",
                        fontSize = 11.sp
                    )
                }

                OutlinedButton(
                    onClick = { viewModel.showProxyMapView.value = !showMapView },
                    shape = RoundedCornerShape(8.dp),
                    colors = if (showMapView) ButtonDefaults.outlinedButtonColors(containerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f)) else ButtonDefaults.outlinedButtonColors()
                ) {
                    Icon(Icons.Default.Map, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (showMapView) "پنهان‌سازی نقشه" else "نمایش روی نقشه", fontSize = 11.sp)
                }

                // Delete Failed Proxies
                OutlinedButton(
                    onClick = {
                        viewModel.deleteFailedProxies { count ->
                            Toast.makeText(context, "$count پروکسی نامعتبر حذف شد", Toast.LENGTH_SHORT).show()
                        }
                    },
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.testTag("delete_failed_proxies_button")
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color(0xFFEF4444))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("حذف نامعتبرها", fontSize = 11.sp, color = Color(0xFFEF4444))
                }

                // Delete Both Failed
                OutlinedButton(
                    onClick = {
                        viewModel.deleteFailedConfigsAndProxies { cfgCount, prxCount ->
                            Toast.makeText(
                                context,
                                "$cfgCount کانفیگ و $prxCount پروکسی نامعتبر حذف شدند",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    },
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.testTag("delete_both_failed_button")
                ) {
                    Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color(0xFFC62828))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("حذف تمام نامعتبرها", fontSize = 10.sp, color = Color(0xFFC62828))
                }
            }

            // Testing Progress Bar
            AnimatedVisibility(visible = isTesting) {
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    val current = testProgress.first
                    val total = testProgress.second
                    val fraction = if (total > 0) current.toFloat() / total.toFloat() else 0f
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("پیشرفت تست پروکسی‌ها:", fontSize = 11.sp, color = MaterialTheme.colorScheme.secondary)
                        Text("$current از $total", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
                }
            }

            // Interactive Map View
            AnimatedVisibility(visible = showMapView) {
                GlobalMapView(
                    countries = proxyCountryDistribution,
                    selectedCountry = selectedCountry,
                    onCountrySelected = { code ->
                        viewModel.selectedProxyCountry.value = if (selectedCountry == code) null else code
                    },
                    onCloseMap = { viewModel.showProxyMapView.value = false },
                    itemTypeLabel = "پروکسی",
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Protocol / Type Filter Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilterChip(
                    selected = selectedType == null,
                    onClick = { viewModel.selectedProxyType.value = null },
                    label = { Text("همه نوع‌ها (${rawProxies.size})", fontSize = 11.sp) }
                )
                ProxyType.values().forEach { type ->
                    val count = rawProxies.count { it.type == type }
                    FilterChip(
                        selected = selectedType == type,
                        onClick = {
                            viewModel.selectedProxyType.value = if (selectedType == type) null else type
                        },
                        label = { Text("${type.displayName} ($count)", fontSize = 11.sp) }
                    )
                }
            }

            // Country Filter Tag (if active) & Domestic Filter
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(
                    selected = filterDomesticOnly,
                    onClick = { viewModel.filterDomesticProxiesOnly.value = !filterDomesticOnly },
                    label = { Text("⚡ سرورهای داخل ایران", fontSize = 11.sp) }
                )

                if (selectedCountry != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.clickable { viewModel.selectedProxyCountry.value = null }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text("کشور: $selectedCountry ✕", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSecondaryContainer)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Proxies List
            if (proxies.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "پروکسی‌ای با این فیلترها یافت نشد",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "دکمه «جمع‌آوری پروکسی» را لمس کنید تا از منابع معتبر داخلی و خارجی دریافت شود.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(proxies, key = { it.id }) { proxy ->
                        val isProxyConnected = connectedProxyId == proxy.id
                        ProxyItemCard(
                            proxy = proxy,
                            isConnected = isProxyConnected,
                            onConnect = {
                                if (isProxyConnected) {
                                    viewModel.disconnect()
                                } else {
                                    onRequestConnect {
                                        viewModel.connectProxy(proxy)
                                    }
                                }
                            },
                            onTest = { viewModel.testSingleProxy(proxy) },
                            onShareQr = { shareQrProxy = proxy },
                            onCopy = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val text = if (proxy.type == ProxyType.MTPROTO) {
                                    "tg://proxy?server=${proxy.host}&port=${proxy.port}&secret=${proxy.secretOrAuth ?: ""}"
                                } else {
                                    "${proxy.type.name.lowercase()}://${proxy.host}:${proxy.port}"
                                }
                                val clip = ClipData.newPlainText("Proxy", text)
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, "مشخصات پروکسی کپی شد", Toast.LENGTH_SHORT).show()
                            },
                            onLaunchTelegram = {
                                if (proxy.type == ProxyType.MTPROTO) {
                                    val secret = proxy.secretOrAuth ?: ""
                                    val uri = Uri.parse("tg://proxy?server=${proxy.host}&port=${proxy.port}&secret=$secret")
                                    val intent = Intent(Intent.ACTION_VIEW, uri)
                                    try {
                                        context.startActivity(intent)
                                    } catch (_: Exception) {
                                        Toast.makeText(context, "تلگرام روی دستگاه نصب نیست", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }

        // Share QR Dialog
        if (shareQrProxy != null) {
            val item = shareQrProxy!!
            val shareUri = if (item.type == ProxyType.MTPROTO) {
                "tg://proxy?server=${item.host}&port=${item.port}&secret=${item.secretOrAuth ?: ""}"
            } else {
                "${item.type.name.lowercase()}://${item.host}:${item.port}"
            }
            ShareQrDialog(
                title = "${item.type.displayName} | ${item.countryFlag} ${item.countryName}",
                subtitle = "${item.host}:${item.port} • منبع: ${item.sourceName}",
                contentToShare = shareUri,
                onDismiss = { shareQrProxy = null }
            )
        }
    }
}

@Composable
fun ProxyItemCard(
    proxy: ProxyItem,
    isConnected: Boolean,
    onConnect: () -> Unit,
    onTest: () -> Unit,
    onShareQr: () -> Unit,
    onCopy: () -> Unit,
    onLaunchTelegram: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulseProxy")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onConnect() }
            .testTag("proxy_item_${proxy.id}"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isConnected) Color(0xFF0D2E1A) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        border = if (isConnected) BorderStroke(2.dp, Color(0xFF00E676)) else null
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Connected Active Banner
            if (isConnected) {
                Surface(
                    color = Color(0xFF00E676).copy(alpha = 0.25f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .scale(pulseAlpha)
                                    .background(Color(0xFF00E676), CircleShape)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "پروکسی متصل و فعال تونل",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF00E676)
                            )
                        }

                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFF00E676),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            // Row 1: Protocol badge, Country flag & name, Domestic tag, and Ping
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = when (proxy.type) {
                            ProxyType.MTPROTO -> Color(0xFF0088CC).copy(alpha = 0.15f)
                            ProxyType.SOCKS5 -> Color(0xFF10B981).copy(alpha = 0.15f)
                            ProxyType.HTTP, ProxyType.HTTPS -> Color(0xFF06B6D4).copy(alpha = 0.15f)
                            ProxyType.SOCKS4 -> Color(0xFF8B5CF6).copy(alpha = 0.15f)
                        },
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = proxy.type.displayName,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = when (proxy.type) {
                                ProxyType.MTPROTO -> Color(0xFF0088CC)
                                ProxyType.SOCKS5 -> Color(0xFF10B981)
                                ProxyType.HTTP, ProxyType.HTTPS -> Color(0xFF06B6D4)
                                ProxyType.SOCKS4 -> Color(0xFF8B5CF6)
                            },
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    Text(proxy.countryFlag, fontSize = 14.sp)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = proxy.countryName,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (isConnected) Color.White else MaterialTheme.colorScheme.onSurface
                    )

                    if (proxy.isDomestic) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            color = Color(0xFF10B981).copy(alpha = 0.15f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = "⚡ سرور ایران",
                                fontSize = 10.sp,
                                color = Color(0xFF10B981),
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (proxy.pingMs > 0) {
                        PingBadge(proxy.pingMs)
                    } else if (proxy.isHealthy) {
                        Surface(
                            color = Color(0xFF10B981).copy(alpha = 0.2f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "سالم",
                                fontSize = 10.sp,
                                color = Color(0xFF10B981),
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    } else {
                        Text(
                            text = "تست‌نشده",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Row 2: Host:Port and Source
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "${proxy.host}:${proxy.port}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = if (isConnected) Color(0xFFA5D6A7) else MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "منبع: ${proxy.sourceName}",
                        fontSize = 10.sp,
                        color = if (isConnected) Color(0xFFA5D6A7) else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    // Connect / Disconnect button
                    Button(
                        onClick = onConnect,
                        colors = if (isConnected) {
                            ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828))
                        } else {
                            ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Icon(
                            imageVector = if (isConnected) Icons.Default.Stop else Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(if (isConnected) "قطع" else "اتصال", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    // Telegram Launch if MTProto
                    if (proxy.type == ProxyType.MTPROTO) {
                        OutlinedButton(
                            onClick = onLaunchTelegram,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Text("تلگرام", fontSize = 10.sp, color = Color(0xFF0088CC))
                        }
                    }

                    // Test button
                    OutlinedButton(
                        onClick = onTest,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Icon(Icons.Default.Speed, contentDescription = null, modifier = Modifier.size(14.dp))
                    }

                    // QR / Share button
                    IconButton(onClick = onShareQr, modifier = Modifier.size(34.dp)) {
                        Icon(Icons.Default.QrCode2, contentDescription = "کد QR", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                    }

                    // Copy button
                    IconButton(onClick = onCopy, modifier = Modifier.size(34.dp)) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "کپی", modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}
