package com.airlink.mobile.crypto

import android.util.Base64
import android.util.Log
import androidx.annotation.Keep
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * AES-256-GCM encryption manager for AirLink.
 *
 * Provides authenticated encryption for WebSocket JSON payloads and binary screen frames.
 * Uses 12-byte random IVs per message and 128-bit authentication tags.
 * Supports graceful fallback to plain JSON / binary frames for backwards compatibility.
 */
@Keep
class AirLinkCrypto {

    @Volatile
    private var secretKey: SecretKey? = null
    private val secureRandom = SecureRandom()

    val isKeyEstablished: Boolean
        get() = secretKey != null

    /**
     * Initializes the symmetric key directly from raw bytes.
     * If the input is not 32 bytes, SHA-256 is used to derive a 256-bit key.
     */
    fun establishKey(keyBytes: ByteArray) {
        val key = if (keyBytes.size == 32) {
            keyBytes
        } else {
            val md = MessageDigest.getInstance("SHA-256")
            md.digest(keyBytes)
        }
        secretKey = SecretKeySpec(key, "AES")
        Log.d(TAG, "AES-256-GCM symmetric key established successfully")
    }

    /**
     * Derives or extracts the AES-256-GCM key from the pairing handshake response.
     */
    fun establishKeyFromHandshake(handshakeJson: JSONObject, deviceId: String) {
        val keyStr = when {
            handshakeJson.has("key") -> handshakeJson.getString("key")
            handshakeJson.has("sessionKey") -> handshakeJson.getString("sessionKey")
            handshakeJson.has("secret") -> handshakeJson.getString("secret")
            else -> null
        }

        if (!keyStr.isNullOrEmpty()) {
            val decoded = try {
                Base64.decode(keyStr, Base64.DEFAULT)
            } catch (_: Exception) {
                keyStr.toByteArray(StandardCharsets.UTF_8)
            }
            establishKey(decoded)
        } else {
            // Deterministic pairing key derived from device ID + shared salt
            val seed = "$deviceId:airlink_aes256_gcm_salt"
            val md = MessageDigest.getInstance("SHA-256")
            val derived = md.digest(seed.toByteArray(StandardCharsets.UTF_8))
            establishKey(derived)
        }
    }

    /**
     * Clears the current session key upon disconnect.
     */
    fun clearKey() {
        secretKey = null
        Log.d(TAG, "AES-256-GCM key cleared")
    }

    /**
     * Encrypts outgoing JSON text into an authenticated AES-256-GCM payload.
     * Output format: {"type": "encrypted", "iv": "<base64_12b>", "data": "<base64_ciphertext_tag>"}
     */
    fun encryptText(plainText: String): String {
        val key = secretKey ?: return plainText
        return try {
            val iv = ByteArray(12)
            secureRandom.nextBytes(iv)

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
            val cipherBytes = cipher.doFinal(plainText.toByteArray(StandardCharsets.UTF_8))

            val ivBase64 = Base64.encodeToString(iv, Base64.NO_WRAP)
            val dataBase64 = Base64.encodeToString(cipherBytes, Base64.NO_WRAP)

            JSONObject().apply {
                put("type", TYPE_ENCRYPTED)
                put("iv", ivBase64)
                put("data", dataBase64)
            }.toString()
        } catch (e: Exception) {
            Log.e(TAG, "Text encryption failed: ${e.message}", e)
            plainText
        }
    }

    /**
     * Decrypts an incoming text message if encrypted; gracefully falls back to raw text
     * if unencrypted or plaintext JSON.
     */
    fun decryptText(rawText: String): String {
        val key = secretKey ?: return rawText
        return try {
            val trimmed = rawText.trim()
            if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) {
                return rawText
            }
            val json = JSONObject(trimmed)
            if (json.optString("type") != TYPE_ENCRYPTED) {
                // Backwards-compatible fallback to plaintext JSON
                return rawText
            }

            val (iv, cipherBytes) = if (json.has("iv") && json.has("data")) {
                val ivBase64 = json.getString("iv")
                val dataBase64 = json.getString("data")
                Pair(Base64.decode(ivBase64, Base64.NO_WRAP), Base64.decode(dataBase64, Base64.NO_WRAP))
            } else if (json.has("payload")) {
                val combined = Base64.decode(json.getString("payload"), Base64.NO_WRAP)
                if (combined.size < 28) return rawText
                val iv = combined.copyOfRange(0, 12)
                val cipher = combined.copyOfRange(12, combined.size)
                Pair(iv, cipher)
            } else {
                return rawText
            }

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
            val plainBytes = cipher.doFinal(cipherBytes)
            String(plainBytes, StandardCharsets.UTF_8)
        } catch (e: Exception) {
            Log.w(TAG, "Decryption fallback triggered: ${e.message}")
            rawText
        }
    }

    /**
     * Encrypts outgoing binary frames (screen mirroring JPEGs) using AES-256-GCM.
     * Output format: [4 bytes MAGIC "AENC"] + [12 bytes IV] + [Ciphertext + 16 bytes GCM Tag]
     */
    fun encryptBinary(plainBytes: ByteArray): ByteArray {
        val key = secretKey ?: return plainBytes
        return try {
            val iv = ByteArray(12)
            secureRandom.nextBytes(iv)

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
            val cipherBytes = cipher.doFinal(plainBytes)

            val output = ByteArray(4 + 12 + cipherBytes.size)
            System.arraycopy(MAGIC_BYTES, 0, output, 0, 4)
            System.arraycopy(iv, 0, output, 4, 12)
            System.arraycopy(cipherBytes, 0, output, 16, cipherBytes.size)
            output
        } catch (e: Exception) {
            Log.e(TAG, "Binary encryption failed: ${e.message}", e)
            plainBytes
        }
    }

    /**
     * Decrypts incoming binary bytes if encrypted; gracefully falls back to raw bytes
     * if unencrypted (e.g. raw JPEG/PNG).
     */
    fun decryptBinary(rawBytes: ByteArray): ByteArray {
        val key = secretKey ?: return rawBytes
        if (rawBytes.size < 4 + 12 + 16) {
            return rawBytes
        }
        val isEncryptedMagic = rawBytes[0] == MAGIC_BYTES[0] &&
                               rawBytes[1] == MAGIC_BYTES[1] &&
                               rawBytes[2] == MAGIC_BYTES[2] &&
                               rawBytes[3] == MAGIC_BYTES[3]
        if (!isEncryptedMagic) {
            return rawBytes
        }

        return try {
            val iv = ByteArray(12)
            System.arraycopy(rawBytes, 4, iv, 0, 12)
            val cipherLen = rawBytes.size - 16
            val cipherBytes = ByteArray(cipherLen)
            System.arraycopy(rawBytes, 16, cipherBytes, 0, cipherLen)

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
            cipher.doFinal(cipherBytes)
        } catch (e: Exception) {
            Log.w(TAG, "Binary decryption fallback triggered: ${e.message}")
            rawBytes
        }
    }

    companion object {
        private const val TAG = "AirLinkCrypto"
        const val TYPE_ENCRYPTED = "encrypted"
        val MAGIC_BYTES = byteArrayOf('A'.code.toByte(), 'E'.code.toByte(), 'N'.code.toByte(), 'C'.code.toByte())
    }
}
