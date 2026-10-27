package com.aiolos.cutemarmot.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FakeSettingsScreen(
    onRequestBiometricAuth: () -> Unit
) {
    var clickCount by remember { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Text(
                    text = "网络与连接",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
            item { SettingItemRow(symbol = "W", symbolColor = Color(0xFF2196F3), title = "WLAN", subtitle = "已连接到本地网络") }
            item { SettingItemRow(symbol = "B", symbolColor = Color(0xFF3F51B5), title = "蓝牙", subtitle = "已开启") }
            item { SettingItemRow(symbol = "5G", symbolColor = Color(0xFF4CAF50), title = "移动网络", subtitle = "中国移动 5G") }

            item {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Text(
                    text = "系统与设备",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
            item { SettingItemRow(symbol = "D", symbolColor = Color(0xFFFF9800), title = "屏幕与亮度", subtitle = "自适应亮度已开启") }
            item { SettingItemRow(symbol = "S", symbolColor = Color(0xFFE91E63), title = "声音与振动", subtitle = "响铃模式") }
            item { SettingItemRow(symbol = "A", symbolColor = Color(0xFF9C27B0), title = "应用管理", subtitle = "管理已安装应用") }

            item {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Text(
                    text = "关于设备",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
            item { SettingItemRow(symbol = "G", symbolColor = Color(0xFF607D8B), title = "设备名称", subtitle = "Galaxy S21 5G") }

            // 暗门入口：系统版本号，连续点击 5 次直接调起系统级指纹/手势/锁屏密码
            item {
                SettingItemRow(
                    symbol = "UI",
                    symbolColor = Color(0xFF009688),
                    title = "软件版本",
                    subtitle = "One UI 6.1 (Android 14)",
                    onClick = {
                        clickCount++
                        if (clickCount >= 5) {
                            clickCount = 0
                            onRequestBiometricAuth() // 呼起系统原生指纹/锁屏密码
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun SettingItemRow(
    symbol: String,
    symbolColor: Color,
    title: String,
    subtitle: String,
    onClick: (() -> Unit)? = null
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = onClick != null) { onClick?.invoke() },
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(vertical = 12.dp, horizontal = 8.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(symbolColor.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = symbol,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = symbolColor
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(text = title, fontWeight = FontWeight.Medium, fontSize = 16.sp)
                Text(text = subtitle, fontSize = 13.sp, color = MaterialTheme.colorScheme.outline)
            }
        }
    }
}
