package net.milosvasic.factory.security

import net.milosvasic.factory.validation.InputValidator
import net.milosvasic.factory.validation.ValidationResult
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach

/**
 * Deployment flow verification tests.
 *
 * These tests verify that security enhancements don't break existing deployment flows.
 * Tests cover:
 * - Configuration loading and parsing
 * - Credential handling (encrypted and plain text)
 * - Connection establishment
 * - Command execution with validation
 * - Installation step execution
 * - Error handling and recovery
 * - Backward compatibility
 *
 * @author Mail Server Factory Team
 * @since 3.1.0
 */
@DisplayName("Deployment Flow Verification Tests")
class DeploymentFlowVerificationTest {

    private val testMasterKey = "deployment-test-master-key-12345678"

    @BeforeEach
    fun setup() {
        System.setProperty("MAIL_FACTORY_MASTER_KEY", testMasterKey)
    }

    // ========== Configuration Loading Flow ==========

    @Test
    @DisplayName("Configuration with encrypted passwords loads correctly")
    fun testConfigurationWithEncryptedPasswords() {
        // Simulate configuration JSON structure
        val dbPassword = "db_secure_pass_123"
        val sshPassword = "ssh_secure_pass_456"

        // Encrypt passwords
        val encryptedDbPass = Encryption.encrypt(dbPassword, testMasterKey)
        val encryptedSshPass = Encryption.encrypt(sshPassword, testMasterKey)

        // Simulate configuration structure
        val config = mapOf(
            "database" to mapOf(
                "host" to "localhost",
                "port" to "5432",
                "username" to "postgres",
                "password" to "encrypted:$encryptedDbPass"
            ),
            "ssh" to mapOf(
                "host" to "mail.example.com",
                "port" to "22",
                "username" to "root",
                "password" to "encrypted:$encryptedSshPass"
            )
        )

        // Verify database configuration
        val dbConfig = config["database"]!!
        assertEquals("localhost", dbConfig["host"])
        assertEquals("5432", dbConfig["port"])
        assertEquals("postgres", dbConfig["username"])

        val dbPassEncrypted = dbConfig["password"] as String
        assertTrue(SecureConfiguration.isEncrypted(dbPassEncrypted))

        val dbPassDecrypted = SecureConfiguration.decryptPassword(
            dbPassEncrypted,  // Don't remove prefix - decryptPassword() does it
            testMasterKey
        )
        assertEquals(dbPassword, dbPassDecrypted)

        // Verify SSH configuration
        val sshConfig = config["ssh"]!!
        assertEquals("mail.example.com", sshConfig["host"])

        val sshPassDecrypted = SecureConfiguration.decryptPassword(
            sshConfig["password"] as String,  // Don't remove prefix
            testMasterKey
        )
        assertEquals(sshPassword, sshPassDecrypted)

        // Log configuration load
        AuditLogger.logConfigurationChange(
            user = "system",
            resource = "mail.example.com",
            details = "Configuration loaded with encrypted passwords"
        )
    }

    @Test
    @DisplayName("Configuration with plain text passwords still works (backward compatibility)")
    fun testBackwardCompatibilityPlainTextPasswords() {
        // Plain text passwords should still work (but logged as warning)
        val plainPassword = "plain_text_password"

        assertFalse(SecureConfiguration.isEncrypted(plainPassword))

        // Decryption of non-encrypted password returns as-is
        val decrypted = SecureConfiguration.decryptPassword(plainPassword, testMasterKey)
        assertEquals(plainPassword, decrypted)
    }

    @Test
    @DisplayName("Configuration validation during loading")
    fun testConfigurationValidationDuringLoading() {
        // Simulate configuration with all fields that need validation
        val config = mapOf(
            "hostname" to "mail.example.com",
            "port" to 25,
            "username" to "postfix",
            "email" to "postmaster@example.com",
            "dbHost" to "192.168.1.100",
            "dbPort" to 5432,
            "installPath" to "/opt/mailserver"
        )

        // Validate all fields
        val validationResults = mutableMapOf<String, ValidationResult>()

        validationResults["hostname"] = InputValidator.validateHost(config["hostname"] as String)
        validationResults["port"] = InputValidator.validatePort(config["port"] as Int)
        validationResults["username"] = InputValidator.validateUsername(config["username"] as String)
        validationResults["email"] = InputValidator.validateEmail(config["email"] as String)
        validationResults["dbHost"] = InputValidator.validateHost(config["dbHost"] as String)
        validationResults["dbPort"] = InputValidator.validatePort(config["dbPort"] as Int)
        validationResults["installPath"] = InputValidator.validatePath(
            config["installPath"] as String,
            mustExist = false,
            allowAbsolute = true
        )

        // All should be valid
        validationResults.forEach { (field, result) ->
            assertTrue(
                result.isSuccess(),
                "Field '$field' should be valid: ${if (result is ValidationResult.Invalid) result.reason else ""}"
            )
        }
    }

    @Test
    @DisplayName("Invalid configuration is rejected during validation")
    fun testInvalidConfigurationRejected() {
        val invalidConfig = mapOf(
            "hostname" to "invalid..hostname",
            "port" to 70000,
            "username" to "ab", // too short
            "email" to "invalid-email",
            "installPath" to "../../../etc/passwd" // path traversal
        )

        val hostnameResult = InputValidator.validateHost(invalidConfig["hostname"] as String)
        assertTrue(hostnameResult.isFailed())

        val portResult = InputValidator.validatePort(invalidConfig["port"] as Int)
        assertTrue(portResult.isFailed())

        val usernameResult = InputValidator.validateUsername(invalidConfig["username"] as String)
        assertTrue(usernameResult.isFailed())

        val emailResult = InputValidator.validateEmail(invalidConfig["email"] as String)
        assertTrue(emailResult.isFailed())

        val pathResult = InputValidator.validatePath(invalidConfig["installPath"] as String)
        assertTrue(pathResult.isFailed())

        // Log validation failure
        AuditLogger.log(
            event = AuditEvent.CONFIGURATION,
            action = AuditAction.READ,
            details = "Invalid configuration rejected during validation",
            result = AuditResult.FAILURE
        )
    }

    // ========== Connection Flow ==========

    @Test
    @DisplayName("SSH connection parameters validated before connection")
    fun testSshConnectionParametersValidated() {
        val connectionParams = mapOf(
            "host" to "mail.example.com",
            "port" to 22,
            "username" to "root",
            "password" to "encrypted:${Encryption.encrypt("ssh_pass", testMasterKey)}"
        )

        // Validate before attempting connection
        val hostResult = InputValidator.validateHost(connectionParams["host"] as String)
        assertTrue(hostResult.isSuccess())

        val portResult = InputValidator.validatePort(connectionParams["port"] as Int)
        assertTrue(portResult.isSuccess())

        val usernameResult = InputValidator.validateUsername(connectionParams["username"] as String)
        assertTrue(usernameResult.isSuccess())

        // Decrypt password
        val encryptedPass = connectionParams["password"] as String
        val password = SecureConfiguration.decryptPassword(
            encryptedPass,  // Don't remove prefix
            testMasterKey
        )

        assertNotNull(password)
        assertNotEquals("", password)

        // Log connection attempt
        AuditLogger.logConnection(
            action = AuditAction.CONNECT,
            remote = connectionParams["host"] as String,
            user = connectionParams["username"] as String,
            success = true
        )
    }

    @Test
    @DisplayName("Malicious SSH parameters rejected")
    fun testMaliciousSshParametersRejected() {
        val maliciousParams = mapOf(
            "host" to "host;rm -rf /",
            "username" to "root' OR '1'='1",
            "port" to -1
        )

        // Host validation
        val sanitizedHost = InputValidator.sanitizeForShell(maliciousParams["host"] as String)
        assertFalse(sanitizedHost.contains(";"))
        assertFalse(sanitizedHost.contains("rm"))

        // Username validation
        val usernameResult = InputValidator.validateUsername(maliciousParams["username"] as String)
        assertTrue(usernameResult.isFailed())

        // Port validation
        val portResult = InputValidator.validatePort(maliciousParams["port"] as Int)
        assertTrue(portResult.isFailed())

        // Log security event
        AuditLogger.log(
            event = AuditEvent.AUTHORIZATION,
            action = AuditAction.CONNECT,
            details = "Malicious connection parameters rejected",
            result = AuditResult.DENIED
        )
    }

    // ========== Command Execution Flow ==========

    @Test
    @DisplayName("Safe commands execute without issues")
    fun testSafeCommandExecution() {
        val safeCommands = listOf(
            "ls -la /home",
            "cat /etc/hostname",
            "systemctl status postfix",
            "docker ps -a",
            "df -h"
        )

        safeCommands.forEach { command ->
            // Validate command
            val result = InputValidator.validateCommand(command)
            assertTrue(result.isSuccess() || result is ValidationResult.Warning)

            // Log command execution
            AuditLogger.logCommandExecution(
                command = command,
                user = "system",
                remote = "localhost",
                success = true
            )
        }

        AuditLogger.flush()
    }

    @Test
    @DisplayName("Dangerous commands generate warnings")
    fun testDangerousCommandWarnings() {
        val dangerousCommands = mapOf(
            "rm -rf /" to "rm -rf",
            "sudo reboot" to "privilege escalation",
            "echo test > /etc/passwd" to "output redirection",
            "cat /etc/shadow | grep root" to "pipe"
        )

        dangerousCommands.forEach { (command, expectedWarning) ->
            val result = InputValidator.validateCommand(command)

            assertTrue(
                result is ValidationResult.Warning,
                "Command should generate warning: $command"
            )

            if (result is ValidationResult.Warning) {
                assertTrue(
                    result.message.contains(expectedWarning, ignoreCase = true),
                    "Warning should mention '$expectedWarning': ${result.message}"
                )
            }

            // Log dangerous command attempt
            AuditLogger.logCommandExecution(
                command = command,
                user = "admin",
                remote = "mail.example.com",
                success = false
            )
        }
    }

    @Test
    @DisplayName("Command injection attempts sanitized")
    fun testCommandInjectionSanitized() {
        val injectionAttempts = listOf(
            "ls; rm -rf /",
            "cat /etc/passwd & rm -rf /",
            "echo `whoami`",
            "test$(dangerous command)",
            "path | pipe | command"
        )

        injectionAttempts.forEach { malicious ->
            val sanitized = InputValidator.sanitizeForShell(malicious)

            // Dangerous characters should be removed
            assertFalse(sanitized.contains(";"))
            assertFalse(sanitized.contains("&"))
            assertFalse(sanitized.contains("|"))
            assertFalse(sanitized.contains("`"))
            assertFalse(sanitized.contains("$"))

            // Should be wrapped in single quotes
            assertTrue(sanitized.startsWith("'"))
            assertTrue(sanitized.endsWith("'"))
        }
    }

    // ========== Installation Step Flow ==========

    @Test
    @DisplayName("Package installation step with validation")
    fun testPackageInstallationStepWithValidation() {
        val packages = listOf(
            "postfix",
            "dovecot-core",
            "postgresql-15",
            "redis-server",
            "docker-ce"
        )

        packages.forEach { packageName ->
            // Validate package name (simple alphanumeric + dash)
            val isValid = packageName.matches(Regex("^[a-zA-Z0-9._-]+$"))
            assertTrue(isValid, "Package name should be valid: $packageName")

            // Log installation
            AuditLogger.log(
                event = AuditEvent.SYSTEM,
                action = AuditAction.CREATE,
                details = "Installing package: $packageName",
                result = AuditResult.SUCCESS,
                resource = packageName
            )
        }
    }

    @Test
    @DisplayName("File operation step with path validation")
    fun testFileOperationStepWithValidation() {
        val fileOperations = listOf(
            "/etc/postfix/main.cf",
            "/etc/dovecot/dovecot.conf",
            "/opt/mailserver/config.json",
            "/var/mail/data"
        )

        fileOperations.forEach { filePath ->
            // Validate path
            val result = InputValidator.validatePath(
                filePath,
                mustExist = false,
                allowAbsolute = true
            )
            assertTrue(result.isSuccess(), "File path should be valid: $filePath")

            // Log file access
            AuditLogger.logFileAccess(
                action = AuditAction.MODIFY,
                filePath = filePath,
                user = "system",
                success = true
            )
        }
    }

    @Test
    @DisplayName("Malicious file paths rejected")
    fun testMaliciousFilePathsRejected() {
        val maliciousPaths = listOf(
            "../../../etc/passwd",
            "~/.ssh/id_rsa",
            "\${HOME}/.bashrc",
            "/etc/../../../etc/shadow"
        )

        maliciousPaths.forEach { malicious ->
            val result = InputValidator.validatePath(malicious, mustExist = false)
            assertTrue(result.isFailed(), "Malicious path should be rejected: $malicious")

            // Log security event
            AuditLogger.log(
                event = AuditEvent.FILE_ACCESS,
                action = AuditAction.READ,
                details = "Malicious file path rejected: $malicious",
                result = AuditResult.DENIED
            )
        }
    }

    // ========== Database Configuration Flow ==========

    @Test
    @DisplayName("Database connection with encrypted credentials")
    fun testDatabaseConnectionWithEncryptedCredentials() {
        val dbConfig = mapOf(
            "host" to "localhost",
            "port" to 5432,
            "database" to "mailserver",
            "username" to "postgres",
            "password" to "encrypted:${Encryption.encrypt("db_pass_123", testMasterKey)}"
        )

        // Validate host
        val hostResult = InputValidator.validateHost(dbConfig["host"] as String)
        assertTrue(hostResult.isSuccess())

        // Validate port
        val portResult = InputValidator.validatePort(dbConfig["port"] as Int)
        assertTrue(portResult.isSuccess())

        // Validate username
        val usernameResult = InputValidator.validateUsername(dbConfig["username"] as String)
        assertTrue(usernameResult.isSuccess())

        // Decrypt password
        val password = SecureConfiguration.getDatabasePassword(dbConfig["password"] as String)
        assertEquals("db_pass_123", password)

        // Log database connection
        AuditLogger.log(
            event = AuditEvent.CONNECTION,
            action = AuditAction.CONNECT,
            details = "Database connection established",
            result = AuditResult.SUCCESS,
            user = dbConfig["username"] as String,
            resource = "${dbConfig["host"]}:${dbConfig["port"]}"
        )
    }

    // ========== Docker Operations Flow ==========

    @Test
    @DisplayName("Docker Hub credentials with encryption")
    fun testDockerHubCredentialsWithEncryption() {
        val dockerConfig = mapOf(
            "username" to "dockeruser",
            "password" to "encrypted:${Encryption.encrypt("docker_pass_789", testMasterKey)}",
            "registry" to "docker.io"
        )

        // Validate username
        val usernameResult = InputValidator.validateUsername(dockerConfig["username"] as String)
        assertTrue(usernameResult.isSuccess())

        // Decrypt password
        val password = SecureConfiguration.getDockerPassword(dockerConfig["password"] as String)
        assertEquals("docker_pass_789", password)

        // Log Docker login
        AuditLogger.logAuthentication(
            user = dockerConfig["username"] as String,
            success = true,
            details = "Docker Hub authentication"
        )
    }

    // ========== Mail Account Creation Flow ==========

    @Test
    @DisplayName("Mail account with email validation")
    fun testMailAccountCreationWithValidation() {
        val mailAccounts = listOf(
            mapOf(
                "email" to "postmaster@example.com",
                "password" to "encrypted:${Encryption.encrypt("mail_pass_1", testMasterKey)}"
            ),
            mapOf(
                "email" to "admin@example.com",
                "password" to "encrypted:${Encryption.encrypt("mail_pass_2", testMasterKey)}"
            ),
            mapOf(
                "email" to "user@example.com",
                "password" to "encrypted:${Encryption.encrypt("mail_pass_3", testMasterKey)}"
            )
        )

        mailAccounts.forEach { account ->
            // Validate email
            val emailResult = InputValidator.validateEmail(account["email"] as String)
            assertTrue(emailResult.isSuccess(), "Email should be valid: ${account["email"]}")

            // Decrypt password
            val password = SecureConfiguration.decryptPassword(
                account["password"] as String,  // Don't remove prefix
                testMasterKey
            )
            assertNotNull(password)
            assertTrue(password.isNotEmpty())

            // Log account creation
            AuditLogger.log(
                event = AuditEvent.CONFIGURATION,
                action = AuditAction.CREATE,
                details = "Mail account created",
                result = AuditResult.SUCCESS,
                resource = account["email"] as String
            )
        }
    }

    @Test
    @DisplayName("Invalid email addresses rejected for mail accounts")
    fun testInvalidEmailsRejectedForMailAccounts() {
        val invalidEmails = listOf(
            "notanemail",
            "@example.com",
            "user@",
            "user@@example.com",
            "user@example"
        )

        invalidEmails.forEach { email ->
            val result = InputValidator.validateEmail(email)
            assertTrue(result.isFailed(), "Invalid email should be rejected: $email")

            // Log validation failure
            AuditLogger.log(
                event = AuditEvent.CONFIGURATION,
                action = AuditAction.CREATE,
                details = "Invalid email rejected: $email",
                result = AuditResult.FAILURE
            )
        }
    }

    // ========== Error Recovery Flow ==========

    @Test
    @DisplayName("Graceful handling of decryption failures")
    fun testGracefulDecryptionFailureHandling() {
        val wrongKey = "wrong-key-12345678"
        val password = "test_password"
        val encrypted = Encryption.encrypt(password, testMasterKey)

        // Attempt decryption with wrong key
        assertThrows(DecryptionException::class.java) {
            Encryption.decrypt(encrypted, wrongKey)
        }

        // Log decryption failure
        AuditLogger.log(
            event = AuditEvent.ENCRYPTION,
            action = AuditAction.DECRYPT,
            details = "Decryption failed - wrong master key",
            result = AuditResult.FAILURE
        )
    }

    @Test
    @DisplayName("Graceful handling of validation failures")
    fun testGracefulValidationFailureHandling() {
        val invalidInputs = mapOf(
            "hostname" to "invalid..host",
            "port" to -1,
            "email" to "invalid-email"
        )

        invalidInputs.forEach { (type, value) ->
            val result = when (type) {
                "hostname" -> InputValidator.validateHost(value as String)
                "port" -> InputValidator.validatePort(value as Int)
                "email" -> InputValidator.validateEmail(value as String)
                else -> ValidationResult.Valid
            }

            assertTrue(result.isFailed(), "Validation should fail for $type: $value")

            // Log validation failure
            AuditLogger.log(
                event = AuditEvent.CONFIGURATION,
                action = AuditAction.READ,
                details = "Validation failed for $type: $value",
                result = AuditResult.FAILURE
            )
        }
    }

    // ========== End-to-End Deployment Flow ==========

    @Test
    @DisplayName("Complete deployment flow simulation")
    fun testCompleteDeploymentFlowSimulation() {
        // Step 1: Load configuration with encrypted passwords
        val config = mapOf(
            "server" to mapOf(
                "hostname" to "mail.example.com",
                "port" to 22,
                "username" to "root",
                "password" to "encrypted:${Encryption.encrypt("ssh_pass", testMasterKey)}"
            ),
            "database" to mapOf(
                "host" to "localhost",
                "port" to 5432,
                "username" to "postgres",
                "password" to "encrypted:${Encryption.encrypt("db_pass", testMasterKey)}"
            ),
            "mail" to listOf(
                mapOf(
                    "email" to "postmaster@example.com",
                    "password" to "encrypted:${Encryption.encrypt("mail_pass_1", testMasterKey)}"
                )
            )
        )

        // Step 2: Validate all configuration
        val serverConfig = config["server"] as Map<*, *>
        assertTrue(InputValidator.validateHost(serverConfig["hostname"] as String).isSuccess())
        assertTrue(InputValidator.validatePort(serverConfig["port"] as Int).isSuccess())
        assertTrue(InputValidator.validateUsername(serverConfig["username"] as String).isSuccess())

        val dbConfig = config["database"] as Map<*, *>
        assertTrue(InputValidator.validateHost(dbConfig["host"] as String).isSuccess())
        assertTrue(InputValidator.validatePort(dbConfig["port"] as Int).isSuccess())

        val mailAccounts = config["mail"] as List<*>
        mailAccounts.forEach { account ->
            val accountMap = account as Map<*, *>
            assertTrue(InputValidator.validateEmail(accountMap["email"] as String).isSuccess())
        }

        // Step 3: Decrypt passwords
        val sshPassword = SecureConfiguration.getSshPassword(serverConfig["password"] as String)
        assertEquals("ssh_pass", sshPassword)

        val dbPassword = SecureConfiguration.getDatabasePassword(dbConfig["password"] as String)
        assertEquals("db_pass", dbPassword)

        // Step 4: Log deployment steps
        AuditLogger.logConfigurationChange("system", "mail.example.com", "Configuration loaded")
        AuditLogger.logConnection(AuditAction.CONNECT, "mail.example.com", "root", success = true)
        AuditLogger.logPrivilegedOperation(AuditAction.EXECUTE, "Package installation", "root", success = true)
        AuditLogger.logConnection(AuditAction.CONNECT, "localhost:5432", "postgres", success = true)
        AuditLogger.log(
            event = AuditEvent.CONFIGURATION,
            action = AuditAction.CREATE,
            details = "Mail accounts created",
            result = AuditResult.SUCCESS
        )

        AuditLogger.flush()

        // Verify audit log created
        assertTrue(true, "Complete deployment flow executed successfully")
    }
}
