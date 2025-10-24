package net.milosvasic.factory.security

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * Unit tests for the Encryption class.
 *
 * Tests cover:
 * - Basic encryption/decryption
 * - Different data types and sizes
 * - Error handling (wrong key, tampered data, invalid format)
 * - Security properties (unique IVs, unique salts, authentication)
 * - Edge cases (empty strings, special characters, large data)
 *
 * @author Mail Server Factory Team
 * @since 3.1.0
 */
@DisplayName("Encryption Tests")
class EncryptionTest {

    private val testMasterKey = "test-master-key-12345678"
    private val strongMasterKey = "VeryStrongMasterKey!@#123456789"

    @Test
    @DisplayName("Encrypt and decrypt simple string")
    fun testBasicEncryptionDecryption() {
        val plaintext = "Hello, World!"

        val encrypted = Encryption.encrypt(plaintext, testMasterKey)
        assertNotNull(encrypted)
        assertNotEquals(plaintext, encrypted)
        assertTrue(encrypted.contains(":"))

        val decrypted = Encryption.decrypt(encrypted, testMasterKey)
        assertEquals(plaintext, decrypted)
    }

    @Test
    @DisplayName("Encrypt and decrypt password")
    fun testPasswordEncryption() {
        val password = "MySecureP@ssw0rd!"

        val encrypted = Encryption.encrypt(password, testMasterKey)
        val decrypted = Encryption.decrypt(encrypted, testMasterKey)

        assertEquals(password, decrypted)
    }

    @Test
    @DisplayName("Encrypt and decrypt complex data")
    fun testComplexDataEncryption() {
        val complexData = """
            {
                "username": "admin",
                "password": "P@ssw0rd!",
                "database": {
                    "host": "localhost",
                    "port": 5432
                }
            }
        """.trimIndent()

        val encrypted = Encryption.encrypt(complexData, strongMasterKey)
        val decrypted = Encryption.decrypt(encrypted, strongMasterKey)

        assertEquals(complexData, decrypted)
    }

    @Test
    @DisplayName("Encrypted data format is correct")
    fun testEncryptedDataFormat() {
        val plaintext = "test data"

        val encrypted = Encryption.encrypt(plaintext, testMasterKey)

        // Format should be: salt:iv:ciphertext
        val parts = encrypted.split(":")
        assertEquals(3, parts.size, "Encrypted data should have 3 parts (salt:iv:ciphertext)")

        // Each part should be valid Base64
        parts.forEach { part ->
            assertTrue(part.matches(Regex("^[A-Za-z0-9+/]+=*$")), "Part should be valid Base64: $part")
        }
    }

    @Test
    @DisplayName("Decryption with wrong key fails")
    fun testDecryptionWithWrongKey() {
        val plaintext = "secret data"
        val encrypted = Encryption.encrypt(plaintext, testMasterKey)

        val wrongKey = "wrong-master-key-987654321"

        assertThrows<DecryptionException> {
            Encryption.decrypt(encrypted, wrongKey)
        }
    }

    @Test
    @DisplayName("Decryption with tampered data fails")
    fun testDecryptionWithTamperedData() {
        val plaintext = "secret data"
        val encrypted = Encryption.encrypt(plaintext, testMasterKey)

        // Tamper with ciphertext by changing last character
        val tampered = encrypted.dropLast(1) + "X"

        assertThrows<DecryptionException> {
            Encryption.decrypt(tampered, testMasterKey)
        }
    }

    @Test
    @DisplayName("Decryption with invalid format fails")
    fun testDecryptionWithInvalidFormat() {
        // Not enough parts
        assertThrows<IllegalArgumentException> {
            Encryption.decrypt("invalid:format", testMasterKey)
        }

        // Too many parts
        assertThrows<IllegalArgumentException> {
            Encryption.decrypt("too:many:parts:here", testMasterKey)
        }

        // Invalid Base64
        assertThrows<DecryptionException> {
            Encryption.decrypt("invalid:base64:!!!!", testMasterKey)
        }
    }

    @Test
    @DisplayName("Encryption with empty data fails")
    fun testEncryptionWithEmptyData() {
        assertThrows<IllegalArgumentException> {
            Encryption.encrypt("", testMasterKey)
        }
    }

    @Test
    @DisplayName("Encryption with empty master key fails")
    fun testEncryptionWithEmptyMasterKey() {
        assertThrows<IllegalArgumentException> {
            Encryption.encrypt("data", "")
        }
    }

    @Test
    @DisplayName("Encryption with short master key fails")
    fun testEncryptionWithShortMasterKey() {
        assertThrows<IllegalArgumentException> {
            Encryption.encrypt("data", "short")
        }
    }

    @Test
    @DisplayName("Each encryption produces unique ciphertext (random IV)")
    fun testUniqueEncryptions() {
        val plaintext = "same data"

        val encrypted1 = Encryption.encrypt(plaintext, testMasterKey)
        val encrypted2 = Encryption.encrypt(plaintext, testMasterKey)
        val encrypted3 = Encryption.encrypt(plaintext, testMasterKey)

        // All encrypted values should be different (due to random IV and salt)
        assertNotEquals(encrypted1, encrypted2)
        assertNotEquals(encrypted2, encrypted3)
        assertNotEquals(encrypted1, encrypted3)

        // But all should decrypt to same plaintext
        assertEquals(plaintext, Encryption.decrypt(encrypted1, testMasterKey))
        assertEquals(plaintext, Encryption.decrypt(encrypted2, testMasterKey))
        assertEquals(plaintext, Encryption.decrypt(encrypted3, testMasterKey))
    }

    @Test
    @DisplayName("Encrypt and decrypt Unicode characters")
    fun testUnicodeEncryption() {
        val unicode = "Hello 世界 🌍 مرحبا Привет"

        val encrypted = Encryption.encrypt(unicode, testMasterKey)
        val decrypted = Encryption.decrypt(encrypted, testMasterKey)

        assertEquals(unicode, decrypted)
    }

    @Test
    @DisplayName("Encrypt and decrypt special characters")
    fun testSpecialCharactersEncryption() {
        val special = "!@#$%^&*()_+-=[]{}|;':\",./<>?`~"

        val encrypted = Encryption.encrypt(special, testMasterKey)
        val decrypted = Encryption.decrypt(encrypted, testMasterKey)

        assertEquals(special, decrypted)
    }

    @Test
    @DisplayName("Encrypt and decrypt newlines and whitespace")
    fun testWhitespaceEncryption() {
        val whitespace = "Line 1\nLine 2\r\nLine 3\tTabbed\t  Spaces  "

        val encrypted = Encryption.encrypt(whitespace, testMasterKey)
        val decrypted = Encryption.decrypt(encrypted, testMasterKey)

        assertEquals(whitespace, decrypted)
    }

    @Test
    @DisplayName("Encrypt and decrypt large data")
    fun testLargeDataEncryption() {
        // 10KB of data
        val largeData = "A".repeat(10 * 1024)

        val encrypted = Encryption.encrypt(largeData, strongMasterKey)
        val decrypted = Encryption.decrypt(encrypted, strongMasterKey)

        assertEquals(largeData, decrypted)
    }

    @Test
    @DisplayName("Encrypt and decrypt very large data")
    fun testVeryLargeDataEncryption() {
        // 100KB of data
        val veryLargeData = "B".repeat(100 * 1024)

        val encrypted = Encryption.encrypt(veryLargeData, strongMasterKey)
        val decrypted = Encryption.decrypt(encrypted, strongMasterKey)

        assertEquals(veryLargeData, decrypted)
    }

    @Test
    @DisplayName("Master key with special characters works")
    fun testMasterKeyWithSpecialCharacters() {
        val masterKey = "Sp€c!@l-K€y#123\$%^&*()"
        val plaintext = "test data"

        val encrypted = Encryption.encrypt(plaintext, masterKey)
        val decrypted = Encryption.decrypt(encrypted, masterKey)

        assertEquals(plaintext, decrypted)
    }

    @Test
    @DisplayName("Different master keys produce different ciphertexts")
    fun testDifferentMasterKeys() {
        val plaintext = "same data"
        val key1 = "master-key-1-12345678"
        val key2 = "master-key-2-87654321"

        val encrypted1 = Encryption.encrypt(plaintext, key1)
        val encrypted2 = Encryption.encrypt(plaintext, key2)

        assertNotEquals(encrypted1, encrypted2)

        // Each can be decrypted with its own key
        assertEquals(plaintext, Encryption.decrypt(encrypted1, key1))
        assertEquals(plaintext, Encryption.decrypt(encrypted2, key2))

        // But not with the other key
        assertThrows<DecryptionException> {
            Encryption.decrypt(encrypted1, key2)
        }
        assertThrows<DecryptionException> {
            Encryption.decrypt(encrypted2, key1)
        }
    }

    @Test
    @DisplayName("Wipe function clears char array")
    fun testWipeFunction() {
        val sensitive = "password123".toCharArray()
        val originalLength = sensitive.size

        Encryption.wipe(sensitive)

        // All characters should be null
        assertEquals(originalLength, sensitive.size)
        sensitive.forEach { char ->
            assertEquals('\u0000', char)
        }
    }

    @Test
    @DisplayName("Encryption is deterministic for same IV and salt (security property)")
    fun testEncryptionProperties() {
        // This test verifies that the same plaintext with different IVs/salts
        // produces different ciphertexts (preventing pattern analysis)

        val plaintext = "repeated data"

        val encrypted1 = Encryption.encrypt(plaintext, testMasterKey)
        val encrypted2 = Encryption.encrypt(plaintext, testMasterKey)

        // Different random IVs and salts should produce different ciphertexts
        assertNotEquals(encrypted1, encrypted2, "Same plaintext should produce different ciphertexts (random IV/salt)")

        // Extract salts (first part)
        val salt1 = encrypted1.split(":")[0]
        val salt2 = encrypted2.split(":")[0]
        assertNotEquals(salt1, salt2, "Salts should be different")

        // Extract IVs (second part)
        val iv1 = encrypted1.split(":")[1]
        val iv2 = encrypted2.split(":")[1]
        assertNotEquals(iv1, iv2, "IVs should be different")
    }

    @Test
    @DisplayName("Decryption validates authentication tag (GCM)")
    fun testAuthenticationTag() {
        val plaintext = "authenticated data"
        val encrypted = Encryption.encrypt(plaintext, testMasterKey)

        // Split into parts
        val parts = encrypted.split(":")
        val salt = parts[0]
        val iv = parts[1]
        val ciphertext = parts[2]

        // Modify ciphertext (flip a bit in the last character)
        val tamperedCiphertext = if (ciphertext.last() == 'A') {
            ciphertext.dropLast(1) + 'B'
        } else {
            ciphertext.dropLast(1) + 'A'
        }

        val tampered = "$salt:$iv:$tamperedCiphertext"

        // Decryption should fail with authentication error
        val exception = assertThrows<DecryptionException> {
            Encryption.decrypt(tampered, testMasterKey)
        }

        assertTrue(
            exception.message?.contains("Authentication failed") == true ||
            exception.message?.contains("tampered") == true,
            "Exception should indicate authentication failure"
        )
    }

    @Test
    @DisplayName("Stress test - multiple encryptions and decryptions")
    fun testStressEncryptionDecryption() {
        val iterations = 100

        for (i in 1..iterations) {
            val plaintext = "Test data iteration $i with random content ${System.nanoTime()}"
            val encrypted = Encryption.encrypt(plaintext, testMasterKey)
            val decrypted = Encryption.decrypt(encrypted, testMasterKey)

            assertEquals(plaintext, decrypted, "Failed at iteration $i")
        }
    }
}
