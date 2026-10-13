package com.example

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.components.RtlProvider
import com.example.ui.screens.ConfigsScreen
import com.example.ui.screens.DiagnosticsScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.SourcesScreen
import com.example.ui.theme.AzadiYarTheme
import com.example.ui.viewmodel.MainViewModel

import androidx.compose.material.icons.filled.VpnLock
import com.example.ui.screens.ProxiesScreen

enum class AppScreen(val titlePersian: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    HOME("داشبورد", Icons.Default.Home),
    CONFIGS("کانفیگ‌ها", Icons.Default.Tune),
    PROXIES("پروکسی‌ها", Icons.Default.VpnLock),
    SOURCES("منابع", Icons.Default.Dns),
    DIAGNOSTICS("عیب‌یابی", Icons.Default.MedicalServices),
    SETTINGS("تنظیمات", Icons.Default.Settings)
}

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            AzadiYarTheme {
                MainAppScaffold()
            }
        }
    }
}

@Composable
fun MainAppScaffold(
    viewModel: MainViewModel = viewModel()
) {
    var currentScreen by remember { mutableStateOf(AppScreen.HOME) }
    val context = LocalContext.current

    // VPN Permission Launcher
    val vpnPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.connectSmart()
        }
    }

    // Android 13+ Notification Permission Launcher
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ -> }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // Handle back button on sub-screens
    if (currentScreen != AppScreen.HOME) {
        BackHandler {
            currentScreen = AppScreen.HOME
        }
    }

    RtlProvider {
        Scaffold(
            bottomBar = {
                NavigationBar(
                    tonalElevation = 6.dp,
                    modifier = Modifier.testTag("bottom_nav_bar")
                ) {
                    AppScreen.entries.forEach { screen ->
                        NavigationBarItem(
                            selected = currentScreen == screen,
                            onClick = { currentScreen = screen },
                            icon = {
                                Icon(
                                    imageVector = screen.icon,
                                    contentDescription = screen.titlePersian
                                )
                            },
                            label = { Text(screen.titlePersian, fontSize = 11.sp) },
                            modifier = Modifier.testTag("nav_${screen.name.lowercase()}")
                        )
                    }
                }
            },
            modifier = Modifier.fillMaxSize()
        ) { innerPadding ->
            when (currentScreen) {
                AppScreen.HOME -> HomeScreen(
                    viewModel = viewModel,
                    onNavigateToConfigs = { currentScreen = AppScreen.CONFIGS },
                    onNavigateToDiagnostics = { currentScreen = AppScreen.DIAGNOSTICS },
                    onNavigateToSources = { currentScreen = AppScreen.SOURCES },
                    modifier = Modifier.padding(innerPadding)
                )

                AppScreen.CONFIGS -> ConfigsScreen(
                    viewModel = viewModel,
                    modifier = Modifier.padding(innerPadding)
                )

                AppScreen.PROXIES -> ProxiesScreen(
                    viewModel = viewModel,
                    modifier = Modifier.padding(innerPadding)
                )

                AppScreen.SOURCES -> SourcesScreen(
                    viewModel = viewModel,
                    modifier = Modifier.padding(innerPadding)
                )

                AppScreen.DIAGNOSTICS -> DiagnosticsScreen(
                    viewModel = viewModel,
                    modifier = Modifier.padding(innerPadding)
                )

                AppScreen.SETTINGS -> SettingsScreen(
                    viewModel = viewModel,
                    modifier = Modifier.padding(innerPadding)
                )
            }
        }
    }
}
