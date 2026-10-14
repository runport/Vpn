package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AltRoute
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SettingsEthernet
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.example.core.model.BridgeConfig
import com.example.core.model.DnsTunnelConfig
import com.example.core.model.RoutingMode
import com.example.core.model.TestMode
import com.example.ui.components.PerAppProxyDialog
import com.example.ui.components.RtlProvider
import com.example.ui.viewmodel.MainViewModel

@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val settings by viewModel.v2raySettings.collectAsState()
    val dnsTunnel by viewModel.dnsTunnelConfig.collectAsState()
    val bridgeConfig by viewModel.bridgeConfig.collectAsState()

    var showClearDialog by remember { mutableStateOf(false) }
    var showDnsDialog by remember { mutableStateOf(false) }
    var showBridgeDialog by remember { mutableStateOf(false) }
    var showPerAppDialog by remember { mutableStateOf(false) }

    val context = LocalContext.current

    RtlProvider {
        Column(
            modifier = modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Text(
                text = "تنظیمات پیشرفته آزادیار (v2rayNG Core)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "پیکربندی تفکیک برنامه‌ها، روتینگ، دی‌ان‌اس، تست تاخیر واقعی و پروتکل‌ها",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 1. ROUTING MODE SECTION
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AltRoute, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("حالت‌های مسیریابی ترافیک (Routing Rules)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    RoutingMode.values().forEach { mode ->
                        val isSelected = settings.routingMode == mode
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface,
                            border = if (isSelected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp)
                                .clickable {
                                    viewModel.updateV2raySettings(settings.copy(routingMode = mode))
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = { viewModel.updateV2raySettings(settings.copy(routingMode = mode)) }
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Column {
                                    Text(text = mode.titlePersian, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                    Text(text = mode.descPersian, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 2. PER-APP PROXY SECTION
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Apps, contentDescription = null, tint = Color(0xFF8B5CF6), modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("تفکیک برنامه‌ها (Per-App Proxy)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                        Switch(
                            checked = settings.perAppProxyEnabled,
                            onCheckedChange = {
                                viewModel.updateV2raySettings(settings.copy(perAppProxyEnabled = it))
                            },
                            modifier = Modifier.testTag("per_app_proxy_switch")
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = if (settings.perAppProxyEnabled) {
                            "فعال: ${settings.selectedApps.size} برنامه انتخاب شده (${if (settings.perAppProxyBypassMode) "معاف‌سازی" else "تونل اختصاصی"})"
                        } else {
                            "غیرفعال: همه برنامه‌های دستگاه به صورت یکپارچه از فیلترشکن استفاده می‌کنند."
                        },
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedButton(
                        onClick = { showPerAppDialog = true },
                        shape = RoundedCornerShape(8.dp),
                        enabled = settings.perAppProxyEnabled,
                        modifier = Modifier.testTag("manage_per_app_button")
                    ) {
                        Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("انتخاب و مدیریت برنامه‌ها (${settings.selectedApps.size})", fontSize = 11.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 3. TESTING ENGINE & AUTOMATION SECTION
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Speed, contentDescription = null, tint = Color(0xFF0288D1), modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("تست تاخیر واقعی و خودکارسازی (Real Delay)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Test Mode Chips
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TestMode.values().forEach { mode ->
                            FilterChip(
                                selected = settings.testMode == mode,
                                onClick = { viewModel.updateV2raySettings(settings.copy(testMode = mode)) },
                                label = { Text(mode.titlePersian, fontSize = 11.sp) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "آدرس URL برای تست تاخیر واقعی (generate_204):",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = settings.realDelayTestUrl,
                        onValueChange = { viewModel.updateV2raySettings(settings.copy(realDelayTestUrl = it)) },
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    // Preset buttons
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(
                            onClick = { viewModel.updateV2raySettings(settings.copy(realDelayTestUrl = "https://www.gstatic.com/generate_204")) },
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text("Google gstatic", fontSize = 10.sp)
                        }
                        OutlinedButton(
                            onClick = { viewModel.updateV2raySettings(settings.copy(realDelayTestUrl = "https://cp.cloudflare.com/generate_204")) },
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text("Cloudflare cp", fontSize = 10.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Auto delete failed configs switch
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("حذف خودکار کانفیگ‌های نامعتبر پس از تست", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Text("کانفیگ‌های تایم‌اوت و بدون پاسخ پس از اتمام تست فوراً حذف می‌شوند.", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = settings.autoDeleteInvalidAfterTest,
                            onCheckedChange = { viewModel.updateV2raySettings(settings.copy(autoDeleteInvalidAfterTest = it)) }
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Auto sort configs by ping
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("مرتب‌سازی خودکار بر اساس کمترین پینگ", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Text("پس از پایان تست، سالم‌ترین و پرسرعت‌ترین کانفیگ‌ها در بالای لیست قرار می‌گیرند.", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = settings.autoSortAfterTest,
                            onCheckedChange = { viewModel.updateV2raySettings(settings.copy(autoSortAfterTest = it)) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 4. DNS CONFIGURATION SECTION
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Dns, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("تنظیمات سرور DNS (ضد مسمومیت DNS)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text("سرور DNS راه دور (Remote DNS):", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = settings.remoteDns,
                        onValueChange = { viewModel.updateV2raySettings(settings.copy(remoteDns = it)) },
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    // Preset DNS buttons
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(
                            onClick = { viewModel.updateV2raySettings(settings.copy(remoteDns = "1.1.1.1")) },
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text("Cloudflare 1.1.1.1", fontSize = 10.sp)
                        }
                        OutlinedButton(
                            onClick = { viewModel.updateV2raySettings(settings.copy(remoteDns = "8.8.8.8")) },
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text("Google 8.8.8.8", fontSize = 10.sp)
                        }
                        OutlinedButton(
                            onClick = { viewModel.updateV2raySettings(settings.copy(remoteDns = "94.140.14.14")) },
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text("AdGuard", fontSize = 10.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Fake DNS toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("خنثی‌سازی مسمومیت DNS (Fake DNS Protection)", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Text("از جعل آدرس‌های اینترنتی توسط شبکه جلوگیری می‌کند.", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = settings.enableFakeDns,
                            onCheckedChange = { viewModel.updateV2raySettings(settings.copy(enableFakeDns = it)) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 5. TUNNEL & ADVANCED NETWORK SETTINGS
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.SettingsEthernet, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("تنظیمات فنی تونل (MTU و اشتراک هات‌اسپات)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text("اندازه بسته MTU تونل شبکه:", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(1280, 1360, 1400, 1500).forEach { mtuVal ->
                            FilterChip(
                                selected = settings.mtu == mtuVal,
                                onClick = { viewModel.updateV2raySettings(settings.copy(mtu = mtuVal)) },
                                label = {
                                    Text(
                                        text = if (mtuVal == 1360) "$mtuVal (ایران)" else mtuVal.toString(),
                                        fontSize = 11.sp
                                    )
                                }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Enable UDP toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("فعال‌سازی ترافیک UDP (تماس صوتی و بازی)", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Text("برای برقراری تماس صوتی تلگرام، واتساپ و کاهش پینگ بازی‌ها لازم است.", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = settings.enableUdp,
                            onCheckedChange = { viewModel.updateV2raySettings(settings.copy(enableUdp = it)) }
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Share LAN toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("اشتراک‌گذاری در شبکه محلی (Share LAN / هات‌اسپات)", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Text("امکان اتصال لپ‌تاپ یا سایر گوشی‌ها از طریق پورت SOCKS5 محلی (۱۰۸۰۸)", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = settings.allowLanSharing,
                            onCheckedChange = { viewModel.updateV2raySettings(settings.copy(allowLanSharing = it)) }
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Auto reconnect toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("اتصال مجدد خودکار (Auto Reconnect)", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Text("در صورت قطعی ارتباط یا تغییر اینترنت (وای‌فای/همراه)، بهترین مسیر سالم به سرعت وصل می‌شود.", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = settings.autoReconnect,
                            onCheckedChange = { viewModel.updateV2raySettings(settings.copy(autoReconnect = it)) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 6. BRIDGE & FRAGMENTATION
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Lan, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("پل‌های واسط و خردکردن بسته‌ها (TLS Fragmentation)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = if (bridgeConfig.isEnabled)
                            "فعال: آدرس پل ${bridgeConfig.bridgeHost} | خردسازی: ${if (bridgeConfig.enableFragmentation) "فعال" else "غیرفعال"}"
                        else
                            "پل واسط غیرفعال است. امکان تنظیم آی‌پی تمیز و خردسازی بسته‌های ClientHello جهت عبور از DPI فراهم است.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedButton(
                        onClick = { showBridgeDialog = true },
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("پیکربندی پل واسط و خردسازی", fontSize = 11.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 7. DNS TUNNEL (dnstt)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.NetworkCheck, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("تونل DNS اختصاصی (پروتکل dnstt)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "وضعیت: ${dnsTunnel.serverStatus}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFF59E0B)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedButton(
                        onClick = { showDnsDialog = true },
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("تنظیم مشخصات سرور DNS Tunnel", fontSize = 11.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 8. DATABASE & CACHE MANAGEMENT
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("مدیریت حافظه و داده‌های محلی", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "پاکسازی کامل تمام کانفیگ‌ها و پروکسی‌های ثبت‌شده.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Button(
                        onClick = { showClearDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.testTag("clear_all_configs_button")
                    ) {
                        Text("پاکسازی تمام کانفیگ‌ها و پروکسی‌ها", fontSize = 11.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // About AzadiYar
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Security, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("درباره آزادیار (AzadiYar)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "نسخه ۱.۱.۰ | مجهز به هسته سنجش تاخیر واقعی (Real Delay Test)، تفکیک برنامه‌ها (Per-App Proxy)، مسیریابی ضد تحریم و پروکسی چندگانه. تمام عملیات رمزنگاری و تست روی دستگاه انجام می‌شود.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Per-App Proxy Dialog
        if (showPerAppDialog) {
            PerAppProxyDialog(
                viewModel = viewModel,
                onDismiss = { showPerAppDialog = false }
            )
        }

        // Clear Dialog
        if (showClearDialog) {
            AlertDialog(
                onDismissRequest = { showClearDialog = false },
                title = { Text("پاکسازی پایگاه داده") },
                text = { Text("آیا مطمئن هستید؟ تمام کانفیگ‌ها و پروکسی‌های ذخیره‌شده حذف خواهند شد.") },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.clearAllConfigs()
                            viewModel.clearAllProxies()
                            showClearDialog = false
                            Toast.makeText(context, "تمام داده‌ها پاکسازی شدند", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                    ) {
                        Text("بله، پاک شود")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showClearDialog = false }) {
                        Text("انصراف")
                    }
                }
            )
        }

        // DNS Tunnel Dialog
        if (showDnsDialog) {
            var domain by remember { mutableStateOf(dnsTunnel.domain) }
            var resolver by remember { mutableStateOf(dnsTunnel.resolverIp) }
            var pubKey by remember { mutableStateOf(dnsTunnel.pubKey) }

            AlertDialog(
                onDismissRequest = { showDnsDialog = false },
                title = { Text("پیکربندی سرور DNS Tunnel (dnstt)") },
                text = {
                    Column {
                        Text("برای استفاده از این روش باید سرور اختصاصی dnstt-server را راه‌اندازی کرده باشید.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = domain,
                            onValueChange = { domain = it },
                            label = { Text("دامنه تونل (دارای رکورد NS)") },
                            placeholder = { Text("t.example.com") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = resolver,
                            onValueChange = { resolver = it },
                            label = { Text("آی‌پی ریزالور DNS") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = pubKey,
                            onValueChange = { pubKey = it },
                            label = { Text("کلید عمومی سرور (Public Key)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        viewModel.updateDnsTunnel(
                            DnsTunnelConfig(
                                domain = domain,
                                resolverIp = resolver,
                                pubKey = pubKey,
                                isEnabled = domain.isNotBlank(),
                                serverStatus = if (domain.isNotBlank()) "پیکربندی ثبت شد (آماده تست سرور)" else "نیازمند سرور اختصاصی dnstt"
                            )
                        )
                        showDnsDialog = false
                    }) {
                        Text("ذخیره")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDnsDialog = false }) {
                        Text("انصراف")
                    }
                }
            )
        }

        // Bridge & Fragmentation Dialog
        if (showBridgeDialog) {
            var host by remember { mutableStateOf(bridgeConfig.bridgeHost) }
            var port by remember { mutableStateOf(bridgeConfig.bridgePort.toString()) }
            var sni by remember { mutableStateOf(bridgeConfig.fakeSni) }
            var frag by remember { mutableStateOf(bridgeConfig.enableFragmentation) }

            AlertDialog(
                onDismissRequest = { showBridgeDialog = false },
                title = { Text("تنظیمات پل واسط و خردسازی TLS") },
                text = {
                    Column {
                        OutlinedTextField(
                            value = host,
                            onValueChange = { host = it },
                            label = { Text("آدرس پل / آی‌پی تمیز") },
                            placeholder = { Text("مثال: clean.cloudflare.com") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = port,
                            onValueChange = { port = it },
                            label = { Text("پورت پل") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = sni,
                            onValueChange = { sni = it },
                            label = { Text("SNI جعلی / جایگزین") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("خردکردن بسته‌های ClientHello (TLS Frag)", fontSize = 12.sp, modifier = Modifier.weight(1f))
                            Switch(checked = frag, onCheckedChange = { frag = it })
                        }
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        viewModel.updateBridge(
                            BridgeConfig(
                                bridgeHost = host,
                                bridgePort = port.toIntOrNull() ?: 443,
                                fakeSni = sni,
                                enableFragmentation = frag,
                                isEnabled = host.isNotBlank() || frag
                            )
                        )
                        showBridgeDialog = false
                    }) {
                        Text("ذخیره")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showBridgeDialog = false }) {
                        Text("انصراف")
                    }
                }
            )
        }
    }
}
