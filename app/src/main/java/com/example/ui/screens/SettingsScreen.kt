package com.example.ui.screens

import android.widget.Toast
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
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import com.example.ui.components.RtlProvider
import com.example.ui.viewmodel.MainViewModel

@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val autoRecovery by viewModel.autoRecovery.collectAsState()
    val dnsTunnel by viewModel.dnsTunnelConfig.collectAsState()
    val bridgeConfig by viewModel.bridgeConfig.collectAsState()

    var showClearDialog by remember { mutableStateOf(false) }
    var showDnsDialog by remember { mutableStateOf(false) }
    var showBridgeDialog by remember { mutableStateOf(false) }

    val context = LocalContext.current

    RtlProvider {
        Column(
            modifier = modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Text(
                text = "تنظیمات و پل‌های ارتباطی",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "مدیریت بازیابی خودکار، پل‌های واسط، تونل DNS و داده‌های محلی",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Auto-Recovery Setting
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "بازیابی خودکار اتصال (Auto-Recovery)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        Text(
                            text = "در صورت قطع ارتباط سرور، به صورت خودکار سالم‌ترین کانفیگ بعدی جایگزین می‌شود.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = autoRecovery,
                        onCheckedChange = { viewModel.setAutoRecovery(it) },
                        modifier = Modifier.testTag("auto_recovery_switch")
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Bridge / Intermediate Server Section
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
                            Icon(Icons.Default.Lan, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("پل‌های واسط و خردکردن بسته‌ها (Fragmentation)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = if (bridgeConfig.isEnabled)
                            "فعال: آدرس پل ${bridgeConfig.bridgeHost} | خردسازی: ${if (bridgeConfig.enableFragmentation) "فعال" else "غیرفعال"}"
                        else
                            "پل واسط غیرفعال است. امکان تنظیم آی‌پی تمیز (Clean IP) و خردسازی بسته‌های TLS جهت عبور از DPI فراهم است.",
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

            // DNS Tunnel (dnstt) Section
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Dns, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("تونل DNS (پروتکل dnstt)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "وضعیت: ${dnsTunnel.serverStatus}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFF59E0B)
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "توجه فنی: تونل DNS نیازمند دامنه با رکورد NS اختصاصی و سرور dnstt سمت مقابل است. بازبودن پورت ۵۳ به تنهایی اینترنت آزاد فراهم نمی‌کند.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
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

            // Database & Cache Management
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
                        text = "حذف تمام کانفیگ‌های ذخیره‌شده و بازنشانی نتایج تست.",
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
                        Text("پاکسازی تمام کانفیگ‌ها", fontSize = 11.sp)
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
                        text = "نسخه ۱.۰.۰ | ابزار متن‌باز، بدون لاگ و بدون ردیابی ترافیک کاربر. تمام عملیات تفکیک، رمزنگاری و تست‌ها مستقیماً روی دستگاه انجام می‌شود.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Clear Dialog
        if (showClearDialog) {
            AlertDialog(
                onDismissRequest = { showClearDialog = false },
                title = { Text("پاکسازی پایگاه داده کانفیگ‌ها") },
                text = { Text("آیا مطمئن هستید؟ تمام کانفیگ‌های دریافت‌شده حذف خواهند شد.") },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.clearAllConfigs()
                            showClearDialog = false
                            Toast.makeText(context, "تمام کانفیگ‌ها پاکسازی شدند", Toast.LENGTH_SHORT).show()
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
            var isEnabled by remember { mutableStateOf(dnsTunnel.isEnabled) }

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
