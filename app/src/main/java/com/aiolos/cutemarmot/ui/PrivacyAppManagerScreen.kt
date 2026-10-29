package com.aiolos.cutemarmot.ui

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import com.aiolos.cutemarmot.lock.AppLockRepository
import com.aiolos.cutemarmot.profile.StealthProfile
import com.aiolos.cutemarmot.profile.StealthProfileManager
import com.aiolos.cutemarmot.shizuku.ShizukuManager
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
    val profileManager = remember { StealthProfileManager(context) }
    val appLockRepo = remember { AppLockRepository(context) }
    val scope = rememberCoroutineScope()

    var isOwner by remember { mutableStateOf(manager.isDeviceOwner()) }
    val isShizukuReady by ShizukuManager.hasPermission.collectAsState()
    val isShizukuRunning by ShizukuManager.isServiceRunning.collectAsState()
    val canHide = isOwner || isShizukuReady

    var isSelfIconHidden by remember { mutableStateOf(iconHideManager.isIconHidden()) }
    var apps by remember { mutableStateOf<List<AppInfoItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }
    var onlyShowHidden by remember { mutableStateOf(false) }
    var currentTab by remember { mutableIntStateOf(0) }

    // 方案管理状态
    var profiles by remember { mutableStateOf(profileManager.getProfiles()) }
    var activeProfileId by remember { mutableStateOf(profileManager.getActiveProfileId()) }
    var selectedProfileId by remember { mutableStateOf(profileManager.getActiveProfileId()) }
    var isApplyingProfile by remember { mutableStateOf(false) }

    // 下拉抽屉状态：默认折叠，留出最大展示空间给下面的 APP 列表
    var isPanelExpanded by remember { mutableStateOf(false) }

    // 重命名弹窗状态
    var showRenameDialog by remember { mutableStateOf(false) }
    var renameTargetProfile by remember { mutableStateOf<StealthProfile?>(null) }
    var renameInputValue by remember { mutableStateOf("") }

    fun refreshApps() {
        isLoading = true
        scope.launch {
            val list = withContext(Dispatchers.IO) { manager.getInstalledApps() }
            apps = list
            isOwner = manager.isDeviceOwner()
            ShizukuManager.updateState()
            profiles = profileManager.getProfiles()
            activeProfileId = profileManager.getActiveProfileId()
            isSelfIconHidden = iconHideManager.isIconHidden()
            isLoading = false
        }
    }

    LaunchedEffect(Unit) {
        refreshApps()
    }

    // 重命名方案对话框
    if (showRenameDialog && renameTargetProfile != null) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("重命名方案") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("为该隐藏方案设置一个好记的名字（如：日常办公、家庭借用、极端隐身）：", fontSize = 13.sp)
                    OutlinedTextField(
                        value = renameInputValue,
                        onValueChange = { renameInputValue = it },
                        label = { Text("方案名称") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val ok = profileManager.renameProfile(renameTargetProfile!!.id, renameInputValue)
                        if (ok) {
                            profiles = profileManager.getProfiles()
                            Toast.makeText(context, "方案已重命名为「$renameInputValue」", Toast.LENGTH_SHORT).show()
                        }
                        showRenameDialog = false
                    },
                    enabled = renameInputValue.isNotBlank()
                ) {
                    Text("保存")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) {
                    Text("取消")
                }
            }
        )
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
                        text = { Text("📱 隐藏应用与方案") }
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
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // 1. 顶层信息栏与方案控制（支持下拉式折叠与展开，节省屏幕空间）
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .animateContentSize()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            val activeProfile = profiles.find { it.id == activeProfileId }

                            // 常驻折叠摘要条（点击任意处展开/收起）
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { isPanelExpanded = !isPanelExpanded },
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = "🛡️ 当前方案：",
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                    Text(
                                        text = activeProfile?.name ?: "默认方案",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                    ) {
                                        Text(
                                            text = "生效中",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                        )
                                    }
                                }

                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = if (isPanelExpanded) "收起设定 ▲" else "方案与设定 ▼",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                }
                            }

                            // 下拉展开的详细配置抽屉
                            AnimatedVisibility(visible = isPanelExpanded) {
                                Column(
                                    modifier = Modifier.padding(top = 10.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    // 方案选择列表
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        profiles.forEach { profile ->
                                            val isSelected = profile.id == selectedProfileId
                                            val isActive = profile.id == activeProfileId

                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = if (isSelected)
                                                    MaterialTheme.colorScheme.primaryContainer
                                                else
                                                    MaterialTheme.colorScheme.surface,
                                                border = if (isActive)
                                                    ButtonDefaults.outlinedButtonBorder(enabled = true)
                                                else null,
                                                modifier = Modifier.clickable {
                                                    selectedProfileId = profile.id
                                                }
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        text = profile.name,
                                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                        fontSize = 13.sp,
                                                        color = if (isSelected)
                                                            MaterialTheme.colorScheme.onPrimaryContainer
                                                        else
                                                            MaterialTheme.colorScheme.onSurface
                                                    )
                                                    if (isActive) {
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Text(
                                                            text = "●生效中",
                                                            fontSize = 10.sp,
                                                            color = MaterialTheme.colorScheme.primary,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    val selectedProfile = profiles.find { it.id == selectedProfileId }
                                    if (selectedProfile != null) {
                                        // 方案标签
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            AssistChip(
                                                onClick = {},
                                                label = { Text("隐藏 ${selectedProfile.hiddenPackages.size}款", fontSize = 11.sp) }
                                            )
                                            AssistChip(
                                                onClick = {},
                                                label = { Text("加锁 ${selectedProfile.lockedPackages.size}款", fontSize = 11.sp) }
                                            )
                                            AssistChip(
                                                onClick = {},
                                                label = {
                                                    Text(
                                                        if (selectedProfile.usbDefenseEnabled) "USB封锁" else "USB允许",
                                                        fontSize = 11.sp
                                                    )
                                                }
                                            )
                                            AssistChip(
                                                onClick = {},
                                                label = {
                                                    Text(
                                                        if (selectedProfile.disguiseVault) "密柜隐匿" else "密柜常规",
                                                        fontSize = 11.sp
                                                    )
                                                }
                                            )
                                        }

                                        // 方案操作按钮栏
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Button(
                                                onClick = {
                                                    scope.launch {
                                                        isApplyingProfile = true
                                                        val summary = withContext(Dispatchers.IO) {
                                                            profileManager.applyProfile(selectedProfile)
                                                        }
                                                        refreshApps()
                                                        isApplyingProfile = false
                                                        Toast.makeText(
                                                            context,
                                                            "✅ 已应用方案「${summary.profileName}」(隐藏${summary.hiddenAppsApplied}款，加锁${summary.lockedAppsApplied}款)",
                                                            Toast.LENGTH_SHORT
                                                        ).show()
                                                    }
                                                },
                                                enabled = !isApplyingProfile,
                                                modifier = Modifier.weight(1.2f),
                                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp)
                                            ) {
                                                Text(if (isApplyingProfile) "..." else "⚡ 一键应用", fontSize = 12.sp)
                                            }

                                            OutlinedButton(
                                                onClick = {
                                                    val updated = profileManager.saveCurrentStateToProfile(selectedProfile.id)
                                                    if (updated != null) {
                                                        profiles = profileManager.getProfiles()
                                                        activeProfileId = selectedProfile.id
                                                        Toast.makeText(
                                                            context,
                                                            "💾 当前配置已存入方案「${updated.name}」",
                                                            Toast.LENGTH_SHORT
                                                        ).show()
                                                    }
                                                },
                                                modifier = Modifier.weight(1.2f),
                                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp)
                                            ) {
                                                Text("💾 保存配置", fontSize = 12.sp)
                                            }

                                            OutlinedButton(
                                                onClick = {
                                                    renameTargetProfile = selectedProfile
                                                    renameInputValue = selectedProfile.name
                                                    showRenameDialog = true
                                                },
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                            ) {
                                                Text("✏️ 重命名", fontSize = 12.sp)
                                            }
                                        }
                                    }

                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))



                                    // 特权模式与 Shizuku 授权状态条
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (canHide) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                        else MaterialTheme.colorScheme.surface,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 8.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = if (canHide) Icons.Default.CheckCircle else Icons.Default.Warning,
                                                contentDescription = null,
                                                tint = if (canHide) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = when {
                                                        isOwner -> "特权模式：Device Owner 已激活 (支持隐藏与USB)"
                                                        isShizukuReady -> "特权模式：✅ Shizuku 模式就绪 (支持免Root从桌面彻底隐藏应用)"
                                                        isShizukuRunning -> "Shizuku 服务运行中，尚未授权 Cute_Marmot"
                                                        else -> "未检测到 Shizuku 服务 (可直接使用加锁保护)"
                                                    },
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    color = if (canHide) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                                )
                                                if (!canHide) {
                                                    Text(
                                                        text = if (isShizukuRunning) "点击右侧按钮进行一键授权" else "请先在手机打开 Shizuku 并启动服务",
                                                        fontSize = 10.sp,
                                                        color = MaterialTheme.colorScheme.outline
                                                    )
                                                }
                                            }

                                            if (!canHide) {
                                                Button(
                                                    onClick = {
                                                        if (isShizukuRunning) {
                                                            ShizukuManager.requestPermission()
                                                        } else {
                                                            val launchIntent = context.packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")
                                                            if (launchIntent != null) {
                                                                context.startActivity(launchIntent)
                                                            } else {
                                                                Toast.makeText(context, "未找到 Shizuku 应用", Toast.LENGTH_SHORT).show()
                                                            }
                                                        }
                                                    },
                                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                                    modifier = Modifier.height(28.dp)
                                                ) {
                                                    Text(
                                                        text = if (isShizukuRunning) "点击授权" else "打开Shizuku",
                                                        fontSize = 11.sp
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 2. 紧凑型搜索框与状态指示
                    val hiddenCount = remember(apps) { apps.count { it.isHidden } }
                    val lockedCount = remember(apps) { apps.count { it.isLocked } }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("搜索应用或包名...", fontSize = 13.sp) },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { searchQuery = "" }) {
                                        Text("✕", fontSize = 12.sp)
                                    }
                                }
                            }
                        )

                        FilterChip(
                            selected = onlyShowHidden,
                            onClick = { onlyShowHidden = !onlyShowHidden },
                            label = { Text("隐藏 ($hiddenCount)", fontSize = 11.sp) }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "已加锁 $lockedCount 款应用",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "共 ${apps.size} 款应用",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }

                    // 3. 应用列表展示栏（拥有最大化的屏幕纵向空间）
                    val filteredApps = apps.filter {
                        val matchesSearch = it.appName.contains(searchQuery, ignoreCase = true) ||
                                it.packageName.contains(searchQuery, ignoreCase = true)
                        val matchesHidden = if (onlyShowHidden) it.isHidden else true
                        matchesSearch && matchesHidden
                    }

                    if (isLoading) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(filteredApps, key = { it.packageName }) { app ->
                                AppItemRow(
                                    item = app,
                                    enabled = canHide,
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
                                            } else {
                                                withContext(Dispatchers.Main) {
                                                    val msg = if (!manager.isShizukuReady() && !manager.isDeviceOwner()) {
                                                        "操作失败：请确认 Shizuku 正在运行且已授权"
                                                    } else {
                                                        "操作失败：该应用可能处于系统休眠保护中，请重试"
                                                    }
                                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        }
                                    },
                                    onToggleLock = { shouldLock ->
                                        if (shouldLock) {
                                            scope.launch {
                                                val isServiceOn = withContext(Dispatchers.IO) {
                                                    var enabled = com.aiolos.cutemarmot.lock.AppLockAccessibilityService.isAccessibilityServiceEnabled(context)
                                                    if (!enabled && ShizukuManager.isPermissionGranted()) {
                                                        ShizukuManager.enableAccessibilityServiceSync(context.packageName)
                                                        enabled = com.aiolos.cutemarmot.lock.AppLockAccessibilityService.isAccessibilityServiceEnabled(context)
                                                    }
                                                    enabled
                                                }
                                                if (!isServiceOn) {
                                                    withContext(Dispatchers.Main) {
                                                        Toast.makeText(
                                                            context,
                                                            "⚠️ 加锁需开启无障碍权限，请在随后页面开启「Cute Marmot / 系统高级设置」",
                                                            Toast.LENGTH_LONG
                                                        ).show()
                                                        val intent = Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                        }
                                                        context.startActivity(intent)
                                                    }
                                                }
                                            }
                                        }
                                        appLockRepo.setAppLocked(app.packageName, shouldLock)
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
                val context = LocalContext.current
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = if (!enabled) {
                        Modifier.clickable {
                            Toast.makeText(
                                context,
                                "⚠️ 隐藏应用需授权 Shizuku；请在上方卡片点击「点击授权」，或直接开启下方「加锁」指纹保护！",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    } else Modifier
                ) {
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
                        enabled = true
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
