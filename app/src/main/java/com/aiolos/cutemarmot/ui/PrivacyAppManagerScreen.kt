package com.aiolos.cutemarmot.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aiolos.cutemarmot.admin.AppHideManager
import com.aiolos.cutemarmot.admin.AppInfoItem
import com.aiolos.cutemarmot.stealth.IconHideManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyAppManagerScreen(
    onLockBackToNormal: () -> Unit = {}
) {
    val context = LocalContext.current
    val manager = remember { AppHideManager(context) }
    val iconHideManager = remember { IconHideManager(context) }
    val scope = rememberCoroutineScope()

    var isOwner by remember { mutableStateOf(manager.isDeviceOwner()) }
    var isSelfIconHidden by remember { mutableStateOf(iconHideManager.isIconHidden()) }
    var apps by remember { mutableStateOf<List<AppInfoItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }
    var onlyShowHidden by remember { mutableStateOf(false) }
    var currentTab by remember { mutableIntStateOf(0) }

    fun refreshApps() {
        isLoading = true
        scope.launch {
            val list = withContext(Dispatchers.IO) { manager.getInstalledApps() }
            apps = list
            isOwner = manager.isDeviceOwner()
            isLoading = false
        }
    }

    LaunchedEffect(Unit) {
        refreshApps()
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("安全空间 (已解锁)") },
                    actions = {
                        TextButton(onClick = onLockBackToNormal) {
                            Text("一键锁定", fontWeight = FontWeight.Bold)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )

                TabRow(selectedTabIndex = currentTab) {
                    Tab(
                        selected = currentTab == 0,
                        onClick = { currentTab = 0 },
                        text = { Text("📱 隐藏应用") }
                    )
                    Tab(
                        selected = currentTab == 1,
                        onClick = { currentTab = 1 },
                        text = { Text("🔒 文件与 USB 防御") }
                    )
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (currentTab == 1) {
                VaultMediaScreen()
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    // 1. Device Owner 权限状态卡片
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isOwner)
                                MaterialTheme.colorScheme.primaryContainer
                            else
                                MaterialTheme.colorScheme.errorContainer
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (isOwner) Icons.Default.CheckCircle else Icons.Default.Warning,
                                contentDescription = null,
                                tint = if (isOwner)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = if (isOwner) "Device Owner 权限：已激活" else "权限未激活 (需 ADB 授权)",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                                Text(
                                    text = if (isOwner)
                                        "具备系统最高隐藏/冻结应用特权"
                                    else
                                        "请在电脑执行: adb shell dpm set-device-owner ...",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // 2. 自身隐身控制卡片
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (isSelfIconHidden) "本应用桌面图标：已彻底隐形" else "从桌面隐藏本应用图标",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                                Text(
                                    text = if (isSelfIconHidden)
                                        "桌面完全无痕。唤醒方式：拨号盘输入 *#*#8888#*#*"
                                    else
                                        "开启后桌面上不留任何痕迹，像未安装一样",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                            Switch(
                                checked = isSelfIconHidden,
                                onCheckedChange = { shouldHide ->
                                    val ok = iconHideManager.setIconHidden(shouldHide)
                                    if (ok) {
                                        isSelfIconHidden = shouldHide
                                    }
                                }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // 3. 搜索框
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        label = { Text("搜索应用名称或包名...") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    val hiddenCount = remember(apps) { apps.count { it.isHidden } }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilterChip(
                            selected = onlyShowHidden,
                            onClick = { onlyShowHidden = !onlyShowHidden },
                            label = { Text("仅看已隐藏 ($hiddenCount)") }
                        )
                        Text(
                            text = "共 ${apps.size} 款应用",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    val filteredApps = apps.filter {
                        val matchesSearch = it.appName.contains(searchQuery, ignoreCase = true) ||
                                it.packageName.contains(searchQuery, ignoreCase = true)
                        val matchesHidden = if (onlyShowHidden) it.isHidden else true
                        matchesSearch && matchesHidden
                    }

                    if (isLoading) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(filteredApps, key = { it.packageName }) { app ->
                                AppItemRow(
                                    item = app,
                                    enabled = isOwner,
                                    onToggleHide = { shouldHide ->
                                        scope.launch {
                                            val success = withContext(Dispatchers.IO) {
                                                if (shouldHide) {
                                                    manager.hideApp(app.packageName)
                                                } else {
                                                    manager.unhideApp(app.packageName)
                                                }
                                            }
                                            if (success) {
                                                refreshApps()
                                            }
                                        }
                                    },
                                    onToggleLock = { shouldLock ->
                                        com.aiolos.cutemarmot.lock.AppLockRepository(context).setAppLocked(app.packageName, shouldLock)
                                        refreshApps()
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AppItemRow(
    item: AppInfoItem,
    enabled: Boolean,
    onToggleHide: (Boolean) -> Unit,
    onToggleLock: (Boolean) -> Unit = {}
) {
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (item.isHidden)
                MaterialTheme.colorScheme.surfaceVariant
            else
                MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val bitmap = remember(item.icon) { item.icon?.toBitmap() }
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = item.appName,
                    modifier = Modifier.size(44.dp)
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.appName,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp
                )
                Text(
                    text = item.packageName,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.outline,
                    maxLines = 1
                )
                if (item.isHidden) {
                    Text(
                        text = "● 已在桌面和系统隐藏",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Medium
                    )
                } else if (item.isLocked) {
                    Text(
                        text = "🔒 已加锁 (打开需指纹/手势)",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("隐藏", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                    Spacer(modifier = Modifier.width(4.dp))
                    Switch(
                        checked = item.isHidden,
                        onCheckedChange = { onToggleHide(it) },
                        enabled = enabled
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("加锁", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                    Spacer(modifier = Modifier.width(4.dp))
                    Switch(
                        checked = item.isLocked,
                        onCheckedChange = { onToggleLock(it) },
                        enabled = !item.isHidden
                    )
                }
            }
        }
    }
}

fun Drawable.toBitmap(): Bitmap {
    if (this is BitmapDrawable && this.bitmap != null) {
        return this.bitmap
    }
    val width = if (intrinsicWidth > 0) intrinsicWidth else 48
    val height = if (intrinsicHeight > 0) intrinsicHeight else 48
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    setBounds(0, 0, canvas.width, canvas.height)
    draw(canvas)
    return bitmap
}
