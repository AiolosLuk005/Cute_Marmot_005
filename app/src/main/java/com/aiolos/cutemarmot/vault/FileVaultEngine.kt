package com.aiolos.cutemarmot.vault

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.system.Os
import android.util.Log
import java.io.*
import java.security.KeyStore
import java.security.MessageDigest
import java.util.UUID
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

data class VaultFileItem(
    val id: String,
    val originalName: String,
    val originalPath: String? = null, // Historical display information only.
    val mimeType: String,
    val sizeBytes: Long,
    val encryptedFile: File
)

enum class ImportResult { FAILED, SOURCE_REMOVED, SOURCE_RETAINED }
enum class ExportResult { FAILED, EXPORTED, EXPORTED_LEGACY_RETAINED }

class FileVaultEngine internal constructor(
    private val context: Context,
    private val keyFactory: (() -> SecretKey)? = null
) {
    private val vaultDir = File(context.filesDir, "stealth_vault").apply { mkdirs() }
    private val metaPrefs = context.getSharedPreferences("vault_meta_prefs", Context.MODE_PRIVATE)
    private val keyStoreAlias = "CuteMarmot_Vault_Hardware_MasterKey_v1"

    // Read-only compatibility. Never selected for new encryption.
    private val legacyMasterKeyBytes = byteArrayOf(
        0x53, 0x74, 0x65, 0x61, 0x6C, 0x74, 0x68, 0x56,
        0x61, 0x75, 0x6C, 0x74, 0x4B, 0x65, 0x79, 0x32,
        0x30, 0x32, 0x36, 0x41, 0x69, 0x6F, 0x6C, 0x6F,
        0x73, 0x4C, 0x75, 0x6B, 0x30, 0x30, 0x35, 0x21
    )
    private val legacySecretKey = SecretKeySpec(legacyMasterKeyBytes, "AES")


    private fun getHardwareSecretKey(): SecretKey {
        keyFactory?.let { return it() }
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (keyStore.containsAlias(keyStoreAlias)) {
            return (keyStore.getEntry(keyStoreAlias, null) as KeyStore.SecretKeyEntry).secretKey
        }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(KeyGenParameterSpec.Builder(
            keyStoreAlias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256).build())
        return generator.generateKey()
    }

    private fun writeVerified(input: InputStream, target: File, key: SecretKey) {
        val expected = FileOutputStream(target).use { output ->
            val digest = VaultCipher.encrypt(input, output, key)
            output.fd.sync()
            digest
        }
        val actual = FileInputStream(target).use {
            VaultCipher.decrypt(it, discardOutput, key, versioned = true)
        }
        check(MessageDigest.isEqual(expected, actual)) { "Vault verification failed" }
    }

    fun encryptAndImport(sourceUri: Uri): ImportResult {
        if (sourceUri.scheme != "content") return ImportResult.FAILED
        val id = UUID.randomUUID().toString()
        val pending = File(vaultDir, "$id.pending")
        val encrypted = File(vaultDir, "$id.enc")
        try {
            val key = getHardwareSecretKey()
            val resolver = context.contentResolver
            var displayName = "vault_file"
            resolver.query(sourceUri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) {
                    val index = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index >= 0) displayName = safeName(it.getString(index))
                }
            }
            val input = resolver.openInputStream(sourceUri) ?: throw IOException("No source stream")
            input.use { writeVerified(it, pending, key) }
            Os.rename(pending.absolutePath, encrypted.absolutePath)
            check(encrypted.isFile && !pending.exists()) { "Vault commit failed" }
            check(metaPrefs.edit().putString("${encrypted.name}_display_name", displayName).commit())
        } catch (e: Exception) {
            pending.delete()
            encrypted.delete()
            metaPrefs.edit().remove("${encrypted.name}_display_name").apply()
            Log.e(TAG, "导入失败，未删除原文件", e)
            return ImportResult.FAILED
        }
        // The grant authorizes only this URI, never a provider-supplied physical path.
        val removed = try {
            if (DocumentsContract.isDocumentUri(context, sourceUri)) {
                DocumentsContract.deleteDocument(context.contentResolver, sourceUri)
            } else {
                context.contentResolver.delete(sourceUri, null, null) > 0
            }
        } catch (_: Exception) { false }
        return if (removed) ImportResult.SOURCE_REMOVED else ImportResult.SOURCE_RETAINED
    }

    fun listVaultFiles(): List<VaultFileItem> {
        return vaultDir.listFiles { file -> file.isFile && file.name.endsWith(".enc") }
            .orEmpty().map { file ->
                val name = safeName(metaPrefs.getString("${file.name}_display_name", null)
                    ?: file.name.removeSuffix(".enc").substringAfter("_"))
                val mime = when (name.substringAfterLast('.', "").lowercase()) {
                    "jpg", "jpeg", "png" -> "image/*"
                    "mp4", "mkv" -> "video/*"
                    "mp3", "m4a" -> "audio/*"
                    else -> "application/octet-stream"
                }
                VaultFileItem(file.name, name, metaPrefs.getString("${file.name}_orig_path", null),
                    mime, file.length(), file)
            }.sortedByDescending { it.encryptedFile.lastModified() }
    }

    private fun checkedFile(item: VaultFileItem): File {
        require(item.id == File(item.id).name && item.id.endsWith(".enc"))
        val file = File(vaultDir, item.id)
        require(file.canonicalFile.parentFile == vaultDir.canonicalFile && file.isFile)
        return file
    }

    private fun decryptTo(source: File, temp: File, key: SecretKey, versioned: Boolean) {
        FileInputStream(source).use { input ->
            FileOutputStream(temp).use { output ->
                VaultCipher.decrypt(input, output, key, versioned)
            }
        }
    }

    /** Export to a user-selected URI; retain vault ciphertext after export. */
    fun decryptAndExport(item: VaultFileItem, destination: Uri): ExportResult {
        if (destination.scheme != "content") return ExportResult.FAILED
        val temp = try {
            File.createTempFile("vault_restore_", ".tmp", context.cacheDir)
        } catch (e: Exception) {
            Log.e(TAG, "无法创建私有导出缓存，密柜副本保留", e)
            return ExportResult.FAILED
        }
        try {
            val source = checkedFile(item)
            val versioned = FileInputStream(source).use { VaultCipher.isVersioned(it) }
            if (versioned) {
                decryptTo(source, temp, getHardwareSecretKey(), true)
            } else {
                try {
                    decryptTo(source, temp, getHardwareSecretKey(), false)
                } catch (_: Exception) {
                    // FileOutputStream truncates any unauthenticated partial plaintext.
                    decryptTo(source, temp, legacySecretKey, false)
                }
            }
            // Authenticate entirely in private cache before sending bytes to the destination.
            var migrated = versioned
            if (!versioned) {
                val replacement = File.createTempFile("migration_", ".pending", vaultDir)
                try {
                    val key = getHardwareSecretKey()
                    FileInputStream(temp).use { writeVerified(it, replacement, key) }
                    Os.rename(replacement.absolutePath, source.absolutePath)
                    migrated = true
                } catch (e: Exception) {
                    Log.w(TAG, "旧文件迁移未完成，保留旧密文", e)
                } finally {
                    replacement.delete()
                }
            }
            val output = context.contentResolver.openOutputStream(destination, "wt")
                ?: throw IOException("No destination stream")
            output.use { target -> FileInputStream(temp).use { it.copyTo(target) } }
            return if (migrated) ExportResult.EXPORTED else ExportResult.EXPORTED_LEGACY_RETAINED
        } catch (e: Exception) {
            Log.e(TAG, "导出失败，密柜副本保留", e)
            return ExportResult.FAILED
        } finally {
            temp.delete()
        }
    }

    /** Logical deletion only; flash overwrite is not guaranteed. */
    fun shredFile(item: VaultFileItem): Boolean = try {
        if (checkedFile(item).delete()) {
            metaPrefs.edit().remove("${item.id}_orig_path")
                .remove("${item.id}_display_name").apply()
            true
        } else false
    } catch (_: Exception) { false }

    companion object {
        private const val TAG = "FileVaultEngine"
        internal fun safeName(name: String?): String = name.orEmpty()
            .map { if (it == '/' || it == '\\' || it.isISOControl()) '_' else it }
            .joinToString("").take(120)
            .takeIf { it.isNotBlank() && it != "." && it != ".." } ?: "vault_file"
        private val discardOutput = object : OutputStream() {
            override fun write(value: Int) {}
            override fun write(bytes: ByteArray, offset: Int, length: Int) {}
        }
    }
}
