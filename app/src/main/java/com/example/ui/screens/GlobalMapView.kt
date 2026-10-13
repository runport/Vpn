package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.model.CountryStats
import kotlin.math.sqrt

@Composable
fun GlobalMapView(
    countries: List<CountryStats>,
    selectedCountry: String?,
    onCountrySelected: (String?) -> Unit,
    onCloseMap: () -> Unit,
    title: String = "نقشه پراکندگی جهانی سرورها",
    itemTypeLabel: String = "سرور",
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current

    val infiniteTransition = rememberInfiniteTransition(label = "map_radar_pulse")
    val pulseProgress by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulseRadius"
    )

    // Base fallback hubs to ensure map is always visually rich and functional
    val baseHubs = listOf(
        CountryStats("DE", "آلمان", "🇩🇪", 0, 0, 115, 0.52f, 0.32f),
        CountryStats("NL", "هلند", "🇳🇱", 0, 0, 130, 0.50f, 0.31f),
        CountryStats("US", "آمریکا", "🇺🇸", 0, 0, 210, 0.22f, 0.36f),
        CountryStats("FR", "فرانسه", "🇫🇷", 0, 0, 145, 0.49f, 0.35f),
        CountryStats("TR", "ترکیه", "🇹🇷", 0, 0, 85, 0.58f, 0.38f),
        CountryStats("FI", "فنلاند", "🇫🇮", 0, 0, 138, 0.56f, 0.24f),
        CountryStats("IR", "ایران", "🇮🇷", 0, 0, 35, 0.63f, 0.40f),
        CountryStats("GB", "انگلستان", "🇬🇧", 0, 0, 160, 0.47f, 0.30f),
        CountryStats("CA", "کانادا", "🇨🇦", 0, 0, 220, 0.24f, 0.27f),
        CountryStats("SG", "سنگاپور", "🇸🇬", 0, 0, 190, 0.78f, 0.56f)
    )

    // Merge actual data with base coordinates
    val displayCountries = if (countries.isNotEmpty()) {
        val mappedCodes = countries.map { it.countryCode }.toSet()
        val combined = countries.toMutableList()
        for (hub in baseHubs) {
            if (!mappedCodes.contains(hub.countryCode)) {
                combined.add(hub)
            }
        }
        combined
    } else {
        baseHubs
    }

    val selectedStat = displayCountries.find { it.countryCode == selectedCountry }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(390.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp)
        ) {
            // Header bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Public,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = title,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        Text(
                            text = "برای فیلتر کردن روی هر موقعیت یا کشور ضربه بزنید",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                IconButton(
                    onClick = onCloseMap,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "بستن نقشه",
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // World Map Canvas
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF0F172A))
                    .border(1.dp, Color(0xFF334155), RoundedCornerShape(14.dp))
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(displayCountries) {
                            detectTapGestures { tapOffset ->
                                val w = size.width
                                val h = size.height
                                val hitRadius = with(density) { 36.dp.toPx() }

                                for (stat in displayCountries) {
                                    val cx = stat.mapXRatio * w
                                    val cy = stat.mapYRatio * h
                                    val dist = sqrt(
                                        (tapOffset.x - cx) * (tapOffset.x - cx) +
                                                (tapOffset.y - cy) * (tapOffset.y - cy)
                                    )
                                    if (dist <= hitRadius) {
                                        onCountrySelected(
                                            if (selectedCountry == stat.countryCode) null else stat.countryCode
                                        )
                                        return@detectTapGestures
                                    }
                                }
                            }
                        }
                ) {
                    val w = size.width
                    val h = size.height

                    // 1. Ocean Background Gradient
                    drawRect(
                        brush = Brush.verticalGradient(
                            listOf(Color(0xFF0A0F1D), Color(0xFF0F172A), Color(0xFF131D33))
                        ),
                        size = size
                    )

                    // 2. Latitude & Longitude Grid
                    val gridColor = Color(0xFF38BDF8).copy(alpha = 0.12f)
                    val gridStroke = with(density) { 1.dp.toPx() }
                    for (i in 1..4) {
                        drawLine(
                            color = gridColor,
                            start = Offset(0f, h * (i / 5f)),
                            end = Offset(w, h * (i / 5f)),
                            strokeWidth = gridStroke
                        )
                    }
                    for (i in 1..5) {
                        drawLine(
                            color = gridColor,
                            start = Offset(w * (i / 6f), 0f),
                            end = Offset(w * (i / 6f), h),
                            strokeWidth = gridStroke
                        )
                    }

                    // 3. Clear Continent Shapes with High Contrast
                    val landFill = Color(0xFF1E293B)
                    val landStrokeColor = Color(0xFF38BDF8).copy(alpha = 0.45f)
                    val landStrokeWidth = with(density) { 1.5.dp.toPx() }

                    // North America
                    val na = Path().apply {
                        moveTo(w * 0.10f, h * 0.18f)
                        lineTo(w * 0.32f, h * 0.16f)
                        lineTo(w * 0.28f, h * 0.40f)
                        lineTo(w * 0.20f, h * 0.48f)
                        lineTo(w * 0.12f, h * 0.35f)
                        close()
                    }
                    drawPath(na, landFill)
                    drawPath(na, landStrokeColor, style = Stroke(landStrokeWidth))

                    // South America
                    val sa = Path().apply {
                        moveTo(w * 0.23f, h * 0.52f)
                        lineTo(w * 0.33f, h * 0.56f)
                        lineTo(w * 0.29f, h * 0.82f)
                        lineTo(w * 0.24f, h * 0.70f)
                        close()
                    }
                    drawPath(sa, landFill)
                    drawPath(sa, landStrokeColor, style = Stroke(landStrokeWidth))

                    // Europe
                    val eu = Path().apply {
                        moveTo(w * 0.43f, h * 0.20f)
                        lineTo(w * 0.58f, h * 0.18f)
                        lineTo(w * 0.56f, h * 0.38f)
                        lineTo(w * 0.45f, h * 0.36f)
                        close()
                    }
                    drawPath(eu, landFill)
                    drawPath(eu, landStrokeColor, style = Stroke(landStrokeWidth))

                    // Africa
                    val af = Path().apply {
                        moveTo(w * 0.45f, h * 0.40f)
                        lineTo(w * 0.58f, h * 0.42f)
                        lineTo(w * 0.54f, h * 0.76f)
                        lineTo(w * 0.47f, h * 0.65f)
                        close()
                    }
                    drawPath(af, landFill)
                    drawPath(af, landStrokeColor, style = Stroke(landStrokeWidth))

                    // Asia
                    val asPath = Path().apply {
                        moveTo(w * 0.58f, h * 0.18f)
                        lineTo(w * 0.88f, h * 0.20f)
                        lineTo(w * 0.84f, h * 0.55f)
                        lineTo(w * 0.65f, h * 0.52f)
                        lineTo(w * 0.58f, h * 0.36f)
                        close()
                    }
                    drawPath(asPath, landFill)
                    drawPath(asPath, landStrokeColor, style = Stroke(landStrokeWidth))

                    // Australia
                    val au = Path().apply {
                        moveTo(w * 0.76f, h * 0.66f)
                        lineTo(w * 0.88f, h * 0.68f)
                        lineTo(w * 0.84f, h * 0.84f)
                        lineTo(w * 0.74f, h * 0.80f)
                        close()
                    }
                    drawPath(au, landFill)
                    drawPath(au, landStrokeColor, style = Stroke(landStrokeWidth))

                    // 4. Country Nodes with Pulsing Glow & Pins
                    for (stat in displayCountries) {
                        val cx = stat.mapXRatio * w
                        val cy = stat.mapYRatio * h
                        val isSel = selectedCountry == stat.countryCode

                        val baseRadiusPx = with(density) {
                            if (isSel) 12.dp.toPx()
                            else if (stat.count > 0) (8.dp + (stat.count.coerceAtMost(50) / 7f).dp).toPx()
                            else 7.dp.toPx()
                        }

                        // Outer Pulsing Radar Ring
                        val pulseColor = if (isSel) Color(0xFF10B981)
                        else if (stat.healthyCount > 0) Color(0xFF06B6D4)
                        else Color(0xFF38BDF8)

                        drawCircle(
                            color = pulseColor.copy(alpha = if (isSel) 0.55f else 0.30f),
                            radius = baseRadiusPx * pulseProgress,
                            center = Offset(cx, cy),
                            style = Stroke(width = with(density) { 2.dp.toPx() })
                        )

                        // Secondary soft halo
                        drawCircle(
                            color = pulseColor.copy(alpha = 0.25f),
                            radius = baseRadiusPx * 1.3f,
                            center = Offset(cx, cy)
                        )

                        // Solid Core Circle
                        drawCircle(
                            color = if (isSel) Color(0xFF10B981)
                            else if (stat.healthyCount > 0) Color(0xFF059669)
                            else Color(0xFF0284C7),
                            radius = baseRadiusPx,
                            center = Offset(cx, cy)
                        )

                        // White Center Pin
                        drawCircle(
                            color = Color.White,
                            radius = with(density) { 3.dp.toPx() },
                            center = Offset(cx, cy)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Selected Country Details Banner
            AnimatedVisibility(visible = selectedStat != null) {
                selectedStat?.let { stat ->
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(stat.flag, fontSize = 16.sp)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "${stat.countryName} (${stat.count} $itemTypeLabel)",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                                if (stat.avgPing > 0) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "⚡ ${stat.avgPing}ms",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }

                            TextButton(
                                onClick = { onCountrySelected(null) },
                                modifier = Modifier.height(28.dp)
                            ) {
                                Text("نمایش همه", fontSize = 11.sp)
                            }
                        }
                    }
                }
            }

            // Country Filter Chips Horizontal Scroll
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                item {
                    val isAll = selectedCountry == null
                    Surface(
                        color = if (isAll) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.clickable { onCountrySelected(null) }
                    ) {
                        Text(
                            text = "همه کشورها",
                            fontSize = 11.sp,
                            fontWeight = if (isAll) FontWeight.Bold else FontWeight.Normal,
                            color = if (isAll) Color.White else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }

                items(displayCountries, key = { it.countryCode }) { stat ->
                    val isSelected = selectedCountry == stat.countryCode
                    Surface(
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(8.dp),
                        border = if (isSelected) androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
                        modifier = Modifier.clickable {
                            onCountrySelected(if (isSelected) null else stat.countryCode)
                        }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                        ) {
                            Text(stat.flag, fontSize = 13.sp)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = stat.countryName,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                            if (stat.count > 0) {
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = "${stat.count}",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
