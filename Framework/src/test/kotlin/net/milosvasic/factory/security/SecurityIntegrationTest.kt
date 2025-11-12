package net.milosvasic.factory.security

import net.milosvasic.factory.validation.InputValidator
import net.milosvasic.factory.validation.ValidationResult
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import java.io.File

/**
 * Integration tests for security components.
 *
 * Tests verify:
 * - End-to-end encryption/decryption workflows
 * - Integration between SecureConfiguration and Encryption
 * - Input validation in realistic scenarios
 * - Audit logging integration
 * - Error handling and edge cases
 * - Performance under load
 *
 * @author Mail Server Factory Team
 * @since 3.1.0
 */
@DisplayName("Security Integration Tests")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SecurityIntegrationTest {

    private val testMasterKey = "integration-test-master-key-12345678"
    private lateinit var tempDir: File

    @BeforeAll
    fun setupAll() {
        // Create temporary directory for audit logs
        tempDir = File.createTempFile("audit_test", "").apply {
            delete()
            mkdir()
            deleteOnExit()
        }

        System.setProperty("MAIL_FACTORY_AUDIT_LOG_DIR", tempDir.absolutePath)
        System.setProperty("MAIL_FACTORY_AUDIT_LOG_RETENTION_DAYS", "1")

        // Initialize AuditLogger after setting properties
        AuditLogger.initialize()
    }

    @AfterAll
    fun cleanupAll() {
        AuditLogger.shutdown()
        tempDir.deleteRecursively()
    }

    @BeforeEach
    fun setup() {
        // Set master key for tests that need it
        System.setProperty("MAIL_FACTORY_MASTER_KEY", testMasterKey)
    }

    // ========== End-to-End Encryption Workflows ==========

    @Test
    @DisplayName("Complete password encryption workflow")
    fun testCompletePasswordEncryptionWorkflow() {
        // Simulate user workflow: encrypt password -> save to config -> load from config -> decrypt

        // Step 1: User encrypts password with PasswordEncryptor
        val originalPassword = "MySecurePassword123!"
        val encrypted = Encryption.encrypt(originalPassword, testMasterKey)

        // Verify format
        assertTrue(encrypted.split(":").size == 3)

        // Step 2: Configuration stored with encrypted password
        val configJson = """
            {
                "database": {
                    "password": "encrypted:$encrypted"
                }
            }
        """.trimIndent()

        // Step 3: Application loads configuration
        val passwordFromConfig = "encrypted:$encrypted"

        // Step 4: SecureConfiguration detects and decrypts
        assertTrue(SecureConfiguration.isEncrypted(passwordFromConfig))

        val decrypted = SecureConfiguration.decryptPassword(
            passwordFromConfig,  // Don't remove prefix - decryptPassword() does it
            testMasterKey
        )

        // Step 5: Verify password matches original
        assertEquals(originalPassword, decrypted)

        // Step 6: Audit logging
        AuditLogger.logEncryption(AuditAction.DECRYPT, "database.password", success = true)
        AuditLogger.flush()

        // Verify audit log created
        val auditFiles = tempDir.listFiles { file ->
            file.name.startsWith("audit_") && file.name.endsWith(".log")
        }
        assertNotNull(auditFiles)
        assertTrue(auditFiles!!.isNotEmpty())
    }

    @Test
    @DisplayName("Multiple password encryption with same master key")
    fun testMultiplePasswordsEncryption() {
        val passwords = mapOf(
            "database" to "db_pass_123",
            "ssh" to "ssh_pass_456",
            "docker" to "docker_pass_789"
        )

        // Encrypt all passwords
        val encrypted = passwords.mapValues { (_, password) ->
            Encryption.encrypt(password, testMasterKey)
        }

        // Verify all unique (different IVs and salts)
        val uniqueEncrypted = encrypted.values.toSet()
        assertEquals(passwords.size, uniqueEncrypted.size)

        // Decrypt and verify
        encrypted.forEach { (key, encryptedPassword) ->
            val decrypted = Encryption.decrypt(encryptedPassword, testMasterKey)
            assertEquals(passwords[key], decrypted)
        }
    }

    @Test
    @DisplayName("Wrong master key fails gracefully")
    fun testWrongMasterKeyFailsGracefully() {
        val password = "test_password"
        val encrypted = Encryption.encrypt(password, testMasterKey)

        val wrongKey = "wrong-master-key-87654321"

        val exception = assertThrows<DecryptionException> {
            Encryption.decrypt(encrypted, wrongKey)
        }

        assertTrue(exception.message?.contains("Authentication failed") == true ||
                  exception.message?.contains("decrypt") == true)
    }

    // ========== Input Validation Integration ==========

    @Test
    @DisplayName("Configuration loading with validation")
    fun testConfigurationLoadingWithValidation() {
        // Simulate loading configuration with validation

        val configData = mapOf(
            "hostname" to "mail.example.com",
            "port" to "25",
            "username" to "admin",
            "email" to "admin@example.com",
            "path" to "/var/mail/data"
        )

        // Validate hostname
        val hostnameResult = InputValidator.validateHost(configData["hostname"]!!)
        assertTrue(hostnameResult.isSuccess())

        // Validate port
        val port = configData["port"]!!.toInt()
        val portResult = InputValidator.validatePort(port, allowPrivileged = true)
        assertTrue(portResult.isSuccess())

        // Validate username
        val usernameResult = InputValidator.validateUsername(configData["username"]!!)
        assertTrue(usernameResult.isSuccess())

        // Validate email
        val emailResult = InputValidator.validateEmail(configData["email"]!!)
        assertTrue(emailResult.isSuccess())

        // Validate path
        val pathResult = InputValidator.validatePath(
            configData["path"]!!,
            mustExist = false,
            allowAbsolute = true
        )
        assertTrue(pathResult.isSuccess())

        // Log validation success
        AuditLogger.logConfigurationChange(
            user = "system",
            resource = "mail.example.com",
            details = "Configuration validated successfully"
        )
    }

    @Test
    @DisplayName("Malicious input detected and rejected")
    fun testMaliciousInputRejected() {
        val maliciousInputs = mapOf(
            "command_injection" to "test; rm -rf /",
            "path_traversal" to "../../../etc/passwd",
            "sql_injection" to "admin' OR '1'='1",
            "invalid_hostname" to "host..example.com",
            "invalid_email" to "user@@example.com"
        )

        // Command injection
        val sanitized = InputValidator.sanitizeForShell(maliciousInputs["command_injection"]!!)
        assertFalse(sanitized.contains(";"))
        assertFalse(sanitized.contains("rm"))

        // Path traversal
        val pathResult = InputValidator.validatePath(
            maliciousInputs["path_traversal"]!!,
            mustExist = false
        )
        assertTrue(pathResult.isFailed())

        // Invalid hostname
        val hostnameResult = InputValidator.validateHost(maliciousInputs["invalid_hostname"]!!)
        assertTrue(hostnameResult.isFailed())

        // Invalid email
        val emailResult = InputValidator.validateEmail(maliciousInputs["invalid_email"]!!)
        assertTrue(emailResult.isFailed())

        // Log security event
        AuditLogger.log(
            event = AuditEvent.AUTHORIZATION,
            action = AuditAction.ACCESS,
            details = "Malicious input detected and rejected",
            result = AuditResult.DENIED
        )
    }

    // ========== SecureConfiguration Integration ==========

    @Test
    @DisplayName("SecureConfiguration with environment variables")
    fun testSecureConfigurationWithEnvironment() {
        // Set environment variables
        System.setProperty("MAIL_FACTORY_MASTER_KEY", testMasterKey)
        System.setProperty("MAIL_FACTORY_DB_PASSWORD", "env_db_pass")
        System.setProperty("MAIL_FACTORY_SSH_PASSWORD", "env_ssh_pass")

        try {
            // Get master key
            val masterKey = SecureConfiguration.getMasterKey()
            assertEquals(testMasterKey, masterKey)

            // Get passwords from environment
            val dbPass = SecureConfiguration.getDatabasePassword(null)
            assertEquals("env_db_pass", dbPass)

            val sshPass = SecureConfiguration.getSshPassword(null)
            assertEquals("env_ssh_pass", sshPass)

            // Verify secrets accessible
            val secret = SecureConfiguration.getSecret("MAIL_FACTORY_DB_PASSWORD")
            assertEquals("env_db_pass", secret)

        } finally {
            System.clearProperty("MAIL_FACTORY_MASTER_KEY")
            System.clearProperty("MAIL_FACTORY_DB_PASSWORD")
            System.clearProperty("MAIL_FACTORY_SSH_PASSWORD")
        }
    }

    @Test
    @DisplayName("SecureConfiguration encrypted password precedence")
    fun testEncryptedPasswordPrecedence() {
        val password = "test_password"
        val encrypted = Encryption.encrypt(password, testMasterKey)
        val encryptedFormat = "encrypted:$encrypted"

        System.setProperty("MAIL_FACTORY_MASTER_KEY", testMasterKey)
        System.setProperty("MAIL_FACTORY_DB_PASSWORD", "env_password")

        try {
            // Environment variable should take precedence
            val dbPass = SecureConfiguration.getDatabasePassword(encryptedFormat)
            assertEquals("env_password", dbPass)

            // Without environment, use encrypted config
            System.clearProperty("MAIL_FACTORY_DB_PASSWORD")
            val dbPass2 = SecureConfiguration.getDatabasePassword(encryptedFormat)
            assertEquals(password, dbPass2)

        } finally {
            System.clearProperty("MAIL_FACTORY_MASTER_KEY")
            System.clearProperty("MAIL_FACTORY_DB_PASSWORD")
        }
    }

    // ========== Audit Logging Integration ==========

    @Test
    @DisplayName("Audit logging captures all event types")
    fun testAuditLoggingAllEventTypes() {
        AuditLogger.initialize()

        // Test all event types
        AuditLogger.logAuthentication("testuser", success = true, details = "SSH login")
        AuditLogger.logAuthorization("testuser", "/etc/passwd", allowed = false, details = "Access denied")
        AuditLogger.logConfigurationChange("admin", "mail.conf", "Updated SMTP settings")
        AuditLogger.logPrivilegedOperation(AuditAction.REBOOT, "System reboot", "admin", success = true)
        AuditLogger.logEncryption(AuditAction.ENCRYPT, "password", success = true)
        AuditLogger.logConnection(AuditAction.CONNECT, "192.168.1.100", "root", success = true)
        AuditLogger.logFileAccess(AuditAction.READ, "/etc/shadow", "root", success = true)
        AuditLogger.logCommandExecution("systemctl restart postfix", "admin", "mail.example.com", success = true)

        // Flush to disk
        AuditLogger.flush()

        // Verify audit log file created and contains entries
        val auditFiles = tempDir.listFiles { file ->
            file.name.startsWith("audit_") && file.name.endsWith(".log")
        }

        assertNotNull(auditFiles)
        assertTrue(auditFiles!!.isNotEmpty())

        // Get the most recently modified file (from this test)
        val auditFile = auditFiles.maxByOrNull { it.lastModified() }!!
        assertTrue(auditFile.length() > 0)

        // Read and verify content
        val content = auditFile.readText()
        assertTrue(content.contains("AUTHENTICATION"))
        assertTrue(content.contains("AUTHORIZATION"))
        assertTrue(content.contains("CONFIGURATION"))
        assertTrue(content.contains("PRIVILEGED_OPERATION"))
        assertTrue(content.contains("ENCRYPTION"))
        assertTrue(content.contains("CONNECTION"))
        assertTrue(content.contains("FILE_ACCESS"))
        assertTrue(content.contains("COMMAND_EXECUTION"))
    }

    @Test
    @DisplayName("Audit log JSON format validation")
    fun testAuditLogJsonFormat() {
        AuditLogger.initialize()

        AuditLogger.log(
            event = AuditEvent.AUTHENTICATION,
            action = AuditAction.LOGIN,
            details = "Test login with special chars: \"quotes\", \\backslash, \nnewline",
            result = AuditResult.SUCCESS,
            user = "testuser",
            resource = "192.168.1.1",
            metadata = mapOf("method" to "ssh", "key_type" to "rsa")
        )

        AuditLogger.flush()

        val auditFiles = tempDir.listFiles { file ->
            file.name.startsWith("audit_") && file.name.endsWith(".log")
        }

        assertNotNull(auditFiles)
        assertTrue(auditFiles!!.isNotEmpty())

        // Get the most recently modified file (from this test)
        val auditFile = auditFiles.maxByOrNull { it.lastModified() }!!
        val content = auditFile.readText()

        // Verify JSON format (should be parseable)
        assertTrue(content.contains("\"timestamp\""))
        assertTrue(content.contains("\"event\":\"AUTHENTICATION\""))
        assertTrue(content.contains("\"action\":\"LOGIN\""))
        assertTrue(content.contains("\"result\":\"SUCCESS\""))
        assertTrue(content.contains("\"user\":\"testuser\""))
        assertTrue(content.contains("\"resource\":\"192.168.1.1\""))
        assertTrue(content.contains("\"metadata\""))

        // Verify special characters escaped
        assertTrue(content.contains("\\\"quotes\\\""))
        assertTrue(content.contains("\\\\backslash"))
        assertTrue(content.contains("\\nnewline"))
    }

    // ========== Performance Tests ==========

    @Test
    @DisplayName("Encryption performance under load")
    fun testEncryptionPerformance() {
        val iterations = 100
        val password = "test_password_for_performance"

        val startTime = System.currentTimeMillis()

        repeat(iterations) {
            val encrypted = Encryption.encrypt(password, testMasterKey)
            val decrypted = Encryption.decrypt(encrypted, testMasterKey)
            assertEquals(password, decrypted)
        }

        val endTime = System.currentTimeMillis()
        val duration = endTime - startTime

        // Should complete 100 encrypt/decrypt cycles in < 15 seconds (allowing for CI/test overhead)
        assertTrue(duration < 15000, "Performance test took ${duration}ms, expected < 15000ms")

        println("Encryption performance: $iterations cycles in ${duration}ms (${duration / iterations}ms per cycle)")
    }

    @Test
    @DisplayName("Input validation performance under load")
    fun testInputValidationPerformance() {
        val iterations = 1000
        val testInputs = listOf(
            "example.com",
            "192.168.1.1",
            "user@example.com",
            "/home/user/file.txt",
            "username123"
        )

        val startTime = System.currentTimeMillis()

        repeat(iterations) {
            testInputs.forEach { input ->
                InputValidator.validateHost(input)
                InputValidator.validateEmail(input)
                InputValidator.validatePath(input, mustExist = false)
                InputValidator.validateUsername(input)
            }
        }

        val endTime = System.currentTimeMillis()
        val duration = endTime - startTime

        // Should complete 1000 iterations in < 5 seconds
        assertTrue(duration < 5000, "Validation performance test took ${duration}ms, expected < 5000ms")

        println("Validation performance: ${iterations * testInputs.size} validations in ${duration}ms")
    }

    @Test
    @DisplayName("Audit logging performance under load")
    fun testAuditLoggingPerformance() {
        AuditLogger.initialize()

        val iterations = 1000

        val startTime = System.currentTimeMillis()

        repeat(iterations) {
            AuditLogger.log(
                event = AuditEvent.COMMAND_EXECUTION,
                action = AuditAction.EXECUTE,
                details = "Performance test command $it",
                result = AuditResult.SUCCESS,
                user = "testuser",
                resource = "server$it"
            )
        }

        AuditLogger.flush()

        val endTime = System.currentTimeMillis()
        val duration = endTime - startTime

        // Should complete 1000 log entries in < 2 seconds
        assertTrue(duration < 2000, "Audit logging performance test took ${duration}ms, expected < 2000ms")

        println("Audit logging performance: $iterations entries in ${duration}ms (${duration.toDouble() / iterations}ms per entry)")
    }

    // ========== Edge Cases ==========

    @Test
    @DisplayName("Concurrent encryption operations")
    fun testConcurrentEncryption() {
        val threads = 10
        val iterationsPerThread = 10
        val password = "concurrent_test_password"

        val results = mutableListOf<String>()
        val errors = mutableListOf<Exception>()

        val threadList = (1..threads).map { threadId ->
            Thread {
                try {
                    repeat(iterationsPerThread) {
                        val encrypted = Encryption.encrypt(password, testMasterKey)
                        val decrypted = Encryption.decrypt(encrypted, testMasterKey)

                        synchronized(results) {
                            results.add(decrypted)
                        }
                    }
                } catch (e: Exception) {
                    synchronized(errors) {
                        errors.add(e)
                    }
                }
            }
        }

        threadList.forEach { it.start() }
        threadList.forEach { it.join() }

        // Verify no errors
        assertTrue(errors.isEmpty(), "Concurrent encryption errors: ${errors.joinToString()}")

        // Verify all decryptions successful
        assertEquals(threads * iterationsPerThread, results.size)
        results.forEach { result ->
            assertEquals(password, result)
        }
    }

    @Test
    @DisplayName("Very large password encryption")
    fun testVeryLargePasswordEncryption() {
        // 10KB password
        val largePassword = "A".repeat(10 * 1024)

        val encrypted = Encryption.encrypt(largePassword, testMasterKey)
        val decrypted = Encryption.decrypt(encrypted, testMasterKey)

        assertEquals(largePassword, decrypted)
        assertEquals(largePassword.length, decrypted.length)
    }

    @Test
    @DisplayName("Empty and whitespace-only validation")
    fun testEmptyAndWhitespaceValidation() {
        val emptyInputs = listOf("", "   ", "\t", "\n", "  \n  ")

        emptyInputs.forEach { input ->
            if (input.isNotEmpty()) {
                val result = InputValidator.validateHost(input)
                assertTrue(result.isFailed(), "Should fail for whitespace-only input: '$input'")
            }
        }
    }

    @Test
    @DisplayName("Unicode and special characters in passwords")
    fun testUnicodePasswordEncryption() {
        val unicodePasswords = listOf(
            "Pass世界🌍مرحباПривет",
            "Émojis🎉🎊🎈",
            "Spëcïål©härš®",
            "日本語パスワード"
        )

        unicodePasswords.forEach { password ->
            val encrypted = Encryption.encrypt(password, testMasterKey)
            val decrypted = Encryption.decrypt(encrypted, testMasterKey)
            assertEquals(password, decrypted)
        }
    }

    @Test
    @DisplayName("Audit log rotation and cleanup")
    fun testAuditLogRotation() {
        // Set very small max size to force rotation
        System.setProperty("MAIL_FACTORY_AUDIT_LOG_MAX_SIZE", "0") // Will rotate on each write

        AuditLogger.initialize()

        // Write multiple entries to force rotation
        repeat(5) { i ->
            AuditLogger.log(
                event = AuditEvent.SYSTEM,
                action = AuditAction.CREATE,
                details = "Rotation test entry $i with lots of data to increase file size",
                result = AuditResult.SUCCESS
            )
            AuditLogger.flush()
            Thread.sleep(100) // Small delay to ensure different timestamps
        }

        // Verify multiple log files created
        val auditFiles = tempDir.listFiles { file ->
            file.name.startsWith("audit_") && file.name.endsWith(".log")
        }

        assertNotNull(auditFiles)
        // Should have created at least 2 files due to rotation
        assertTrue(auditFiles!!.size >= 2, "Expected at least 2 files, got ${auditFiles.size}")

        System.clearProperty("MAIL_FACTORY_AUDIT_LOG_MAX_SIZE")
    }

    // ========== Error Recovery ==========

    @Test
    @DisplayName("Graceful handling of missing master key")
    fun testMissingMasterKeyHandling() {
        System.clearProperty("MAIL_FACTORY_MASTER_KEY")

        assertThrows<SecureConfigurationException> {
            SecureConfiguration.getMasterKey()
        }
    }

    @Test
    @DisplayName("Validation with null and invalid types")
    fun testValidationWithInvalidTypes() {
        // Port validation with invalid values
        assertThrows<NumberFormatException> {
            "invalid".toInt()
        }

        val invalidPort = 70000
        val result = InputValidator.validatePort(invalidPort)
        assertTrue(result.isFailed())
    }
}
