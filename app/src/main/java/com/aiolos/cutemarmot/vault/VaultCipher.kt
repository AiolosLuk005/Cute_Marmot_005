package com.aiolos.cutemarmot.vault

import java.io.DataInputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Version 2 uses only a device key. Headerless files retain read-only compatibility. */
internal object VaultCipher {
    private val header = "CMVAULT2".toByteArray(Charsets.US_ASCII)

    fun isVersioned(input: InputStream): Boolean {
        val actual = ByteArray(header.size)
        return try {
            DataInputStream(input).readFully(actual)
            actual.contentEquals(header)
        } catch (_: java.io.EOFException) { false }
    }

    fun encrypt(input: InputStream, output: OutputStream, key: SecretKey): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        require(cipher.iv.size == 12)
        cipher.updateAAD(header)
        output.write(header)
        output.write(cipher.iv)
        return transfer(input, output, cipher, digestInput = true)
    }

    fun decrypt(input: InputStream, output: OutputStream, key: SecretKey, versioned: Boolean): ByteArray {
        val data = DataInputStream(input)
        if (versioned) {
            val actual = ByteArray(header.size)
            data.readFully(actual)
            require(actual.contentEquals(header)) { "Unknown vault format" }
        }
        val iv = ByteArray(12)
        data.readFully(iv)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
        if (versioned) cipher.updateAAD(header)
        // Explicit doFinal propagates authentication errors; callers stage plaintext privately.
        return transfer(data, output, cipher, digestInput = false)
    }

    private fun transfer(input: InputStream, output: OutputStream, cipher: Cipher, digestInput: Boolean): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(8192)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            if (count == 0) continue
            if (digestInput) digest.update(buffer, 0, count)
            cipher.update(buffer, 0, count)?.let {
                output.write(it)
                if (!digestInput) digest.update(it)
            }
        }
        val last = cipher.doFinal()
        output.write(last)
        if (!digestInput) digest.update(last)
        return digest.digest()
    }
}
