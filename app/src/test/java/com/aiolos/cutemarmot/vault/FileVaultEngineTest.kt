package com.aiolos.cutemarmot.vault

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver
import java.io.File
import javax.crypto.KeyGenerator
import javax.crypto.Cipher

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], shadows = [FileVaultEngineTest.RenameOs::class])
class FileVaultEngineTest {
    private lateinit var context: Context
    private lateinit var provider: TestProvider
    private val uri = Uri.parse("content://vault.security.test/source")
    private val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()

    @Before fun setup() {
        org.robolectric.shadows.ShadowLog.stream = System.out
        context = RuntimeEnvironment.getApplication()
        File(context.filesDir, "stealth_vault").deleteRecursively()
        context.getSharedPreferences("vault_meta_prefs", 0).edit().clear().commit()
        provider = TestProvider().apply {
            source = File(this@FileVaultEngineTest.context.cacheDir, "source.txt").apply { writeText("private content") }
            victim = File(this@FileVaultEngineTest.context.cacheDir, "unrelated.txt").apply { writeText("do not delete") }
        }
        provider.attachInfo(context, android.content.pm.ProviderInfo().apply {
            authority = "vault.security.test"
            exported = true
        })
        ShadowContentResolver.registerProviderInternal("vault.security.test", provider)
    }

    @Test fun maliciousMetadataAndFailedDeletionCannotDeleteUnrelatedFile() {
        val engine = FileVaultEngine(context) { key }
        assertEquals(ImportResult.SOURCE_RETAINED, engine.encryptAndImport(uri))
        assertEquals("do not delete", provider.victim.readText())
        assertEquals("private content", provider.source.readText())
        assertEquals(1, engine.listVaultFiles().size)
        assertFalse(provider.requestedColumns.contains("_data"))
        assertEquals(listOf(uri), provider.deletions)
    }

    @Test fun keyFailureAndMissingStreamNeverDeleteSource() {
        val failed = FileVaultEngine(context) { throw IllegalStateException("keystore unavailable") }
        assertEquals(ImportResult.FAILED, failed.encryptAndImport(uri))
        assertTrue(provider.deletions.isEmpty())
        assertTrue(failed.listVaultFiles().isEmpty())
        provider.failOpen = true
        val engine = FileVaultEngine(context) { key }
        assertEquals(ImportResult.FAILED, engine.encryptAndImport(uri))
        assertTrue(provider.deletions.isEmpty())
        assertTrue(engine.listVaultFiles().isEmpty())
        assertEquals("private content", provider.source.readText())
    }

    @Test fun successfulMoveDeletesOnlySelectedUriAfterEncryption() {
        provider.allowDelete = true
        val engine = FileVaultEngine(context) { key }
        assertEquals(ImportResult.SOURCE_REMOVED, engine.encryptAndImport(uri))
        assertFalse(provider.source.exists())
        assertTrue(provider.victim.exists())
        assertEquals(1, engine.listVaultFiles().size)
        val item = engine.listVaultFiles().single()
        assertTrue(item.encryptedFile.inputStream().use { VaultCipher.isVersioned(it) })
    }

    @Test fun exportIgnoresHistoricalPathAndRetainsVaultCopy() {
        val engine = FileVaultEngine(context) { key }
        assertEquals(ImportResult.SOURCE_RETAINED, engine.encryptAndImport(uri))
        val item = engine.listVaultFiles().single()
        context.getSharedPreferences("vault_meta_prefs", 0).edit()
            .putString("${item.id}_orig_path", provider.victim.absolutePath).commit()
        val result = engine.decryptAndExport(engine.listVaultFiles().single(), Uri.parse("content://vault.security.test/destination"))
        assertEquals(ExportResult.EXPORTED, result)
        assertEquals("private content", File(provider.source.parentFile, "export.txt").readText())
        assertEquals("do not delete", provider.victim.readText())
        assertTrue(item.encryptedFile.exists())
    }

    @Test fun corruptCiphertextNeverReachesDestination() {
        val engine = FileVaultEngine(context) { key }
        engine.encryptAndImport(uri)
        val item = engine.listVaultFiles().single()
        val bytes = item.encryptedFile.readBytes()
        bytes[bytes.lastIndex] = (bytes.last().toInt() xor 1).toByte()
        item.encryptedFile.writeBytes(bytes)
        assertEquals(ExportResult.FAILED,
            engine.decryptAndExport(item, Uri.parse("content://vault.security.test/destination")))
        assertFalse(File(provider.source.parentFile, "export.txt").exists())
        assertTrue(item.encryptedFile.exists())
    }

    @Test fun legacyCiphertextMigratesOnlyAfterSuccessfulVerification() {
        val dir = File(context.filesDir, "stealth_vault").apply { mkdirs() }
        val file = File(dir, "123_legacy.txt.enc")
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        file.writeBytes(cipher.iv + cipher.doFinal("old content".toByteArray()))
        val engine = FileVaultEngine(context) { key }
        assertEquals(ExportResult.EXPORTED,
            engine.decryptAndExport(engine.listVaultFiles().single(), Uri.parse("content://vault.security.test/destination")))
        assertTrue(file.inputStream().use { VaultCipher.isVersioned(it) })
        assertEquals("old content", File(provider.source.parentFile, "export.txt").readText())
    }

    @Test fun unavailablePrivateCacheReturnsFailureAndKeepsCiphertext() {
        val engine = FileVaultEngine(context) { key }
        engine.encryptAndImport(uri)
        val item = engine.listVaultFiles().single()
        val invalidCache = File(context.filesDir, "not_a_directory").apply { writeText("occupied") }
        val wrapped = object : android.content.ContextWrapper(context) {
            override fun getCacheDir(): File = invalidCache
        }
        assertEquals(ExportResult.FAILED, FileVaultEngine(wrapped) { key }
            .decryptAndExport(item, Uri.parse("content://vault.security.test/destination")))
        assertTrue(item.encryptedFile.exists())
        assertFalse(File(provider.source.parentFile, "export.txt").exists())
    }

    class TestProvider : ContentProvider() {
        lateinit var source: File
        lateinit var victim: File
        var allowDelete = false
        var failOpen = false
        val requestedColumns = mutableListOf<String>()
        val deletions = mutableListOf<Uri>()
        override fun onCreate() = true
        override fun query(uri: Uri, projection: Array<out String>?, selection: String?, args: Array<out String>?, sort: String?): Cursor {
            requestedColumns.addAll(projection?.toList().orEmpty())
            return MatrixCursor(arrayOf(OpenableColumns.DISPLAY_NAME, "_data")).apply {
                addRow(arrayOf("../../unrelated.txt", victim.absolutePath))
            }
        }
        override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
            if (failOpen) return null
            if (mode.contains('w')) return ParcelFileDescriptor.open(
                File(source.parentFile, "export.txt"),
                ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_WRITE_ONLY or ParcelFileDescriptor.MODE_TRUNCATE)
            return ParcelFileDescriptor.open(source, ParcelFileDescriptor.MODE_READ_ONLY)
        }
        override fun delete(uri: Uri, selection: String?, args: Array<out String>?): Int {
            deletions += uri
            return if (allowDelete && source.delete()) 1 else 0
        }
        override fun getType(uri: Uri) = "text/plain"
        override fun insert(uri: Uri, values: ContentValues?): Uri? = null
        override fun update(uri: Uri, values: ContentValues?, selection: String?, args: Array<out String>?) = 0
    }

    // Robolectric's SDK 28 Os.rename is a no-op on this host. Model the real same-volume
    // atomic replacement using the host filesystem; Android behavior still needs device testing.
    @org.robolectric.annotation.Implements(android.system.Os::class)
    class RenameOs {
        companion object {
            @JvmStatic
            @org.robolectric.annotation.Implementation
            fun rename(oldPath: String, newPath: String) {
                java.nio.file.Files.move(File(oldPath).toPath(), File(newPath).toPath(),
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING)
            }
        }
    }
}
