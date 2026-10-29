package com.aiolos.cutemarmot.vault

import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import java.io.*
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

import com.aiolos.cutemarmot.shizuku.ShizukuManager

data class VaultFileItem(
    val id: String,
    val originalName: String,
    val originalPath: String? = null,
    val mimeType: String,
    val sizeBytes: Long,
    val encryptedFile: File
)

class FileVaultEngine(private val context: Context) {

    private val vaultDir: File = File(context.filesDir, "stealth_vault").apply {
        if (!exists()) mkdirs()
    }

    private val metaPrefs = context.getSharedPreferences("vault_meta_prefs", Context.MODE_PRIVATE)

    // 默认内置的 AES-256 派生安全密钥 (32 字节)
    private val masterKeyBytes = byteArrayOf(
        0x53, 0x74, 0x65, 0x61, 0x6C, 0x74, 0x68, 0x56,
        0x61, 0x75, 0x6C, 0x74, 0x4B, 0x65, 0x79, 0x32,
        0x30, 0x32, 0x36, 0x41, 0x69, 0x6F, 0x6C, 0x6F,
        0x73, 0x4C, 0x75, 0x6B, 0x30, 0x30, 0x35, 0x21
    )
    private val secretKey = SecretKeySpec(masterKeyBytes, "AES")

    /**
     * 解析 Uri 对应的真实物理文件路径（支持内部存储与外置 SD 卡）
     */
    private fun resolveRealPath(uri: Uri): String? {
        if (uri.scheme.equals("file", ignoreCase = true)) {
            return uri.path
        }
        // 1. 直接查询 MediaStore DATA 字段
        try {
            val proj = arrayOf(android.provider.MediaStore.MediaColumns.DATA)
            context.contentResolver.query(uri, proj, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(android.provider.MediaStore.MediaColumns.DATA)
                    if (idx != -1) {
                        val path = cursor.getString(idx)
                        if (!path.isNullOrBlank()) return path
                    }
                }
            }
        } catch (_: Exception) {}

        // 2. 解析 DocumentUri
        if (android.provider.DocumentsContract.isDocumentUri(context, uri)) {
            val docId = android.provider.DocumentsContract.getDocumentId(uri)
            if (uri.authority == "com.android.externalstorage.documents") {
                val split = docId.split(":")
                val type = split[0]
                val relativePath = if (split.size > 1) split[1] else ""
                return if ("primary".equals(type, ignoreCase = true)) {
                    "/storage/emulated/0/$relativePath"
                } else {
                    "/storage/$type/$relativePath"
                }
            } else if (uri.authority == "com.android.providers.media.documents") {
                val split = docId.split(":")
                val type = split[0]
                val id = split.getOrNull(1) ?: return null
                val contentUri = when (type) {
                    "image" -> android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                    "video" -> android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                    "audio" -> android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                    else -> android.provider.MediaStore.Files.getContentUri("external")
                }
                val selection = "_id=?"
                val selectionArgs = arrayOf(id)
                context.contentResolver.query(
                    contentUri,
                    arrayOf(android.provider.MediaStore.MediaColumns.DATA),
                    selection,
                    selectionArgs,
                    null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idx = cursor.getColumnIndex(android.provider.MediaStore.MediaColumns.DATA)
                        if (idx != -1) return cursor.getString(idx)
                    }
                }
            }
        }
        return null
    }

    /**
     * 将用户选中的 Uri (图片/视频/音频/文件) 加密导入安全密柜，并记录原物理路径
     * @param sourceUri 用户选择的文件 Uri
     */
    fun encryptAndImport(sourceUri: Uri): Boolean {
        return try {
            val resolver = context.contentResolver
            var fileName = "vault_file_${System.currentTimeMillis()}"
            var fileSize = 0L

            resolver.query(sourceUri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (cursor.moveToFirst()) {
                    if (nameIndex != -1) fileName = cursor.getString(nameIndex)
                    if (sizeIndex != -1) fileSize = cursor.getLong(sizeIndex)
                }
            }

            val originalPath = resolveRealPath(sourceUri)
            val targetEncFile = File(vaultDir, "${System.currentTimeMillis()}_${fileName}.enc")

            // 生成随机 12 字节 IV 用于 AES-GCM
            val iv = ByteArray(12)
            SecureRandom().nextBytes(iv)

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val spec = GCMParameterSpec(128, iv)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, spec)

            resolver.openInputStream(sourceUri)?.use { inputStream ->
                FileOutputStream(targetEncFile).use { fileOut ->
                    // 头部写入 IV
                    fileOut.write(iv)
                    CipherOutputStream(fileOut, cipher).use { cipherOut ->
                        inputStream.copyTo(cipherOut)
                    }
                }
            }

            // 永久记录该文件原来的物理绝对路径
            if (!originalPath.isNullOrBlank()) {
                metaPrefs.edit().putString("${targetEncFile.name}_orig_path", originalPath).apply()
                Log.i(TAG, "已记录文件原位置: $originalPath")
            }

            // 自动移除外部原文件（彻底隐藏）：优先系统删除，受限则通过特权清理原处残留
            var sourceDeleted = false
            try {
                if (resolver.delete(sourceUri, null, null) > 0) {
                    sourceDeleted = true
                }
            } catch (_: Exception) {}

            if (!sourceDeleted && !originalPath.isNullOrBlank()) {
                try {
                    val srcFile = File(originalPath)
                    if (srcFile.exists() && srcFile.delete()) {
                        sourceDeleted = true
                    }
                } catch (_: Exception) {}
                if (!sourceDeleted && ShizukuManager.isPermissionGranted()) {
                    ShizukuManager.execSync(arrayOf("rm", "-f", originalPath))
                }
                MediaScannerConnection.scanFile(context, arrayOf(originalPath), null, null)
            }

            Log.i(TAG, "成功加密文件: $fileName -> ${targetEncFile.name}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "加密文件失败: ${e.message}", e)
            false
        }
    }

    /**
     * 列出当前安全密柜中所有的加密文件，并附带原路径信息
     */
    fun listVaultFiles(): List<VaultFileItem> {
        val files = vaultDir.listFiles { file -> file.name.endsWith(".enc") } ?: return emptyList()
        return files.map { file ->
            val rawName = file.name.removeSuffix(".enc")
            // 去除时间戳前缀
            val displayName = rawName.substringAfter("_")
            val mimeType = when {
                displayName.endsWith(".jpg", true) || displayName.endsWith(".jpeg", true) || displayName.endsWith(".png", true) -> "image/*"
                displayName.endsWith(".mp4", true) || displayName.endsWith(".mkv", true) -> "video/*"
                displayName.endsWith(".mp3", true) || displayName.endsWith(".m4a", true) -> "audio/*"
                else -> "application/octet-stream"
            }
            val originalPath = metaPrefs.getString("${file.name}_orig_path", null)
            VaultFileItem(
                id = file.name,
                originalName = displayName,
                originalPath = originalPath,
                mimeType = mimeType,
                sizeBytes = file.length(),
                encryptedFile = file
            )
        }.sortedByDescending { it.encryptedFile.lastModified() }
    }

    /**
     * 将密柜中的文件解密恢复**直接放回原位置**（支持外置 SD 卡或私密文件夹，智能防重名）
     */
    fun decryptAndExport(item: VaultFileItem): Boolean {
        return try {
            val origPath = item.originalPath
            val targetDir: File = if (!origPath.isNullOrBlank()) {
                val origFile = File(origPath)
                origFile.parentFile ?: android.os.Environment.getExternalStoragePublicDirectory(
                    android.os.Environment.DIRECTORY_DOWNLOADS
                )
            } else {
                android.os.Environment.getExternalStoragePublicDirectory(
                    android.os.Environment.DIRECTORY_DOWNLOADS
                )
            }

            if (!targetDir.exists()) {
                targetDir.mkdirs()
            }

            var outputFile = File(targetDir, item.originalName)
            // 防覆盖保护：若原位置已有同名文件，自动添加序号
            if (outputFile.exists()) {
                val dotIndex = item.originalName.lastIndexOf('.')
                val base = if (dotIndex != -1) item.originalName.substring(0, dotIndex) else item.originalName
                val ext = if (dotIndex != -1) item.originalName.substring(dotIndex) else ""
                var counter = 1
                while (outputFile.exists()) {
                    outputFile = File(targetDir, "${base}_已恢复($counter)$ext")
                    counter++
                }
            }

            // 先解密到应用临时私有缓存
            val tempFile = File(context.cacheDir, "temp_restore_${System.currentTimeMillis()}")
            FileInputStream(item.encryptedFile).use { fileIn ->
                val iv = ByteArray(12)
                fileIn.read(iv)

                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                val spec = GCMParameterSpec(128, iv)
                cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)

                CipherInputStream(fileIn, cipher).use { cipherIn ->
                    FileOutputStream(tempFile).use { fileOut ->
                        cipherIn.copyTo(fileOut)
                    }
                }
            }

            // 放回目标原位置（支持外置 SD 卡，若普通 I/O 受限则通过特权完成拷贝）
            var restored = false
            try {
                FileOutputStream(outputFile).use { fileOut ->
                    FileInputStream(tempFile).use { it.copyTo(fileOut) }
                }
                restored = true
            } catch (e: Exception) {
                Log.w(TAG, "常规写入原位置受限，启用 Shizuku 特权还原: ${e.message}")
            }

            if (!restored && ShizukuManager.isPermissionGranted()) {
                val cmd = ShizukuManager.execSync(arrayOf("cp", tempFile.absolutePath, outputFile.absolutePath))
                if (cmd.isSuccess) {
                    restored = true
                }
            }
            tempFile.delete()

            if (restored) {
                // 物理删除加密密柜里的密文并清理原路径记录
                item.encryptedFile.delete()
                metaPrefs.edit().remove("${item.id}_orig_path").apply()

                // 触发系统媒体库扫描刷新，确保原位置相册和播放器立刻显示
                MediaScannerConnection.scanFile(
                    context,
                    arrayOf(outputFile.absolutePath),
                    null
                ) { path, uri ->
                    Log.i(TAG, "文件已成功放回原位置并登记媒体库: $path -> $uri")
                }
                true
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "解密导出失败: ${e.message}", e)
            false
        }
    }

    /**
     * 永久粉碎删除密柜里的某个加密文件
     */
    fun shredFile(item: VaultFileItem): Boolean {
        return try {
            metaPrefs.edit().remove("${item.id}_orig_path").apply()
            item.encryptedFile.delete()
        } catch (e: Exception) {
            false
        }
    }

    companion object {
        private const val TAG = "FileVaultEngine"
    }
}
