package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.model.ProxyProtocol
import com.example.core.model.TestStatus

@Composable
fun RtlProvider(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        content()
    }
}

@Composable
fun StatusBadge(status: TestStatus, modifier: Modifier = Modifier) {
    val (bgColor, textColor) = when (status) {
        TestStatus.GLOBAL_ACCESS_CONFIRMED, TestStatus.TUNNEL_CONNECTED ->
            Color(0xFF059669).copy(alpha = 0.15f) to Color(0xFF10B981)
        TestStatus.REACHABLE_ENDPOINT ->
            Color(0xFF0891B2).copy(alpha = 0.15f) to Color(0xFF06B6D4)
        TestStatus.STRUCTURALLY_VALID ->
            Color(0xFF6366F1).copy(alpha = 0.15f) to Color(0xFF818CF8)
        TestStatus.FAILED ->
            Color(0xFFE11D48).copy(alpha = 0.15f) to Color(0xFFF43F5E)
        TestStatus.UNTESTED ->
            Color(0xFF64748B).copy(alpha = 0.15f) to Color(0xFF94A3B8)
        TestStatus.INDETERMINATE ->
            Color(0xFFD97706).copy(alpha = 0.15f) to Color(0xFFFBBF24)
    }

    Surface(
        color = bgColor,
        shape = RoundedCornerShape(8.dp),
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(textColor, CircleShape)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = status.persianTitle,
                color = textColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun ProtocolBadge(protocol: ProxyProtocol, modifier: Modifier = Modifier) {
    val (bgColor, textColor) = when (protocol) {
        ProxyProtocol.VLESS -> Color(0xFF10B981).copy(alpha = 0.15f) to Color(0xFF10B981)
        ProxyProtocol.VMESS -> Color(0xFF3B82F6).copy(alpha = 0.15f) to Color(0xFF60A5FA)
        ProxyProtocol.TROJAN -> Color(0xFF8B5CF6).copy(alpha = 0.15f) to Color(0xFFA78BFA)
        ProxyProtocol.SHADOWSOCKS -> Color(0xFFF59E0B).copy(alpha = 0.15f) to Color(0xFFFBBF24)
        ProxyProtocol.SOCKS5 -> Color(0xFFEC4899).copy(alpha = 0.15f) to Color(0xFFF472B6)
        ProxyProtocol.HTTP -> Color(0xFF64748B).copy(alpha = 0.15f) to Color(0xFF94A3B8)
    }

    Surface(
        color = bgColor,
        shape = RoundedCornerShape(6.dp),
        modifier = modifier
    ) {
        Text(
            text = protocol.displayName,
            color = textColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
fun PingBadge(pingMs: Long, modifier: Modifier = Modifier) {
    if (pingMs == -1L) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(Color(0xFFEF4444), CircleShape)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "-1 ms (نامعتبر)",
                color = Color(0xFFEF4444),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
        return
    }

    if (pingMs <= 0) {
        Text(
            text = "تست نشده",
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            fontSize = 11.sp,
            modifier = modifier
        )
        return
    }

    val color = when {
        pingMs <= 250 -> Color(0xFF10B981) // Green
        pingMs <= 500 -> Color(0xFFF59E0B) // Amber
        else -> Color(0xFFFF9800) // Orange
    }

    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .background(color, CircleShape)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = "$pingMs ms",
            color = color,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
