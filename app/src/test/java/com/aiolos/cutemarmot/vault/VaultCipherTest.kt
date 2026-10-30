package com.aiolos.cutemarmot.vault

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.SecretKeySpec

class VaultCipherTest {
    private fun key() = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()

    @Test fun roundTripEmptyAndMultiBufferFiles() {
        for (bytes in listOf(ByteArray(0), ByteArray(25000) { (it % 251).toByte() })) {
            val key = key()
            val encoded = ByteArrayOutputStream()
            val expected = VaultCipher.encrypt(bytes.inputStream(), encoded, key)
            assertTrue(VaultCipher.isVersioned(encoded.toByteArray().inputStream()))
            val decoded = ByteArrayOutputStream()
            val actual = VaultCipher.decrypt(encoded.toByteArray().inputStream(), decoded, key, true)
            assertArrayEquals(bytes, decoded.toByteArray())
            assertArrayEquals(expected, actual)
        }
    }

    @Test fun corruptedTagWrongKeyAndTruncationAreRejected() {
        val key = key()
        val output = ByteArrayOutputStream()
        VaultCipher.encrypt(ByteArray(20000) { 42 }.inputStream(), output, key)
        val valid = output.toByteArray()
        val corrupted = valid.copyOf().apply { this[lastIndex] = (last().toInt() xor 1).toByte() }
        for (bad in listOf(corrupted, valid.copyOf(valid.size - 1), valid.copyOf(10))) {
            assertThrows(Exception::class.java) {
                VaultCipher.decrypt(bad.inputStream(), ByteArrayOutputStream(), key, true)
            }
        }
        assertThrows(Exception::class.java) {
            VaultCipher.decrypt(valid.inputStream(), ByteArrayOutputStream(), key(), true)
        }
    }

    @Test fun headerlessDeviceAndCompatibilityCiphertextsRemainReadable() {
        // An independent legacy-format producer; no production shared secret copied into tests.
        for (key in listOf(key(), SecretKeySpec(ByteArray(32) { 7 }, "AES"))) {
            val bytes = "legacy document".toByteArray()
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key)
            val encoded = cipher.iv + cipher.doFinal(bytes)
            assertFalse(VaultCipher.isVersioned(ByteArrayInputStream(encoded)))
            val decoded = ByteArrayOutputStream()
            VaultCipher.decrypt(encoded.inputStream(), decoded, key, false)
            assertArrayEquals(bytes, decoded.toByteArray())
            val migrated = ByteArrayOutputStream()
            val deviceKey = key()
            VaultCipher.encrypt(decoded.toByteArray().inputStream(), migrated, deviceKey)
            val recovered = ByteArrayOutputStream()
            VaultCipher.decrypt(migrated.toByteArray().inputStream(), recovered, deviceKey, true)
            assertArrayEquals(bytes, recovered.toByteArray())
        }
    }

    @Test fun providerDisplayNamesAreOnlySafeLabels() {
        assertEquals(".._.._private_file", FileVaultEngine.safeName("../../private/file"))
        assertEquals("evil_name_.jpg", FileVaultEngine.safeName("evil\\name\n.jpg"))
        assertEquals("vault_file", FileVaultEngine.safeName(".."))
        assertEquals("photo.jpg", FileVaultEngine.safeName("photo.jpg"))
    }
}
