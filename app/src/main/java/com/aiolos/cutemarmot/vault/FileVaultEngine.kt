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

data class VaultFileItem(
    val id: String,
    val originalName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val encryptedFile: File
)

class FileVaultEngine(private val context: Context) {

    private val vaultDir: File = File(context.filesDir, "stealth_vault").apply {
        if (!exists()) mkdirs()
    }

    // 默认内置的 AES-256 派生安全密钥 (32 字节)
    private val masterKeyBytes = byteArrayOf(
        0x53, 0x74, 0x65, 0x61, 0x6C, 0x74, 0x68, 0x56,
        0x61, 0x75, 0x6C, 0x74, 0x4B, 0x65, 0x79, 0x32,
        0x30, 0x32, 0x36, 0x41, 0x69, 0x6F, 0x6C, 0x6F,
        0x73, 0x4C, 0x75, 0x6B, 0x30, 0x30, 0x35, 0x21
    )
    private val secretKey = SecretKeySpec(masterKeyBytes, "AES")

    /**
     * 将用户选中的 Uri (图片/视频/音频/文件) 加密导入安全密柜
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

            val mimeType = resolver.getType(sourceUri) ?: "application/octet-stream"
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

            // 尝试通过 ContentResolver 移除原文件（避免留在公有目录）
            try {
                resolver.delete(sourceUri, null, null)
            } catch (e: Exception) {
                Log.w(TAG, "无法直接删除源 Uri，需由外部媒体管理器清理: ${e.message}")
            }

            Log.i(TAG, "成功加密文件: $fileName -> ${targetEncFile.name}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "加密文件失败: ${e.message}", e)
            false
        }
    }

    /**
     * 列出当前安全密柜中所有的加密文件
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
            VaultFileItem(
                id = file.name,
                originalName = displayName,
                mimeType = mimeType,
                sizeBytes = file.length(),
                encryptedFile = file
            )
        }.sortedByDescending { it.encryptedFile.lastModified() }
    }

    /**
     * 将密柜中的文件解密导出恢复回公共 Download 目录，并通知系统媒体库刷新
     */
    fun decryptAndExport(item: VaultFileItem): Boolean {
        return try {
            val downloadDir = context.getExternalFilesDir(null) ?: context.filesDir
            val outputFile = File(downloadDir, item.originalName)

            FileInputStream(item.encryptedFile).use { fileIn ->
                val iv = ByteArray(12)
                fileIn.read(iv)

                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                val spec = GCMParameterSpec(128, iv)
                cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)

                CipherInputStream(fileIn, cipher).use { cipherIn ->
                    FileOutputStream(outputFile).use { fileOut ->
                        cipherIn.copyTo(fileOut)
                    }
                }
            }

            // 物理删除加密密柜里的密文
            item.encryptedFile.delete()

            // 触发系统媒体库扫描刷新
            MediaScannerConnection.scanFile(
                context,
                arrayOf(outputFile.absolutePath),
                null
            ) { path, uri ->
                Log.i(TAG, "文件已解密恢复并登记至系统媒体库: $path -> $uri")
            }
            true
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
            item.encryptedFile.delete()
        } catch (e: Exception) {
            false
        }
    }

    companion object {
        private const val TAG = "FileVaultEngine"
    }
}
