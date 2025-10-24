package net.milosvasic.factory.validation

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Unit tests for the InputValidator class.
 *
 * Tests cover:
 * - Hostname validation (valid, invalid, edge cases)
 * - IP address validation (IPv4, IPv6)
 * - Port validation (valid ranges, privileged ports)
 * - Username validation (length, characters)
 * - Email validation (valid, invalid formats)
 * - Path validation (traversal attacks, absolute/relative)
 * - Variable name validation
 * - Shell command sanitization
 * - Command validation (dangerous patterns)
 * - Type inference
 *
 * @author Mail Server Factory Team
 * @since 3.1.0
 */
@DisplayName("Input Validator Tests")
class InputValidatorTest {

    // ========== Hostname Validation Tests ==========

    @Test
    @DisplayName("Valid hostnames are accepted")
    fun testValidHostnames() {
        val validHostnames = listOf(
            "localhost",
            "example.com",
            "sub.example.com",
            "deep.sub.example.com",
            "host-with-dash.com",
            "123.456.789.012",
            "a.b.c.d.e.f.g.h",
            "xn--n3h.com" // IDN (punycode)
        )

        validHostnames.forEach { hostname ->
            val result = InputValidator.validateHost(hostname)
            assertTrue(result.isSuccess(), "Hostname should be valid: $hostname")
        }
    }

    @Test
    @DisplayName("Invalid hostnames are rejected")
    fun testInvalidHostnames() {
        val invalidHostnames = listOf(
            "",
            "-invalid.com",
            "invalid-.com",
            "in valid.com",
            "invalid..com",
            "toolong${"a".repeat(250)}.com",
            "special@char.com"
        )

        invalidHostnames.forEach { hostname ->
            if (hostname.isNotEmpty()) {
                val result = InputValidator.validateHost(hostname, allowLocalhost = false)
                assertTrue(result.isFailed(), "Hostname should be invalid: $hostname")
            }
        }
    }

    @Test
    @DisplayName("Valid IPv4 addresses are accepted")
    fun testValidIPv4() {
        val validIPs = listOf(
            "192.168.1.1",
            "10.0.0.1",
            "172.16.0.1",
            "8.8.8.8",
            "255.255.255.255",
            "0.0.0.0"
        )

        validIPs.forEach { ip ->
            val result = InputValidator.validateHost(ip)
            assertTrue(result.isSuccess(), "IPv4 should be valid: $ip")
        }
    }

    @Test
    @DisplayName("Invalid IPv4 addresses are rejected")
    fun testInvalidIPv4() {
        val invalidIPs = listOf(
            "256.1.1.1",
            "1.256.1.1",
            "1.1.256.1",
            "1.1.1.256",
            "999.999.999.999",
            "1.1.1",
            "1.1.1.1.1"
        )

        invalidIPs.forEach { ip ->
            val result = InputValidator.validateHost(ip)
            assertTrue(result.isFailed(), "IPv4 should be invalid: $ip")
        }
    }

    @Test
    @DisplayName("Valid IPv6 addresses are accepted")
    fun testValidIPv6() {
        val validIPs = listOf(
            "2001:0db8:85a3:0000:0000:8a2e:0370:7334",
            "2001:db8:85a3::8a2e:370:7334",
            "::1",
            "::",
            "fe80::1",
            "::ffff:192.0.2.1"
        )

        validIPs.forEach { ip ->
            val result = InputValidator.validateHost(ip)
            assertTrue(result.isSuccess(), "IPv6 should be valid: $ip")
        }
    }

    @Test
    @DisplayName("Localhost is allowed when configured")
    fun testLocalhostAllowed() {
        val result = InputValidator.validateHost("localhost", allowLocalhost = true)
        assertTrue(result.isSuccess())
    }

    @Test
    @DisplayName("Localhost is rejected when not allowed")
    fun testLocalhostNotAllowed() {
        val result = InputValidator.validateHost("localhost", allowLocalhost = false)
        assertTrue(result.isFailed())
    }

    // ========== Port Validation Tests ==========

    @Test
    @DisplayName("Valid ports are accepted")
    fun testValidPorts() {
        val validPorts = listOf(1, 80, 443, 3000, 8080, 22, 5432, 65535)

        validPorts.forEach { port ->
            val result = InputValidator.validatePort(port)
            assertTrue(result.isSuccess(), "Port should be valid: $port")
        }
    }

    @Test
    @DisplayName("Invalid ports are rejected")
    fun testInvalidPorts() {
        val invalidPorts = listOf(0, -1, -100, 65536, 70000, 100000)

        invalidPorts.forEach { port ->
            val result = InputValidator.validatePort(port)
            assertTrue(result.isFailed(), "Port should be invalid: $port")
        }
    }

    @Test
    @DisplayName("Privileged ports warn when not allowed")
    fun testPrivilegedPorts() {
        val privilegedPorts = listOf(22, 80, 443, 25, 587, 993)

        privilegedPorts.forEach { port ->
            val result = InputValidator.validatePort(port, allowPrivileged = false)
            assertTrue(result is ValidationResult.Warning, "Privileged port should warn: $port")
        }
    }

    @Test
    @DisplayName("Privileged ports accepted when allowed")
    fun testPrivilegedPortsAllowed() {
        val privilegedPorts = listOf(22, 80, 443, 25)

        privilegedPorts.forEach { port ->
            val result = InputValidator.validatePort(port, allowPrivileged = true)
            assertTrue(result is ValidationResult.Valid, "Privileged port should be valid when allowed: $port")
        }
    }

    // ========== Username Validation Tests ==========

    @Test
    @DisplayName("Valid usernames are accepted")
    fun testValidUsernames() {
        val validUsernames = listOf(
            "user",
            "admin",
            "test_user",
            "test-user",
            "user.name",
            "user123",
            "123user",
            "a1b2c3"
        )

        validUsernames.forEach { username ->
            val result = InputValidator.validateUsername(username)
            assertTrue(result.isSuccess(), "Username should be valid: $username")
        }
    }

    @Test
    @DisplayName("Invalid usernames are rejected")
    fun testInvalidUsernames() {
        val invalidUsernames = listOf(
            "",
            "ab",
            "a".repeat(33),
            "user space",
            "user@host",
            "user#123"
        )

        invalidUsernames.forEach { username ->
            if (username.isNotEmpty()) {
                val result = InputValidator.validateUsername(username)
                assertTrue(result.isFailed(), "Username should be invalid: $username")
            }
        }
    }

    @Test
    @DisplayName("Root user generates warning")
    fun testRootUserWarning() {
        val result = InputValidator.validateUsername("root")
        assertTrue(result is ValidationResult.Warning, "Root user should generate warning")
    }

    // ========== Email Validation Tests ==========

    @Test
    @DisplayName("Valid email addresses are accepted")
    fun testValidEmails() {
        val validEmails = listOf(
            "user@example.com",
            "test.user@example.com",
            "user+tag@example.com",
            "user_123@example.co.uk",
            "admin@sub.example.com",
            "123@example.com"
        )

        validEmails.forEach { email ->
            val result = InputValidator.validateEmail(email)
            assertTrue(result.isSuccess(), "Email should be valid: $email")
        }
    }

    @Test
    @DisplayName("Invalid email addresses are rejected")
    fun testInvalidEmails() {
        val invalidEmails = listOf(
            "",
            "notanemail",
            "@example.com",
            "user@",
            "user@@example.com",
            "user@example",
            "user @example.com",
            "user@exam ple.com",
            "a".repeat(255) + "@example.com"
        )

        invalidEmails.forEach { email ->
            if (email.isNotEmpty()) {
                val result = InputValidator.validateEmail(email)
                assertTrue(result.isFailed(), "Email should be invalid: $email")
            }
        }
    }

    // ========== Path Validation Tests ==========

    @Test
    @DisplayName("Valid paths are accepted")
    fun testValidPaths() {
        val validPaths = listOf(
            "/home/user/file.txt",
            "/var/log/app.log",
            "relative/path/file.txt",
            "file.txt"
        )

        validPaths.forEach { path ->
            val result = InputValidator.validatePath(path, mustExist = false)
            assertTrue(result.isSuccess(), "Path should be valid: $path")
        }
    }

    @Test
    @DisplayName("Path traversal attempts are rejected")
    fun testPathTraversalRejected() {
        val maliciousPaths = listOf(
            "../../../etc/passwd",
            "/var/log/../../../etc/shadow",
            "~/.ssh/id_rsa",
            "\${HOME}/.bashrc",
            "path/with/%20/encoded"
        )

        maliciousPaths.forEach { path ->
            val result = InputValidator.validatePath(path, mustExist = false)
            assertTrue(result.isFailed(), "Path traversal should be rejected: $path")
        }
    }

    @Test
    @DisplayName("Absolute paths rejected when not allowed")
    fun testAbsolutePathsNotAllowed() {
        val absolutePaths = listOf(
            "/home/user/file.txt",
            "/etc/passwd",
            "/var/log/app.log"
        )

        absolutePaths.forEach { path ->
            val result = InputValidator.validatePath(path, allowAbsolute = false)
            assertTrue(result.isFailed(), "Absolute path should be rejected: $path")
        }
    }

    @Test
    @DisplayName("Null byte in path rejected")
    fun testNullByteInPath() {
        val pathWithNull = "/home/user/file\u0000.txt"
        val result = InputValidator.validatePath(pathWithNull)
        assertTrue(result.isFailed(), "Path with null byte should be rejected")
    }

    // ========== Variable Name Validation Tests ==========

    @Test
    @DisplayName("Valid variable names are accepted")
    fun testValidVariableNames() {
        val validNames = listOf(
            "variable",
            "var_name",
            "VAR_NAME",
            "_private",
            "config.database.host",
            "user123"
        )

        validNames.forEach { name ->
            val result = InputValidator.validateVariableName(name)
            assertTrue(result.isSuccess(), "Variable name should be valid: $name")
        }
    }

    @Test
    @DisplayName("Invalid variable names are rejected")
    fun testInvalidVariableNames() {
        val invalidNames = listOf(
            "",
            "123invalid",
            "var-name",
            "var name",
            "var@name",
            "a".repeat(129)
        )

        invalidNames.forEach { name ->
            if (name.isNotEmpty()) {
                val result = InputValidator.validateVariableName(name)
                assertTrue(result.isFailed(), "Variable name should be invalid: $name")
            }
        }
    }

    // ========== Shell Sanitization Tests ==========

    @Test
    @DisplayName("Shell dangerous characters are removed")
    fun testShellSanitization() {
        val dangerous = "command; rm -rf /"
        val sanitized = InputValidator.sanitizeForShell(dangerous)

        // Sanitized should not contain dangerous characters
        assertFalse(sanitized.contains(";"))
        assertFalse(sanitized.contains("&"))
        assertFalse(sanitized.contains("|"))
        assertFalse(sanitized.contains("`"))
        assertFalse(sanitized.contains("$"))
    }

    @Test
    @DisplayName("Sanitized strings are single-quoted")
    fun testShellSanitizationQuoting() {
        val input = "safe text"
        val sanitized = InputValidator.sanitizeForShell(input)

        assertTrue(sanitized.startsWith("'"))
        assertTrue(sanitized.endsWith("'"))
    }

    @Test
    @DisplayName("Single quotes are escaped in sanitization")
    fun testShellSanitizationEscapesQuotes() {
        val input = "text with 'quotes'"
        val sanitized = InputValidator.sanitizeForShell(input)

        // Single quotes should be escaped
        assertTrue(sanitized.contains("\\'"))
    }

    // ========== Command Validation Tests ==========

    @Test
    @DisplayName("Safe commands pass validation")
    fun testSafeCommands() {
        val safeCommands = listOf(
            "ls -la",
            "echo hello",
            "cat file.txt"
        )

        safeCommands.forEach { command ->
            val result = InputValidator.validateCommand(command)
            assertTrue(result is ValidationResult.Valid, "Safe command should pass: $command")
        }
    }

    @Test
    @DisplayName("Dangerous commands generate warnings")
    fun testDangerousCommands() {
        val dangerousCommands = mapOf(
            "rm -rf /" to "rm -rf",
            "sudo reboot" to "privilege escalation",
            "echo test > file.txt" to "output redirection",
            "ls | grep test" to "pipe"
        )

        dangerousCommands.forEach { (command, expectedWarning) ->
            val result = InputValidator.validateCommand(command)
            assertTrue(result is ValidationResult.Warning, "Dangerous command should warn: $command")
        }
    }

    // ========== Type Inference Tests ==========

    @Test
    @DisplayName("IPv4 addresses are inferred correctly")
    fun testInferIPv4() {
        val (type, result) = InputValidator.inferAndValidate("192.168.1.1")
        assertEquals(VariableType.IP_ADDRESS, type)
        assertTrue(result.isSuccess())
    }

    @Test
    @DisplayName("Hostnames are inferred correctly")
    fun testInferHostname() {
        val (type, result) = InputValidator.inferAndValidate("example.com")
        assertEquals(VariableType.HOSTNAME, type)
        assertTrue(result.isSuccess())
    }

    @Test
    @DisplayName("Email addresses are inferred correctly")
    fun testInferEmail() {
        val (type, result) = InputValidator.inferAndValidate("user@example.com")
        assertEquals(VariableType.EMAIL, type)
        assertTrue(result.isSuccess())
    }

    @Test
    @DisplayName("Integers are inferred correctly")
    fun testInferInteger() {
        val (type, result) = InputValidator.inferAndValidate("12345")
        assertEquals(VariableType.INTEGER, type)
        assertTrue(result.isSuccess())
    }

    @Test
    @DisplayName("Booleans are inferred correctly")
    fun testInferBoolean() {
        listOf("true", "false").forEach { value ->
            val (type, result) = InputValidator.inferAndValidate(value)
            assertEquals(VariableType.BOOLEAN, type)
            assertTrue(result.isSuccess())
        }
    }

    @Test
    @DisplayName("Paths are inferred correctly")
    fun testInferPath() {
        val (type, result) = InputValidator.inferAndValidate("/home/user/file.txt")
        assertEquals(VariableType.PATH, type)
        // Result may vary based on path existence
    }

    @Test
    @DisplayName("Generic strings are inferred correctly")
    fun testInferString() {
        val (type, result) = InputValidator.inferAndValidate("just a string")
        assertEquals(VariableType.STRING, type)
        assertTrue(result.isSuccess())
    }

    // ========== ValidationResult Tests ==========

    @Test
    @DisplayName("ValidationResult.Valid is success")
    fun testValidIsSuccess() {
        val result = ValidationResult.Valid
        assertTrue(result.isSuccess())
        assertFalse(result.isFailed())
    }

    @Test
    @DisplayName("ValidationResult.Invalid is failed")
    fun testInvalidIsFailed() {
        val result = ValidationResult.Invalid("test reason")
        assertTrue(result.isFailed())
        assertFalse(result.isSuccess())
    }

    @Test
    @DisplayName("ValidationResult.Warning is success with message")
    fun testWarningIsSuccess() {
        val result = ValidationResult.Warning("test warning")
        assertTrue(result.isSuccess())
        assertFalse(result.isFailed())
    }

    // ========== Edge Cases ==========

    @Test
    @DisplayName("Empty hostname fails validation")
    fun testEmptyHostname() {
        val result = InputValidator.validateHost("")
        assertTrue(result.isFailed())
    }

    @Test
    @DisplayName("Empty username fails validation")
    fun testEmptyUsername() {
        val result = InputValidator.validateUsername("")
        assertTrue(result.isFailed())
    }

    @Test
    @DisplayName("Empty email fails validation")
    fun testEmptyEmail() {
        val result = InputValidator.validateEmail("")
        assertTrue(result.isFailed())
    }

    @Test
    @DisplayName("Empty path fails validation")
    fun testEmptyPath() {
        val result = InputValidator.validatePath("")
        assertTrue(result.isFailed())
    }

    @Test
    @DisplayName("Empty variable name fails validation")
    fun testEmptyVariableName() {
        val result = InputValidator.validateVariableName("")
        assertTrue(result.isFailed())
    }
}
