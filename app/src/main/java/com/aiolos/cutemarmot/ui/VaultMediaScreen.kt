package com.aiolos.cutemarmot.ui

import android.net.Uri
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aiolos.cutemarmot.admin.UsbSecurityManager
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
    val scope = rememberCoroutineScope()

    var isUsbBlocked by remember { mutableStateOf(usbManager.isUsbFileTransferDisabled()) }
    var vaultFiles by remember { mutableStateOf<List<VaultFileItem>>(emptyList()) }
    var isProcessing by remember { mutableStateOf(false) }

    fun refreshFiles() {
        scope.launch {
            val list = withContext(Dispatchers.IO) { vaultEngine.listVaultFiles() }
            vaultFiles = list
            isUsbBlocked = usbManager.isUsbFileTransferDisabled()
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
                withContext(Dispatchers.IO) {
                    vaultEngine.encryptAndImport(pickedUri)
                }
                isProcessing = false
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
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isUsbBlocked) "USB 数据连线：已物理封锁" else "封锁 USB 数据线文件传输 (MTP)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = if (isUsbBlocked)
                            MaterialTheme.colorScheme.error
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = if (isUsbBlocked)
                            "电脑插线无法显示手机盘符，无法读取任何文件，仅支持纯充电"
                        else
                            "开启后通过系统策略阻止电脑访问手机内置存储与媒体",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
                Switch(
                    checked = isUsbBlocked,
                    onCheckedChange = { block ->
                        val success = usbManager.setUsbFileTransferDisabled(block)
                        if (success) {
                            isUsbBlocked = block
                        }
                    }
                )
            }
        }

        // 2. 加密导入操作栏
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
            text = "加密保管库（连线电脑无法扫描，完全隔绝）",
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
                                withContext(Dispatchers.IO) {
                                    vaultEngine.decryptAndExport(item)
                                }
                                refreshFiles()
                            }
                        },
                        onDelete = {
                            vaultEngine.shredFile(item)
                            refreshFiles()
                        }
                    )
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
