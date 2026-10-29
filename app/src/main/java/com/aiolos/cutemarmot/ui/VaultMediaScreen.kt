package com.aiolos.cutemarmot.ui

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import java.io.File
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aiolos.cutemarmot.admin.UsbSecurityManager
import com.aiolos.cutemarmot.profile.StealthProfileManager
import com.aiolos.cutemarmot.vault.FileVaultEngine
import com.aiolos.cutemarmot.vault.VaultFileItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun VaultMediaScreen() {
    val context = LocalContext.current
    val usbManager = remember { UsbSecurityManager(context) }
    val vaultEngine = remember { FileVaultEngine(context) }
    val profileManager = remember { StealthProfileManager(context) }
    val scope = rememberCoroutineScope()

    var isUsbBlocked by remember { mutableStateOf(usbManager.isUsbFileTransferDisabled()) }
    var isVaultDisguised by remember { mutableStateOf(profileManager.isVaultDisguised()) }
    var tempBypassDisguise by remember { mutableStateOf(false) }
    var vaultFiles by remember { mutableStateOf<List<VaultFileItem>>(emptyList()) }
    var isProcessing by remember { mutableStateOf(false) }

    fun refreshFiles() {
        scope.launch {
            val list = withContext(Dispatchers.IO) { vaultEngine.listVaultFiles() }
            vaultFiles = list
            isUsbBlocked = usbManager.isUsbFileTransferDisabled()
            isVaultDisguised = profileManager.isVaultDisguised()
        }
    }

    LaunchedEffect(Unit) {
        refreshFiles()
    }

    // 文件选择器
    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { pickedUri ->
            isProcessing = true
            scope.launch {
                val ok = withContext(Dispatchers.IO) {
                    vaultEngine.encryptAndImport(pickedUri)
                }
                isProcessing = false
                if (ok) {
                    Toast.makeText(context, "文件已安全加密存入隔离密柜", Toast.LENGTH_SHORT).show()
                }
                refreshFiles()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. USB 硬件连线防御开关
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isUsbBlocked)
                    MaterialTheme.colorScheme.errorContainer
                else
                    MaterialTheme.colorScheme.surfaceVariant
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val hasOwner = usbManager.isDeviceOwner()
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (isUsbBlocked) "USB 数据连线：已物理封锁" else "封锁 USB 数据线文件传输 (MTP)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = if (isUsbBlocked)
                                MaterialTheme.colorScheme.error
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = if (hasOwner) {
                            if (isUsbBlocked) "已封锁：电脑插线不弹出手机盘符，防数据导出" else "支持 Device Owner 特权管控，开启后插电脑仅充电"
                        } else {
                            "专为 Device Owner 模式设计的硬件级防御（当前为 Shizuku 模式暂不支持）"
                        },
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
                Switch(
                    checked = isUsbBlocked,
                    enabled = hasOwner,
                    onCheckedChange = { block ->
                        val success = usbManager.setUsbFileTransferDisabled(block)
                        if (success) {
                            isUsbBlocked = block
                            Toast.makeText(
                                context,
                                if (block) "USB 传输已阻断（防电脑读取）" else "USB 传输已恢复",
                                Toast.LENGTH_SHORT
                            ).show()
                        } else {
                            Toast.makeText(
                                context,
                                "⚠️ 硬件级 USB 传输封锁需具备 Device Owner 管理员权限",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                )
            }
        }

        // 2. 密柜隐身伪装开关
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isVaultDisguised)
                    MaterialTheme.colorScheme.tertiaryContainer
                else
                    MaterialTheme.colorScheme.surfaceVariant
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isVaultDisguised) "密柜隐身伪装：已激活" else "密柜隐身与防翻查伪装",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = if (isVaultDisguised)
                            MaterialTheme.colorScheme.onTertiaryContainer
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = if (isVaultDisguised)
                            "对外隐藏密柜真实文件，避免外人翻看；方案切换时可联动自动激活"
                        else
                            "开启后对密藏文件做隐匿伪装保护",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
                Switch(
                    checked = isVaultDisguised,
                    onCheckedChange = { disguised ->
                        profileManager.setVaultDisguised(disguised)
                        isVaultDisguised = disguised
                        tempBypassDisguise = false
                    }
                )
            }
        }

        // 3. 加密导入操作栏与密柜展示
        if (isVaultDisguised && !tempBypassDisguise) {
            // 伪装状态展示
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "🛡️ 密柜处于隐身防护模式",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "当前防御方案已开启密柜隐匿伪装，外人无法查看库内明细",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.outline
                        )
                        Button(
                            onClick = { tempBypassDisguise = true }
                        ) {
                            Text("临时揭开隐身查看")
                        }
                    }
                }
            }
        } else {
            // 正常密柜操作与文件列表
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "AES-256 隐秘文件密柜",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Text(
                            text = "已加密保管 ${vaultFiles.size} 份私密文件",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }

                    Button(
                        onClick = { filePicker.launch(arrayOf("*/*")) },
                        enabled = !isProcessing
                    ) {
                        Text(if (isProcessing) "加密中..." else "+ 导入文件")
                    }
                }
            }

            Text(
                text = "加密保管库（电脑连线无法提取，沙盒硬件密钥加密）",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )

            if (vaultFiles.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "暂无加密文件\n点击右上角“+ 导入文件”添加私密照片/视频",
                        color = MaterialTheme.colorScheme.outline,
                        fontSize = 14.sp
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(vaultFiles, key = { it.id }) { item ->
                        VaultItemRow(
                            item = item,
                            onRestore = {
                                scope.launch {
                                    val ok = withContext(Dispatchers.IO) {
                                        vaultEngine.decryptAndExport(item)
                                    }
                                    if (ok) {
                                        val targetDesc = if (!item.originalPath.isNullOrBlank()) {
                                            "已成功原路放回：${File(item.originalPath).parentFile?.name ?: "原位置"}"
                                        } else {
                                            "已安全放回系统 Download 目录"
                                        }
                                        Toast.makeText(context, targetDesc, Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "还原失败，请重试", Toast.LENGTH_SHORT).show()
                                    }
                                    refreshFiles()
                                }
                            },
                            onDelete = {
                                vaultEngine.shredFile(item)
                                Toast.makeText(context, "已彻底安全覆写粉碎", Toast.LENGTH_SHORT).show()
                                refreshFiles()
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun VaultItemRow(
    item: VaultFileItem,
    onRestore: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
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
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF673AB7).copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "ENC",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF673AB7)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.originalName,
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp,
                    maxLines = 1
                )
                if (!item.originalPath.isNullOrBlank()) {
                    Text(
                        text = "原位置: ${item.originalPath}",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1
                    )
                }
                Text(
                    text = "AES-256 密文 (${item.sizeBytes / 1024} KB)",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onRestore) {
                    Text("恢复")
                }
                TextButton(onClick = onDelete) {
                    Text("粉碎", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}
