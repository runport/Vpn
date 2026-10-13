package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.model.ConfigItem
import com.example.core.model.ProxyProtocol
import com.example.ui.components.ImportConfigDialog
import com.example.ui.components.PingBadge
import com.example.ui.components.ProtocolBadge
import com.example.ui.components.RtlProvider
import com.example.ui.components.StatusBadge
import com.example.ui.viewmodel.ConfigSortOption
import com.example.ui.viewmodel.MainViewModel

@Composable
fun ConfigsScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val configs by viewModel.filteredConfigs.collectAsState()
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

    var showImportDialog by remember { mutableStateOf(false) }

    val context = LocalContext.current

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

            // Action Buttons: Fetch Configs, Test All, Map, Delete Failed
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = { viewModel.fetchAllSources() },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.testTag("configs_fetch_button")
                ) {
                    Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("دریافت کانفیگ‌ها", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = { viewModel.testAllConfigs() },
                    enabled = !isTestingAll && configs.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.testTag("test_all_configs_button")
                ) {
                    if (isTestingAll) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("تست (${testProgress.first}/${testProgress.second})", fontSize = 11.sp)
                    } else {
                        Icon(Icons.Default.Speed, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("تست سریع همه", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                OutlinedButton(
                    onClick = { viewModel.showMapView.value = !showMapView },
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.testTag("toggle_map_button")
                ) {
                    Icon(Icons.Default.Map, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (showMapView) "بستن نقشه" else "🗺️ نمایش نقشه", fontSize = 11.sp)
                }

                OutlinedButton(
                    onClick = {
                        viewModel.deleteFailedConfigs { count ->
                            Toast.makeText(context, "$count کانفیگ ناموفق و خراب حذف شدند", Toast.LENGTH_SHORT).show()
                        }
                    },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("حذف ناموفق‌ها", fontSize = 11.sp, color = Color(0xFFEF4444))
                }
            }

            if (isTestingAll) {
                Spacer(modifier = Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = {
                        if (testProgress.second > 0) testProgress.first.toFloat() / testProgress.second.toFloat() else 0f
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Interactive World Map Component (Collapsible)
            AnimatedVisibility(visible = showMapView) {
                Column(modifier = Modifier.padding(vertical = 8.dp)) {
                    GlobalMapView(
                        countries = countryDistribution,
                        selectedCountry = selectedCountry,
                        onCountrySelected = { code -> viewModel.selectedCountry.value = code },
                        onCloseMap = { viewModel.showMapView.value = false }
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Special Filters: Bypass National Net & Half-Price
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilterChip(
                    selected = onlyBypass,
                    onClick = { viewModel.filterOnlyBypassNationalNet.value = !onlyBypass },
                    leadingIcon = {
                        Icon(Icons.Default.Security, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(16.dp))
                    },
                    label = { Text("🛡️ عبور از نت ملی", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                )

                FilterChip(
                    selected = onlyHalfPrice,
                    onClick = { viewModel.filterOnlyHalfPrice.value = !onlyHalfPrice },
                    leadingIcon = {
                        Icon(Icons.Default.Bolt, contentDescription = null, tint = Color(0xFFF59E0B), modifier = Modifier.size(16.dp))
                    },
                    label = { Text("⚡ ترافیک نیم‌بها", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                )

                FilterChip(
                    selected = selectedCountry == null,
                    onClick = { viewModel.selectedCountry.value = null },
                    label = { Text("همه کشورها", fontSize = 11.sp) }
                )

                countryDistribution.take(7).forEach { c ->
                    FilterChip(
                        selected = selectedCountry == c.countryCode,
                        onClick = {
                            viewModel.selectedCountry.value = if (selectedCountry == c.countryCode) null else c.countryCode
                        },
                        label = { Text("${c.flag} ${c.countryName}", fontSize = 11.sp) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Protocol & Sort Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilterChip(
                    selected = selectedProtocol == null,
                    onClick = { viewModel.selectedProtocol.value = null },
                    label = { Text("همه پروتکل‌ها", fontSize = 11.sp) }
                )
                ProxyProtocol.entries.forEach { proto ->
                    FilterChip(
                        selected = selectedProtocol == proto,
                        onClick = {
                            viewModel.selectedProtocol.value = if (selectedProtocol == proto) null else proto
                        },
                        label = { Text(proto.displayName, fontSize = 11.sp) }
                    )
                }

                ConfigSortOption.entries.forEach { sort ->
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
                            text = "فیلترها را ریست کنید یا از دکمه «+ افزودن» کانفیگ جدید وارد کنید.",
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
                        ConfigListItemEnhanced(
                            config = item,
                            onConnect = { viewModel.connect(item) },
                            onTest = { viewModel.testSingleConfig(item) },
                            onDelete = { viewModel.deleteConfig(item) },
                            onCopy = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("Config Link", item.rawUri)
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, "لینک کانفیگ کپی شد", Toast.LENGTH_SHORT).show()
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
                    val count = viewModel.importFromRawText(text)
                    Toast.makeText(context, "در حال پردازش و استخراج کانفیگ‌ها...", Toast.LENGTH_SHORT).show()
                }
            )
        }
    }
}

@Composable
fun ConfigListItemEnhanced(
    config: ConfigItem,
    onConnect: () -> Unit,
    onTest: () -> Unit,
    onDelete: () -> Unit,
    onCopy: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("config_item_${config.id}"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
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
                    Text(config.countryFlag, fontSize = 16.sp)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = config.remarks,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
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
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = config.countryName,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
                            color = Color(0xFF059669).copy(alpha = 0.15f),
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
                            color = Color(0xFFD97706).copy(alpha = 0.15f),
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
                            color = MaterialTheme.colorScheme.primary
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
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier.height(36.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("اتصال", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

                Row {
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
