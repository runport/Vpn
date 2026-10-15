package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.model.ConfigItem
import com.example.core.model.ConnectionState
import com.example.core.model.ProxyProtocol
import com.example.ui.components.ImportConfigDialog
import com.example.ui.components.PingBadge
import com.example.ui.components.ProtocolBadge
import com.example.ui.components.RtlProvider
import com.example.ui.components.ShareQrDialog
import com.example.ui.components.StatusBadge
import com.example.ui.viewmodel.ConfigSortOption
import com.example.ui.viewmodel.MainViewModel

@Composable
fun ConfigsScreen(
    viewModel: MainViewModel,
    onRequestConnect: (action: () -> Unit) -> Unit = { it() },
    modifier: Modifier = Modifier
) {
    val configs by viewModel.filteredConfigs.collectAsState()
    val connectionState by viewModel.connectionState.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedProtocol by viewModel.selectedProtocol.collectAsState()
    val selectedCountry by viewModel.selectedCountry.collectAsState()
    val onlyBypass by viewModel.filterOnlyBypassNationalNet.collectAsState()
    val onlyHalfPrice by viewModel.filterOnlyHalfPrice.collectAsState()
    val sortOption by viewModel.sortOption.collectAsState()
    val isTestingAll by viewModel.isTestingAll.collectAsState()
    val testProgress by viewModel.testProgress.collectAsState()
    val countryDistribution by viewModel.countryDistribution.collectAsState()
    val showMapView by viewModel.showMapView.collectAsState()
    val fetchProgress by viewModel.fetchProgress.collectAsState()

    var showImportDialog by remember { mutableStateOf(false) }
    var shareQrConfig by remember { mutableStateOf<ConfigItem?>(null) }

    val context = LocalContext.current

    val connectedConfigId = (connectionState as? ConnectionState.Connected)?.config?.id
    val selectedConfigId by viewModel.selectedConfigId.collectAsState()
    val selectedTargetType by viewModel.selectedTargetType.collectAsState()

    RtlProvider {
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            // Search Bar & Import Action
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { viewModel.searchQuery.value = it },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("configs_search_input"),
                    placeholder = { Text("جستجو در کانفیگ‌ها، سرور یا کشور...", fontSize = 12.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.searchQuery.value = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "پاک کردن", modifier = Modifier.size(16.dp))
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )

                Button(
                    onClick = { showImportDialog = true },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier.height(52.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("افزودن", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Action Buttons: Collect, Test Batch, Map, Delete Failed
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Fetch All Configs button
                Button(
                    onClick = { viewModel.fetchAllSources() },
                    enabled = !fetchProgress.isRunning,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.testTag("collect_configs_button")
                ) {
                    if (fetchProgress.isRunning) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), color = Color.White, strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        if (fetchProgress.isRunning) "در حال دریافت..." else "دریافت کانفیگ‌ها",
                        fontSize = 11.sp
                    )
                }

                // Batch Real Delay Test button (Primary v2rayNG accurate test)
                Button(
                    onClick = { viewModel.testAllConfigs(com.example.core.model.TestMode.REAL_DELAY) },
                    enabled = !isTestingAll,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0288D1)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.testTag("test_all_configs_button")
                ) {
                    if (isTestingAll) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), color = Color.White, strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.Speed, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        if (isTestingAll) "در حال تست تاخیر..." else "تست تاخیر واقعی همه",
                        fontSize = 11.sp
                    )
                }

                // Batch TCPing Test button
                OutlinedButton(
                    onClick = { viewModel.testAllConfigs(com.example.core.model.TestMode.TCPING) },
                    enabled = !isTestingAll,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.testTag("test_tcp_button")
                ) {
                    Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color(0xFFF59E0B))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("تست TCP", fontSize = 11.sp)
                }

                // Toggle Map button
                OutlinedButton(
                    onClick = { viewModel.showMapView.value = !showMapView },
                    shape = RoundedCornerShape(8.dp),
                    colors = if (showMapView) ButtonDefaults.outlinedButtonColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)) else ButtonDefaults.outlinedButtonColors()
                ) {
                    Icon(Icons.Default.Map, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (showMapView) "پنهان‌سازی نقشه" else "نمایش روی نقشه", fontSize = 11.sp)
                }

                // Delete Failed Configs Button
                OutlinedButton(
                    onClick = {
                        viewModel.deleteFailedConfigs { count ->
                            Toast.makeText(context, "$count کانفیگ نامعتبر حذف شد", Toast.LENGTH_SHORT).show()
                        }
                    },
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.testTag("delete_failed_configs_button")
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color(0xFFEF4444))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("حذف نامعتبرها", fontSize = 11.sp, color = Color(0xFFEF4444))
                }

                // Delete Failed BOTH Configs & Proxies
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
                    modifier = Modifier.testTag("delete_all_failed_both_button")
                ) {
                    Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color(0xFFC62828))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("حذف یکجای همه نامعتبرها", fontSize = 10.sp, color = Color(0xFFC62828))
                }
            }

            // Testing Progress Bar
            AnimatedVisibility(visible = isTestingAll) {
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    val current = testProgress.first
                    val total = testProgress.second
                    val fraction = if (total > 0) current.toFloat() / total.toFloat() else 0f
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("پیشرفت تست با سرعت بالا:", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                        Text("$current از $total", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
                }
            }

            // Interactive Global Map
            AnimatedVisibility(visible = showMapView) {
                GlobalMapView(
                    countries = countryDistribution,
                    selectedCountry = selectedCountry,
                    onCountrySelected = { code ->
                        viewModel.selectedCountry.value = if (selectedCountry == code) null else code
                    },
                    onCloseMap = { viewModel.showMapView.value = false },
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Protocol & Filter Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilterChip(
                    selected = selectedProtocol == null,
                    onClick = { viewModel.selectedProtocol.value = null },
                    label = { Text("همه پروتکل‌ها (${configs.size})", fontSize = 11.sp) }
                )
                ProxyProtocol.values().forEach { proto ->
                    FilterChip(
                        selected = selectedProtocol == proto,
                        onClick = {
                            viewModel.selectedProtocol.value = if (selectedProtocol == proto) null else proto
                        },
                        label = { Text(proto.displayName, fontSize = 11.sp) }
                    )
                }
            }

            // Bypass & Half-Price and Country Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(
                    selected = onlyBypass,
                    onClick = { viewModel.filterOnlyBypassNationalNet.value = !onlyBypass },
                    label = { Text("🛡️ عبور از نت ملی", fontSize = 11.sp) }
                )
                FilterChip(
                    selected = onlyHalfPrice,
                    onClick = { viewModel.filterOnlyHalfPrice.value = !onlyHalfPrice },
                    label = { Text("⚡ نیم‌بها", fontSize = 11.sp) }
                )

                if (selectedCountry != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.clickable { viewModel.selectedCountry.value = null }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text("فیلتر کشور: $selectedCountry ✕", fontSize = 11.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }
                }
            }

            // Sort Options
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ConfigSortOption.values().forEach { sort ->
                    FilterChip(
                        selected = sortOption == sort,
                        onClick = { viewModel.sortOption.value = sort },
                        label = { Text(sort.titlePersian, fontSize = 11.sp) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Configs List
            if (configs.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "کانفیگی با این مشخصات یافت نشد",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "فیلترها را ریست کنید یا از دکمه «دریافت کانفیگ‌ها» یا «+ افزودن» استفاده کنید.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(configs, key = { it.id }) { item ->
                        val isItemConnected = connectedConfigId == item.id
                        val isItemSelected = (selectedTargetType == "CONFIG" && selectedConfigId == item.id)
                        ConfigListItemEnhanced(
                            config = item,
                            isConnected = isItemConnected,
                            isSelected = isItemSelected,
                            onSelect = { viewModel.selectConfig(item) },
                            onConnect = {
                                if (isItemConnected) {
                                    viewModel.disconnect()
                                } else {
                                    onRequestConnect {
                                        viewModel.connect(item)
                                    }
                                }
                            },
                            onTest = { viewModel.testSingleConfig(item) },
                            onDelete = { viewModel.deleteConfig(item) },
                            onShareQr = { shareQrConfig = item },
                            onCopy = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("Config Link", item.rawUri)
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, "لینک کانفیگ با موفقیت کپی شد", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }
            }
        }

        // Import Dialog
        if (showImportDialog) {
            ImportConfigDialog(
                onDismiss = { showImportDialog = false },
                onImportText = { text ->
                    viewModel.importFromRawText(text)
                    Toast.makeText(context, "در حال استخراج و ذخیره کانفیگ‌ها...", Toast.LENGTH_SHORT).show()
                }
            )
        }

        // Share QR Dialog
        if (shareQrConfig != null) {
            val item = shareQrConfig!!
            ShareQrDialog(
                title = item.remarks,
                subtitle = "${item.protocol.displayName} • ${item.server}:${item.port} • ${item.countryFlag} ${item.countryName}",
                contentToShare = item.rawUri,
                onDismiss = { shareQrConfig = null }
            )
        }
    }
}

@Composable
fun ConfigListItemEnhanced(
    config: ConfigItem,
    isConnected: Boolean,
    isSelected: Boolean = false,
    onSelect: () -> Unit = {},
    onConnect: () -> Unit,
    onTest: () -> Unit,
    onDelete: () -> Unit,
    onShareQr: () -> Unit,
    onCopy: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "greenPulse")
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
            .clip(RoundedCornerShape(14.dp))
            .clickable {
                onSelect()
            }
            .testTag("config_item_${config.id}"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isConnected) Color(0xFF0D2E1A) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        border = when {
            isConnected -> BorderStroke(2.dp, Color(0xFF00E676))
            isSelected -> BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
            else -> null
        }
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Selected Banner (when this config is selected for execution)
            if (isSelected && !isConnected) {
                Surface(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.RadioButtonChecked,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "کانفیگ انتخابی شما برای اجرا",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Text(
                            text = "آماده اتصال",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // Connected Active Banner (Visible when this config is connected)
            if (isConnected) {
                Surface(
                    color = Color(0xFF00E676).copy(alpha = 0.25f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .scale(pulseAlpha)
                                    .background(Color(0xFF00E676), CircleShape)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "کانفیگ متصل و فعال",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF00E676)
                            )
                        }

                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFF00E676),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // Header Row: Remarks & Badges
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(config.countryFlag, fontSize = 18.sp)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = config.remarks,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = if (isConnected) Color.White else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                ProtocolBadge(config.protocol)
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Server & Port & Country Info
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${config.server}:${config.port}",
                    fontSize = 12.sp,
                    color = if (isConnected) Color(0xFFA5D6A7) else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = config.countryName,
                    fontSize = 11.sp,
                    color = if (isConnected) Color(0xFFA5D6A7) else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Special Badges Row: Bypass National Net & Half-Price
            if (config.isBypassNationalNet || config.isHalfPriceDomestic) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    if (config.isBypassNationalNet) {
                        Surface(
                            color = Color(0xFF059669).copy(alpha = 0.18f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "🛡️ عبور از نت ملی",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF10B981),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    if (config.isHalfPriceDomestic) {
                        Surface(
                            color = Color(0xFFD97706).copy(alpha = 0.18f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "⚡ ترافیک نیم‌بها",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFF59E0B),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            // Status, Ping & Quality Score
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatusBadge(config.testStatus)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PingBadge(config.pingMs)
                    if (config.qualityScore > 0) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "${config.qualityScore}/100",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isConnected) Color(0xFF00E676) else MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Actions Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onConnect,
                    shape = RoundedCornerShape(8.dp),
                    colors = if (isConnected) {
                        ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828))
                    } else {
                        ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    },
                    modifier = Modifier.height(36.dp)
                ) {
                    Icon(
                        imageVector = if (isConnected) Icons.Default.Stop else Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        if (isConnected) "قطع اتصال" else "اتصال",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row {
                    IconButton(onClick = onShareQr, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.QrCode2, contentDescription = "کد QR و اشتراک", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = onTest, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.Refresh, contentDescription = "تست سرور", modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = onCopy, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "کپی لینک", modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.Delete, contentDescription = "حذف", tint = Color(0xFFEF4444), modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}
