package com.aiolos.cutemarmot.ui

import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.Settings
import android.telephony.TelephonyManager
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FakeSettingsScreen(
    onRequestBiometricAuth: () -> Unit
) {
    val context = LocalContext.current

    // 动态读取真实手机环境数据
    val deviceName = remember { getRealDeviceName(context) }
    val wifiName = remember { getRealWifiSsid(context) }
    val mobileOperator = remember { getRealMobileOperator(context) }
    val softwareVersion = remember { getRealSoftwareVersion() }
    val bluetoothStatus = remember { getRealBluetoothStatus() }

    // 双重隐蔽暗门状态机：
    // 步骤一：必须连续点击“软件版本”5次
    // 步骤二：必须在步骤一达成后的 10 秒内，接着连续点击“应用管理”3次
    var step1ClickCount by remember { mutableIntStateOf(0) }
    var step1PassedTime by remember { mutableLongStateOf(0L) }
    var step2ClickCount by remember { mutableIntStateOf(0) }

    fun onSoftwareVersionClicked() {
        val now = System.currentTimeMillis()
        step1ClickCount++
        if (step1ClickCount >= 5) {
            step1ClickCount = 0
            step1PassedTime = now  // 第一阶段已达成，开启10秒验证窗口
            step2ClickCount = 0   // 重置第二阶段计数
        }
    }

    fun onAppManagerClicked() {
        val now = System.currentTimeMillis()
        // 检查第一阶段是否在过去 10 秒内成功解锁
        if (step1PassedTime > 0L && (now - step1PassedTime <= 10_000L)) {
            step2ClickCount++
            if (step2ClickCount >= 3) {
                // 双重暗门全部通过！瞬间清空计数并唤起真机指纹/密码验证
                step1ClickCount = 0
                step1PassedTime = 0L
                step2ClickCount = 0
                onRequestBiometricAuth()
            }
        } else {
            // 第一阶段未达成或已超时失效，当作普通点击
            step2ClickCount = 0
        }
    }

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
            // 网络与连接组
            item {
                Text(
                    text = "网络与连接",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
            item {
                SettingItemRow(
                    symbol = "W",
                    symbolColor = Color(0xFF2196F3),
                    title = "WLAN",
                    subtitle = if (wifiName.isNotBlank()) wifiName else "未连接"
                )
            }
            item {
                SettingItemRow(
                    symbol = "B",
                    symbolColor = Color(0xFF3F51B5),
                    title = "蓝牙",
                    subtitle = bluetoothStatus
                )
            }
            item {
                SettingItemRow(
                    symbol = "5G",
                    symbolColor = Color(0xFF4CAF50),
                    title = "移动网络",
                    subtitle = mobileOperator
                )
            }

            // 系统与设备组
            item {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Text(
                    text = "系统与设备",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
            item {
                SettingItemRow(
                    symbol = "D",
                    symbolColor = Color(0xFFFF9800),
                    title = "屏幕与亮度",
                    subtitle = "自适应亮度已开启"
                )
            }
            item {
                SettingItemRow(
                    symbol = "S",
                    symbolColor = Color(0xFFE91E63),
                    title = "声音与振动",
                    subtitle = "响铃模式"
                )
            }

            // 暗门第二触发点：应用管理（在点击软件版本5次后，接着点此处3次）
            item {
                SettingItemRow(
                    symbol = "A",
                    symbolColor = Color(0xFF9C27B0),
                    title = "应用管理",
                    subtitle = "管理已安装应用与默认应用",
                    onClick = { onAppManagerClicked() }
                )
            }

            // 关于设备组
            item {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Text(
                    text = "关于设备",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
            item {
                SettingItemRow(
                    symbol = "G",
                    symbolColor = Color(0xFF607D8B),
                    title = "设备名称",
                    subtitle = deviceName
                )
            }

            // 暗门第一触发点：软件版本（需先连击 5 次）
            item {
                SettingItemRow(
                    symbol = "UI",
                    symbolColor = Color(0xFF009688),
                    title = "软件版本",
                    subtitle = softwareVersion,
                    onClick = { onSoftwareVersionClicked() }
                )
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
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

// ----------------- 真实系统环境动态读取辅助方法 -----------------

/**
 * 动态读取真实设备名称（如 Aiolos' Galaxy Note20 Ultra 5G 或 SM-N9860）
 */
private fun getRealDeviceName(context: Context): String {
    var name = try {
        Settings.Global.getString(context.contentResolver, Settings.Global.DEVICE_NAME)
    } catch (e: Exception) { null }

    if (name.isNullOrBlank()) {
        name = try {
            Settings.System.getString(context.contentResolver, "device_name")
        } catch (e: Exception) { null }
    }

    if (name.isNullOrBlank()) {
        val brand = Build.BRAND.replaceFirstChar { it.uppercase() }
        val model = Build.MODEL
        name = if (model.startsWith(brand, ignoreCase = true)) model else "$brand $model"
    }
    return name
}

/**
 * 动态读取真实移动运营商名称（如 中国电信、中国移动、中国联通 等）
 */
private fun getRealMobileOperator(context: Context): String {
    return try {
        val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        val simName = tm?.simOperatorName
        val networkName = tm?.networkOperatorName
        when {
            !simName.isNullOrBlank() -> simName
            !networkName.isNullOrBlank() -> networkName
            else -> "已连接移动网络"
        }
    } catch (e: Exception) {
        "已连接移动网络"
    }
}

/**
 * 动态读取真实连接的 Wi-Fi 名字（SSID）
 */
private fun getRealWifiSsid(context: Context): String {
    return try {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val info = wm?.connectionInfo
        val rawSsid = info?.ssid?.replace("\"", "")?.trim() ?: ""

        if (rawSsid.isNotEmpty() && rawSsid != "<unknown ssid>" && rawSsid != "0x") {
            rawSsid
        } else {
            val netInfo = cm?.activeNetworkInfo
            if (netInfo != null && netInfo.isConnected && netInfo.type == ConnectivityManager.TYPE_WIFI) {
                "已连接到 WLAN"
            } else if (netInfo != null && netInfo.isConnected) {
                "已通过蜂窝网络连接"
            } else {
                "已开启"
            }
        }
    } catch (e: Exception) {
        "已连接"
    }
}

/**
 * 动态读取真实软件版本（如 One UI 5.1 (Android 13)）
 */
private fun getRealSoftwareVersion(): String {
    val androidVer = Build.VERSION.RELEASE
    val oneUiProp = try {
        val clazz = Class.forName("android.os.SystemProperties")
        val getMethod = clazz.getMethod("get", String::class.java)
        getMethod.invoke(null, "ro.build.version.oneui") as? String
    } catch (e: Exception) { null }

    val oneUiName = when {
        oneUiProp == "50100" -> "One UI 5.1"
        oneUiProp == "50000" -> "One UI 5.0"
        oneUiProp == "60100" -> "One UI 6.1"
        oneUiProp == "60000" -> "One UI 6.0"
        !oneUiProp.isNullOrBlank() -> {
            val major = oneUiProp.take(1)
            val minor = oneUiProp.drop(1).take(1)
            "One UI $major.$minor"
        }
        Build.MANUFACTURER.contains("samsung", ignoreCase = true) -> "One UI"
        else -> ""
    }

    return if (oneUiName.isNotBlank()) {
        "$oneUiName (Android $androidVer)"
    } else {
        "Android $androidVer"
    }
}

/**
 * 动态读取蓝牙状态
 */
private fun getRealBluetoothStatus(): String {
    return try {
        val adapter = BluetoothAdapter.getDefaultAdapter()
        if (adapter?.isEnabled == true) "已开启" else "已关闭"
    } catch (e: Exception) {
        "已开启"
    }
}
