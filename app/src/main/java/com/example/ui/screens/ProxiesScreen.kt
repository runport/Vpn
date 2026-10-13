package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Speed
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.model.ProxyItem
import com.example.core.model.ProxyType
import com.example.ui.components.PingBadge
import com.example.ui.components.RtlProvider
import com.example.ui.viewmodel.MainViewModel

@Composable
fun ProxiesScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val proxies by viewModel.filteredProxies.collectAsState()
    val rawProxies by viewModel.proxies.collectAsState()
    val fetchProgress by viewModel.proxyFetchProgress.collectAsState()
    val isTesting by viewModel.isTestingProxies.collectAsState()
    val testProgress by viewModel.proxyTestProgress.collectAsState()
    val proxyCountryDistribution by viewModel.proxyCountryDistribution.collectAsState()

    val searchQuery by viewModel.proxySearchQuery.collectAsState()
    val selectedType by viewModel.selectedProxyType.collectAsState()
    val selectedCountry by viewModel.selectedProxyCountry.collectAsState()
    val filterDomesticOnly by viewModel.filterDomesticProxiesOnly.collectAsState()
    val showMapView by viewModel.showProxyMapView.collectAsState()

    val context = LocalContext.current

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
                    Text("جمع‌آوری پروکسی‌ها", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = { viewModel.testAllProxies() },
                    enabled = !isTesting && rawProxies.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.testTag("test_proxies_button")
                ) {
                    if (isTesting) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), color = Color.White, strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("تست (${testProgress.first}/${testProgress.second})", fontSize = 11.sp)
                    } else {
                        Icon(Icons.Default.Speed, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("تست سریع همه", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                OutlinedButton(
                    onClick = { viewModel.showProxyMapView.value = !showMapView },
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.testTag("toggle_proxy_map_button")
                ) {
                    Icon(Icons.Default.Map, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (showMapView) "بستن نقشه" else "🗺️ نمایش روی نقشه", fontSize = 11.sp)
                }

                OutlinedButton(
                    onClick = {
                        viewModel.deleteFailedProxies { count ->
                            Toast.makeText(context, "$count پروکسی قطع و خراب حذف شدند", Toast.LENGTH_SHORT).show()
                        }
                    },
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.testTag("delete_failed_proxies_button")
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("حذف نامعتبرها", fontSize = 11.sp, color = Color(0xFFEF4444))
                }
            }

            if (fetchProgress.isRunning) {
                Spacer(modifier = Modifier.height(6.dp))
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text(
                    text = "در حال دریافت: ${fetchProgress.currentSource}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            if (isTesting) {
                Spacer(modifier = Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = {
                        if (testProgress.second > 0) testProgress.first.toFloat() / testProgress.second.toFloat() else 0f
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Collapsible World Map View for Proxies
            AnimatedVisibility(visible = showMapView) {
                Column(modifier = Modifier.padding(vertical = 8.dp)) {
                    GlobalMapView(
                        countries = proxyCountryDistribution,
                        selectedCountry = selectedCountry,
                        onCountrySelected = { code -> viewModel.selectedProxyCountry.value = code },
                        onCloseMap = { viewModel.showProxyMapView.value = false },
                        title = "پراکندگی جهانی پروکسی‌ها",
                        itemTypeLabel = "پروکسی"
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Filter Chips by Protocol & Domestic Location
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilterChip(
                    selected = selectedType == null,
                    onClick = { viewModel.selectedProxyType.value = null },
                    label = { Text("همه پروتکل‌ها", fontSize = 11.sp) }
                )
                ProxyType.entries.forEach { type ->
                    FilterChip(
                        selected = selectedType == type,
                        onClick = { viewModel.selectedProxyType.value = if (selectedType == type) null else type },
                        label = { Text(type.displayName, fontSize = 11.sp) }
                    )
                }
                FilterChip(
                    selected = filterDomesticOnly,
                    onClick = { viewModel.filterDomesticProxiesOnly.value = !filterDomesticOnly },
                    label = { Text("⚡ سرور داخلی ایران", fontSize = 11.sp) }
                )
                if (selectedCountry != null) {
                    FilterChip(
                        selected = true,
                        onClick = { viewModel.selectedProxyCountry.value = null },
                        label = { Text("فیلتر کشور: $selectedCountry ✕", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Subtitle Counter
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${proxies.size} پروکسی آماده اتصال (${proxies.count { it.isHealthy }} تست‌شده سالم)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Proxy List
            if (proxies.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "هیچ پروکسی مطابق فیلتر یافت نشد",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = { viewModel.fetchAllProxies() },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Text("دریافت فوری پروکسی‌ها از سراسر اینترنت")
                        }
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(proxies, key = { it.id }) { proxy ->
                        ProxyItemCard(
                            proxy = proxy,
                            onTest = { viewModel.testSingleProxy(proxy) },
                            onCopy = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clipText = if (proxy.type == ProxyType.MTPROTO) {
                                    "tg://proxy?server=${proxy.host}&port=${proxy.port}&secret=${proxy.secretOrAuth ?: ""}"
                                } else {
                                    "${proxy.type.name.lowercase()}://${proxy.host}:${proxy.port}"
                                }
                                clipboard.setPrimaryClip(ClipData.newPlainText("Proxy", clipText))
                                Toast.makeText(context, "پروکسی کپی شد", Toast.LENGTH_SHORT).show()
                            },
                            onUse = {
                                if (proxy.type == ProxyType.MTPROTO) {
                                    val tgUri = "tg://proxy?server=${proxy.host}&port=${proxy.port}&secret=${proxy.secretOrAuth ?: ""}"
                                    try {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(tgUri))
                                        context.startActivity(intent)
                                    } catch (_: Exception) {
                                        Toast.makeText(context, "تلگرام روی دستگاه یافت نشد", Toast.LENGTH_SHORT).show()
                                    }
                                } else {
                                    val uri = "${proxy.type.name.lowercase()}://${proxy.host}:${proxy.port}"
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("Proxy", uri))
                                    Toast.makeText(context, "آدرس پروکسی کپی شد: $uri", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ProxyItemCard(
    proxy: ProxyItem,
    onTest: () -> Unit,
    onCopy: () -> Unit,
    onUse: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("proxy_item_${proxy.id}"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
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
                        fontWeight = FontWeight.Medium
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
                        fontSize = 13.sp
                    )
                    Text(
                        text = "منبع: ${proxy.sourceName}",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    OutlinedButton(
                        onClick = onTest,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Icon(Icons.Default.Speed, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(2.dp))
                        Text("تست", fontSize = 10.sp)
                    }

                    IconButton(onClick = onCopy, modifier = Modifier.size(34.dp)) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "کپی", modifier = Modifier.size(16.dp))
                    }

                    Button(
                        onClick = onUse,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(if (proxy.type == ProxyType.MTPROTO) "تلگرام" else "استفاده", fontSize = 10.sp)
                    }
                }
            }
        }
    }
}
