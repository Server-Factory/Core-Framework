package net.milosvasic.factory.security

import java.security.SecureRandom
import java.security.spec.KeySpec
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Provides AES-256-GCM encryption and decryption for sensitive data.
 *
 * This implementation uses:
 * - AES-256-GCM (Galois/Counter Mode) for authenticated encryption
 * - PBKDF2 with HMAC-SHA256 for key derivation (65536 iterations)
 * - 12-byte random IV for each encryption operation
 * - 16-byte random salt for each password derivation
 * - 128-bit authentication tag for integrity verification
 *
 * Security features:
 * - Prevents tampering (authenticated encryption)
 * - Prevents rainbow table attacks (random salt)
 * - Prevents replay attacks (random IV)
 * - Key stretching (PBKDF2 with high iteration count)
 *
 * @author Mail Server Factory Team
 * @since 3.1.0
 */
object Encryption {

    private const val ALGORITHM = "AES/GCM/NoPadding"
    private const val KEY_ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val KEY_LENGTH = 256
    private const val ITERATION_COUNT = 65536
    private const val GCM_TAG_LENGTH = 128
    private const val GCM_IV_LENGTH = 12
    private const val SALT_LENGTH = 16

    private val secureRandom = SecureRandom()

    /**
     * Encrypts the given data using AES-256-GCM.
     *
     * The encrypted data format is: base64(salt):base64(iv):base64(ciphertext+tag)
     *
     * @param data The plain text data to encrypt
     * @param masterKey The master password used for key derivation
     * @return Encrypted data in the format "salt:iv:encrypted"
     * @throws IllegalArgumentException if data or masterKey is empty
     * @throws EncryptionException if encryption fails
     */
    fun encrypt(data: String, masterKey: String): String {
        require(data.isNotEmpty()) { "Data to encrypt cannot be empty" }
        require(masterKey.isNotEmpty()) { "Master key cannot be empty" }
        require(masterKey.length >= 8) { "Master key must be at least 8 characters" }

        try {
            val salt = generateSalt()
            val key = deriveKey(masterKey, salt)
            val iv = generateIV()

            val cipher = Cipher.getInstance(ALGORITHM)
            val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
            cipher.init(Cipher.ENCRYPT_MODE, key, gcmSpec)

            val encrypted = cipher.doFinal(data.toByteArray(Charsets.UTF_8))

            // Format: salt:iv:encrypted
            val saltBase64 = Base64.getEncoder().encodeToString(salt)
            val ivBase64 = Base64.getEncoder().encodeToString(iv)
            val encryptedBase64 = Base64.getEncoder().encodeToString(encrypted)

            return "$saltBase64:$ivBase64:$encryptedBase64"
        } catch (e: Exception) {
            throw EncryptionException("Failed to encrypt data: ${e.message}", e)
        }
    }

    /**
     * Decrypts the given encrypted data using AES-256-GCM.
     *
     * @param encryptedData The encrypted data in format "salt:iv:encrypted"
     * @param masterKey The master password used for key derivation
     * @return Decrypted plain text data
     * @throws IllegalArgumentException if format is invalid or masterKey is empty
     * @throws DecryptionException if decryption or authentication fails
     */
    fun decrypt(encryptedData: String, masterKey: String): String {
        require(encryptedData.isNotEmpty()) { "Encrypted data cannot be empty" }
        require(masterKey.isNotEmpty()) { "Master key cannot be empty" }

        val parts = encryptedData.split(":")
        require(parts.size == 3) { "Invalid encrypted data format. Expected format: salt:iv:encrypted" }

        try {
            val salt = Base64.getDecoder().decode(parts[0])
            val iv = Base64.getDecoder().decode(parts[1])
            val encrypted = Base64.getDecoder().decode(parts[2])

            require(salt.size == SALT_LENGTH) { "Invalid salt length: ${salt.size}, expected $SALT_LENGTH" }
            require(iv.size == GCM_IV_LENGTH) { "Invalid IV length: ${iv.size}, expected $GCM_IV_LENGTH" }

            val key = deriveKey(masterKey, salt)

            val cipher = Cipher.getInstance(ALGORITHM)
            val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
            cipher.init(Cipher.DECRYPT_MODE, key, gcmSpec)

            val decrypted = cipher.doFinal(encrypted)
            return String(decrypted, Charsets.UTF_8)
        } catch (e: javax.crypto.AEADBadTagException) {
            throw DecryptionException("Authentication failed. Data may have been tampered with or wrong master key provided.", e)
        } catch (e: Exception) {
            throw DecryptionException("Failed to decrypt data: ${e.message}", e)
        }
    }

    /**
     * Derives a cryptographic key from a password using PBKDF2.
     *
     * @param password The password to derive the key from
     * @param salt Random salt for key derivation
     * @return The derived secret key
     */
    private fun deriveKey(password: String, salt: ByteArray): SecretKey {
        val factory = SecretKeyFactory.getInstance(KEY_ALGORITHM)
        val spec: KeySpec = PBEKeySpec(
            password.toCharArray(),
            salt,
            ITERATION_COUNT,
            KEY_LENGTH
        )
        val tmp = factory.generateSecret(spec)
        return SecretKeySpec(tmp.encoded, "AES")
    }

    /**
     * Generates a random salt for key derivation.
     *
     * @return Random salt bytes
     */
    private fun generateSalt(): ByteArray {
        val salt = ByteArray(SALT_LENGTH)
        secureRandom.nextBytes(salt)
        return salt
    }

    /**
     * Generates a random initialization vector for GCM mode.
     *
     * @return Random IV bytes
     */
    private fun generateIV(): ByteArray {
        val iv = ByteArray(GCM_IV_LENGTH)
        secureRandom.nextBytes(iv)
        return iv
    }

    /**
     * Securely wipes a string from memory by overwriting with zeros.
     *
     * Note: This is best-effort. The JVM may have made copies.
     *
     * @param sensitive The sensitive string to wipe
     */
    fun wipe(sensitive: CharArray) {
        sensitive.fill('\u0000')
    }
}

/**
 * Exception thrown when encryption operations fail.
 */
class EncryptionException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Exception thrown when decryption operations fail.
 */
class DecryptionException(message: String, cause: Throwable? = null) : Exception(message, cause)
