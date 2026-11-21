package com.example.core.security

import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SecurityManager @Inject constructor() {

    private val secureRandom = SecureRandom()

    fun generateSaltHex(bytes: Int = 16): String {
        val buf = ByteArray(bytes)
        secureRandom.nextBytes(buf)
        return buf.toHex()
    }

    fun hashPin(pin: String, saltHex: String): String {
        val spec = PBEKeySpec(pin.toCharArray(), saltHex.hexToBytes(), 10_000, 256)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return factory.generateSecret(spec).encoded.toHex()
    }

    fun verifyPin(pin: String, saltHex: String, expectedHashHex: String): Boolean {
        if (pin.isBlank() || saltHex.isBlank() || expectedHashHex.isBlank()) return false
        val actual = hashPin(pin, saltHex)
        return MessageDigest.isEqual(actual.toByteArray(), expectedHashHex.toByteArray())
    }

    fun generateSessionKey(): SecretKey {
        val keyBytes = ByteArray(32)
        secureRandom.nextBytes(keyBytes)
        return SecretKeySpec(keyBytes, "AES")
    }

    fun keyFromHex(hex: String): SecretKey {
        val bytes = hex.hexToBytes()
        require(bytes.size == 32) { "AES-256 key must be 32 bytes" }
        return SecretKeySpec(bytes, "AES")
    }

    fun keyToHex(key: SecretKey): String = key.encoded.toHex()

    fun generatePairingPin(): String {
        val code = secureRandom.nextInt(900_000) + 100_000
        return code.toString()
    }

    /**
     * Encrypts a byte chunk with AES-256-GCM using a fresh 12-byte IV.
     * Output format: [12-byte IV][ciphertext + 16-byte GCM tag].
     */
    fun encryptChunk(plain: ByteArray, length: Int, key: SecretKey): ByteArray {
        val iv = ByteArray(GCM_IV_LENGTH)
        secureRandom.nextBytes(iv)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        val encrypted = cipher.doFinal(plain, 0, length)
        return ByteArray(GCM_IV_LENGTH + encrypted.size).apply {
            System.arraycopy(iv, 0, this, 0, GCM_IV_LENGTH)
            System.arraycopy(encrypted, 0, this, GCM_IV_LENGTH, encrypted.size)
        }
    }

    /**
     * Decrypts a byte chunk produced by [encryptChunk].
     */
    fun decryptChunk(packet: ByteArray, key: SecretKey): ByteArray {
        require(packet.size > GCM_IV_LENGTH + 16) { "Corrupted encrypted packet" }
        val iv = packet.copyOfRange(0, GCM_IV_LENGTH)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        return cipher.doFinal(packet, GCM_IV_LENGTH, packet.size - GCM_IV_LENGTH)
    }

    fun sha256(input: InputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(8192)
        var read: Int
        while (input.read(buffer).also { read = it } != -1) {
            digest.update(buffer, 0, read)
        }
        return digest.digest().toHex()
    }

    fun sha256Bytes(bytes: ByteArray): String {
        return MessageDigest.getInstance("SHA-256").digest(bytes).toHex()
    }

    /**
     * Prevents path traversal attacks when saving received files from peers.
     */
    fun sanitizeFileName(rawName: String): String {
        val base = rawName.substringAfterLast('/').substringAfterLast('\\')
            .replace(Regex("[^A-Za-z0-9._\\- ]"), "_")
            .trim()
        return if (base.isBlank() || base == "." || base == "..") {
            "aura_received_${System.currentTimeMillis()}.bin"
        } else {
            base.take(120)
        }
    }

    fun resolveSafeFileInDir(baseDir: File, rawName: String): File {
        val cleanName = sanitizeFileName(rawName)
        val target = File(baseDir, cleanName).canonicalFile
        val canonicalBase = baseDir.canonicalFile
        require(target.path.startsWith(canonicalBase.path + File.separator)) {
            "Unsafe path outside target directory"
        }
        return target
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    private fun String.hexToBytes(): ByteArray {
        val clean = trim()
        require(clean.length % 2 == 0) { "Invalid hex string" }
        return ByteArray(clean.length / 2) { i ->
            clean.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
    }

    companion object {
        private const val GCM_IV_LENGTH = 12
        private const val GCM_TAG_BITS = 128
    }
}
