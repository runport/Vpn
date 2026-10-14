package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.diagnostics.AppLogLevel
import com.example.diagnostics.LogItem
import com.example.ui.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogsScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val logs by viewModel.systemLogs.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var selectedLevel by remember { mutableStateOf<AppLogLevel?>(null) }
    var selectedTag by remember { mutableStateOf<String?>(null) }

    val listState = rememberLazyListState()

    val filteredLogs = remember(logs, searchQuery, selectedLevel, selectedTag) {
        logs.filter { item ->
            val matchesQuery = searchQuery.isBlank() ||
                    item.message.contains(searchQuery, ignoreCase = true) ||
                    item.tag.contains(searchQuery, ignoreCase = true) ||
                    (item.details?.contains(searchQuery, ignoreCase = true) == true)

            val matchesLevel = selectedLevel == null || item.level == selectedLevel
            val matchesTag = selectedTag == null || item.tag.equals(selectedTag, ignoreCase = true)

            matchesQuery && matchesLevel && matchesTag
        }
    }

    // Auto-scroll to bottom on new log entry if near bottom
    LaunchedEffect(filteredLogs.size) {
        if (filteredLogs.isNotEmpty()) {
            listState.animateScrollToItem(filteredLogs.size - 1)
        }
    }

    val tags = remember(logs) {
        logs.map { it.tag }.distinct()
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.Terminal, contentDescription = null, tint = Color(0xFF00E676))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("لاگ‌های زنده سیستم", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.width(8.dp))
                        BadgedBox(badge = {
                            Badge(containerColor = MaterialTheme.colorScheme.primaryContainer) {
                                Text("${logs.size}")
                            }
                        }) {}
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "بازگشت")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        val fullLog = viewModel.exportLogs()
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("AzadiYar Logs", fullLog)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, "تمام لاگ‌ها در کلیپ‌بورد کپی شدند", Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(imageVector = Icons.Default.ContentCopy, contentDescription = "کپی لاگ‌ها")
                    }
                    IconButton(onClick = {
                        val fullLog = viewModel.exportLogs()
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, "گزارش لاگ‌های آزادیار")
                            putExtra(Intent.EXTRA_TEXT, fullLog)
                        }
                        context.startActivity(Intent.createChooser(intent, "اشتراک‌گذاری لاگ‌ها"))
                    }) {
                        Icon(imageVector = Icons.Default.Share, contentDescription = "اشتراک‌گذاری")
                    }
                    IconButton(onClick = {
                        viewModel.clearLogs()
                        Toast.makeText(context, "لاگ‌ها پاک شدند", Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(imageVector = Icons.Default.DeleteSweep, contentDescription = "پاک‌سازی")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Search Input
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                placeholder = { Text("جستجو در پیام‌ها یا تگ‌های لاگ...", style = MaterialTheme.typography.bodySmall) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(20.dp)) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.ClearAll, contentDescription = "پاک‌سازی جستجو")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            // Filter Chips (Level)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilterChip(
                    selected = selectedLevel == null,
                    onClick = { selectedLevel = null },
                    label = { Text("همه سطوح (${logs.size})") }
                )
                AppLogLevel.values().forEach { level ->
                    val count = logs.count { it.level == level }
                    FilterChip(
                        selected = selectedLevel == level,
                        onClick = { selectedLevel = if (selectedLevel == level) null else level },
                        label = { Text("${level.titlePersian} ($count)") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(level.badgeColor).copy(alpha = 0.2f),
                            selectedLabelColor = Color(level.badgeColor)
                        )
                    )
                }
            }

            // Filter Chips (Tags)
            if (tags.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = selectedTag == null,
                        onClick = { selectedTag = null },
                        label = { Text("تمام بخش‌ها") }
                    )
                    tags.forEach { tag ->
                        FilterChip(
                            selected = selectedTag == tag,
                            onClick = { selectedTag = if (selectedTag == tag) null else tag },
                            label = { Text(tag) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Terminal Logs Canvas
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF0D1117)) // Modern GitHub dark terminal color
                    .padding(8.dp)
            ) {
                if (filteredLogs.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = if (logs.isEmpty()) "هیچ لاگی ثبت نشده است" else "هیچ موردی مطابق فیلتر یافت نشد",
                            color = Color.Gray,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(filteredLogs, key = { it.id }) { logItem ->
                            LogItemRow(logItem)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LogItemRow(item: LogItem) {
    val levelColor = when (item.level) {
        AppLogLevel.DEBUG -> Color(0xFF8B949E)
        AppLogLevel.INFO -> Color(0xFF58A6FF)
        AppLogLevel.SUCCESS -> Color(0xFF3FB950)
        AppLogLevel.WARN -> Color(0xFFD29922)
        AppLogLevel.ERROR -> Color(0xFFF85149)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFF161B22))
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Timestamp
            Text(
                text = item.formattedTime,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp
                ),
                color = Color(0xFF8B949E)
            )

            Spacer(modifier = Modifier.width(6.dp))

            // Level Badge
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(levelColor.copy(alpha = 0.2f))
                    .padding(horizontal = 4.dp, vertical = 1.dp)
            ) {
                Text(
                    text = item.level.name,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = levelColor
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            // Tag
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFF30363D))
                    .padding(horizontal = 4.dp, vertical = 1.dp)
            ) {
                Text(
                    text = item.tag,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp
                    ),
                    color = Color(0xFFC9D1D9)
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Message
        Text(
            text = item.message,
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                lineHeight = 16.sp
            ),
            color = Color(0xFFE6EDF3)
        )

        // Stacktrace / Details if available
        if (!item.details.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = item.details,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp
                ),
                color = Color(0xFFFFA198)
            )
        }
    }
}
