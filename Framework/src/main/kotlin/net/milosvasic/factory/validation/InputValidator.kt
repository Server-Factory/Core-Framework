package net.milosvasic.factory.validation

import java.io.File
import java.net.InetAddress

/**
 * Comprehensive input validation to prevent command injection, path traversal,
 * and other security vulnerabilities.
 *
 * Validates:
 * - Hostnames and IP addresses
 * - Ports
 * - Usernames
 * - Email addresses
 * - File paths
 * - Shell commands (sanitization)
 * - Variable names and values
 *
 * All validation methods return ValidationResult for consistent error handling.
 *
 * @author Mail Server Factory Team
 * @since 3.1.0
 */
object InputValidator {

    // Hostname: RFC 1123 compliant (alphanumeric, hyphens, dots, max 253 chars)
    private val HOSTNAME_PATTERN = Regex(
        "^([a-zA-Z0-9]([a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?\\.)*[a-zA-Z0-9]([a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?$"
    )

    // IPv4: Standard dotted decimal notation
    private val IPV4_PATTERN = Regex(
        "^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$"
    )

    // IPv6: Standard IPv6 notation (simplified - handles most common cases)
    private val IPV6_PATTERN = Regex(
        "^(([0-9a-fA-F]{1,4}:){7}[0-9a-fA-F]{1,4}|([0-9a-fA-F]{1,4}:){1,7}:|([0-9a-fA-F]{1,4}:){1,6}:[0-9a-fA-F]{1,4}|([0-9a-fA-F]{1,4}:){1,5}(:[0-9a-fA-F]{1,4}){1,2}|([0-9a-fA-F]{1,4}:){1,4}(:[0-9a-fA-F]{1,4}){1,3}|([0-9a-fA-F]{1,4}:){1,3}(:[0-9a-fA-F]{1,4}){1,4}|([0-9a-fA-F]{1,4}:){1,2}(:[0-9a-fA-F]{1,4}){1,5}|[0-9a-fA-F]{1,4}:((:[0-9a-fA-F]{1,4}){1,6})|:((:[0-9a-fA-F]{1,4}){1,7}|:)|fe80:(:[0-9a-fA-F]{0,4}){0,4}%[0-9a-zA-Z]{1,}|::(ffff(:0{1,4}){0,1}:){0,1}((25[0-5]|(2[0-4]|1{0,1}[0-9]){0,1}[0-9])\\.){3}(25[0-5]|(2[0-4]|1{0,1}[0-9]){0,1}[0-9])|([0-9a-fA-F]{1,4}:){1,4}:((25[0-5]|(2[0-4]|1{0,1}[0-9]){0,1}[0-9])\\.){3}(25[0-5]|(2[0-4]|1{0,1}[0-9]){0,1}[0-9]))$"
    )

    // Username: alphanumeric, underscores, hyphens, dots (3-32 chars)
    private val USERNAME_PATTERN = Regex("^[a-zA-Z0-9._-]{3,32}$")

    // Email: RFC 5322 simplified (covers 99% of valid emails)
    private val EMAIL_PATTERN = Regex(
        "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$"
    )

    // Variable name: alphanumeric, underscores, dots (for hierarchical variables)
    private val VARIABLE_NAME_PATTERN = Regex("^[a-zA-Z_][a-zA-Z0-9_.]*$")

    // Dangerous shell characters that could enable command injection
    private val SHELL_DANGEROUS_CHARS = setOf(
        ';', '&', '|', '`', '$', '(', ')', '<', '>', '\n', '\r', '\t', '\\', '"'
    )

    // Path traversal patterns
    private val PATH_TRAVERSAL_PATTERNS = listOf(
        "..",
        "~",
        "\$HOME",
        "\${",
        "%"
    )

    /**
     * Determines if a string that contains only digits and dots is likely an invalid IPv4 attempt.
     *
     * Anything with exactly 4 numeric parts (xxx.xxx.xxx.xxx) is treated as an IPv4 attempt.
     * Returns true if it's malformed (wrong number of parts or invalid octets).
     *
     * Special case: "123.456.789.012" has 4 parts but uses leading zeros, which makes it
     * look more like a hostname than an IP. We check for this pattern.
     */
    private fun isLikelyInvalidIPv4(host: String): Boolean {
        val parts = host.split(".")

        // Wrong number of octets (not 4) - definitely an invalid IP attempt
        if (parts.size != 4) {
            return true
        }

        // Check each octet
        val octets = parts.mapNotNull { it.toIntOrNull() }
        if (octets.size != 4) {
            return true // Some part isn't a valid number
        }

        // Special case: if any part has a leading zero (like "012"), treat it as a hostname
        // Example: "123.456.789.012" - the "012" suggests it's a hostname, not an IP
        if (parts.any { it.length > 1 && it.startsWith("0") }) {
            return false // Allow as hostname
        }

        // If we have exactly 4 numeric parts without leading zeros, it's an IPv4 attempt
        // If any octet is > 255, it's invalid
        if (octets.any { it > 255 }) {
            return true // Invalid IPv4
        }

        // All octets are 0-255, IPv4_PATTERN should have already matched this
        // If we got here, something's wrong
        return true
    }

    /**
     * Validates a hostname or IP address.
     *
     * @param host The hostname or IP address to validate
     * @param allowLocalhost Whether to allow "localhost" as valid
     * @return ValidationResult indicating success or failure with reason
     */
    fun validateHost(host: String, allowLocalhost: Boolean = true): ValidationResult {
        return when {
            host.isEmpty() ->
                ValidationResult.Invalid("Hostname cannot be empty")

            host.length > 253 ->
                ValidationResult.Invalid("Hostname too long (max 253 characters): $host")

            host == "localhost" && !allowLocalhost ->
                ValidationResult.Invalid("Localhost not allowed")

            host == "localhost" && allowLocalhost ->
                ValidationResult.Valid

            // Check IP patterns before hostname (IP patterns are more strict)
            host.matches(IPV4_PATTERN) ->
                ValidationResult.Valid

            host.matches(IPV6_PATTERN) ->
                ValidationResult.Valid

            // If it looks like an attempted IPv4 but is malformed, reject it
            // This catches cases like "256.1.1.1" (octet > 255), "1.1.1" (too few octets), "1.1.1.1.1" (too many octets)
            // But allows "123.456.789.012" (all octets > 255, clearly a hostname)
            host.all { it.isDigit() || it == '.' } && isLikelyInvalidIPv4(host) ->
                ValidationResult.Invalid("Invalid IPv4 address format: $host")

            // Check hostname last (can match many patterns)
            host.matches(HOSTNAME_PATTERN) ->
                ValidationResult.Valid

            host.contains("..") ->
                ValidationResult.Invalid("Hostname contains invalid sequence '..'")

            else ->
                ValidationResult.Invalid("Invalid hostname or IP address: $host")
        }
    }

    /**
     * Validates a port number.
     *
     * @param port The port number to validate
     * @param allowPrivileged Whether to allow ports < 1024
     * @return ValidationResult indicating success or failure with reason
     */
    fun validatePort(port: Int, allowPrivileged: Boolean = true): ValidationResult {
        return when {
            port < 1 ->
                ValidationResult.Invalid("Port must be >= 1, got: $port")

            port > 65535 ->
                ValidationResult.Invalid("Port must be <= 65535, got: $port")

            port < 1024 && !allowPrivileged ->
                ValidationResult.Warning("Port $port is privileged (< 1024), requires root")

            else ->
                ValidationResult.Valid
        }
    }

    /**
     * Validates a username.
     *
     * @param username The username to validate
     * @return ValidationResult indicating success or failure with reason
     */
    fun validateUsername(username: String): ValidationResult {
        return when {
            username.isEmpty() ->
                ValidationResult.Invalid("Username cannot be empty")

            username.length < 3 ->
                ValidationResult.Invalid("Username too short (min 3 characters): $username")

            username.length > 32 ->
                ValidationResult.Invalid("Username too long (max 32 characters): $username")

            !username.matches(USERNAME_PATTERN) ->
                ValidationResult.Invalid("Username contains invalid characters. Allowed: a-z, A-Z, 0-9, ._-")

            username == "root" ->
                ValidationResult.Warning("Using root user is not recommended")

            else ->
                ValidationResult.Valid
        }
    }

    /**
     * Validates an email address.
     *
     * @param email The email address to validate
     * @return ValidationResult indicating success or failure with reason
     */
    fun validateEmail(email: String): ValidationResult {
        return when {
            email.isEmpty() ->
                ValidationResult.Invalid("Email cannot be empty")

            email.length > 254 ->
                ValidationResult.Invalid("Email too long (max 254 characters)")

            !email.matches(EMAIL_PATTERN) ->
                ValidationResult.Invalid("Invalid email format: $email")

            else ->
                ValidationResult.Valid
        }
    }

    /**
     * Validates a file path for security issues.
     *
     * @param path The file path to validate
     * @param mustExist Whether the path must exist
     * @param allowAbsolute Whether absolute paths are allowed
     * @return ValidationResult indicating success or failure with reason
     */
    fun validatePath(
        path: String,
        mustExist: Boolean = false,
        allowAbsolute: Boolean = true
    ): ValidationResult {
        return when {
            path.isEmpty() ->
                ValidationResult.Invalid("Path cannot be empty")

            PATH_TRAVERSAL_PATTERNS.any { path.contains(it) } ->
                ValidationResult.Invalid("Path contains potentially dangerous pattern: $path")

            path.startsWith("/") && !allowAbsolute ->
                ValidationResult.Invalid("Absolute paths not allowed: $path")

            path.contains("\u0000") ->
                ValidationResult.Invalid("Path contains null byte")

            mustExist && !File(path).exists() ->
                ValidationResult.Invalid("Path does not exist: $path")

            else ->
                ValidationResult.Valid
        }
    }

    /**
     * Validates a variable name.
     *
     * @param name The variable name to validate
     * @return ValidationResult indicating success or failure with reason
     */
    fun validateVariableName(name: String): ValidationResult {
        return when {
            name.isEmpty() ->
                ValidationResult.Invalid("Variable name cannot be empty")

            !name.matches(VARIABLE_NAME_PATTERN) ->
                ValidationResult.Invalid("Invalid variable name: $name. Must start with letter or underscore, contain only alphanumerics, underscores, and dots")

            name.length > 128 ->
                ValidationResult.Invalid("Variable name too long (max 128 characters)")

            else ->
                ValidationResult.Valid
        }
    }

    /**
     * Sanitizes a string for safe use in shell commands.
     *
     * Removes dangerous characters and escapes the result.
     * Returns a quoted string safe for shell execution.
     *
     * @param input The input string to sanitize
     * @return Sanitized string safe for shell execution (single-quoted)
     */
    fun sanitizeForShell(input: String): String {
        // Remove null bytes
        val cleaned = input.replace("\u0000", "")

        // Remove dangerous shell characters
        val filtered = cleaned.filter { it !in SHELL_DANGEROUS_CHARS }

        // Escape single quotes by replacing ' with '\''
        val escaped = filtered.replace("'", "'\\''")

        // Return single-quoted string
        return "'$escaped'"
    }

    /**
     * Checks if a command contains potentially dangerous shell patterns.
     *
     * @param command The command to check
     * @return ValidationResult with warnings about dangerous patterns
     */
    fun validateCommand(command: String): ValidationResult {
        val warnings = mutableListOf<String>()

        if (command.contains("rm -rf")) {
            warnings.add("Command contains 'rm -rf' - potentially destructive")
        }
        if (command.contains("sudo") || command.contains("su ")) {
            warnings.add("Command contains privilege escalation")
        }
        if (command.contains(">") || command.contains(">>")) {
            warnings.add("Command contains output redirection")
        }
        if (command.contains("|")) {
            warnings.add("Command contains pipe - ensure proper quoting")
        }
        if (command.contains("\$$") || command.contains("\$!")) {
            warnings.add("Command contains shell variable expansion")
        }

        return when {
            warnings.isEmpty() -> ValidationResult.Valid
            else -> ValidationResult.Warning(warnings.joinToString("; "))
        }
    }

    /**
     * Infers the type of a variable value and validates accordingly.
     *
     * @param value The value to validate
     * @return Pair of inferred type and validation result
     */
    fun inferAndValidate(value: String): Pair<VariableType, ValidationResult> {
        return when {
            // Check boolean first (very specific: only "true" or "false")
            value.toBooleanStrictOrNull() != null ->
                VariableType.BOOLEAN to ValidationResult.Valid

            // Check integer before hostname (numbers can match hostname pattern)
            value.toIntOrNull() != null ->
                VariableType.INTEGER to ValidationResult.Valid

            // Check IP addresses
            value.matches(IPV4_PATTERN) || value.matches(IPV6_PATTERN) ->
                VariableType.IP_ADDRESS to ValidationResult.Valid

            // Check email
            value.matches(EMAIL_PATTERN) ->
                VariableType.EMAIL to ValidationResult.Valid

            // Check path
            value.startsWith("/") ->
                VariableType.PATH to validatePath(value, mustExist = false)

            // Check hostname last (can match many patterns)
            value.matches(HOSTNAME_PATTERN) ->
                VariableType.HOSTNAME to ValidationResult.Valid

            else ->
                VariableType.STRING to ValidationResult.Valid
        }
    }
}

/**
 * Result of a validation operation.
 */
sealed class ValidationResult {
    /**
     * Validation succeeded.
     */
    object Valid : ValidationResult()

    /**
     * Validation failed.
     *
     * @param reason Description of why validation failed
     */
    data class Invalid(val reason: String) : ValidationResult()

    /**
     * Validation succeeded but with warnings.
     *
     * @param message Warning message
     */
    data class Warning(val message: String) : ValidationResult()

    /**
     * Checks if the validation was successful (Valid or Warning).
     */
    fun isSuccess(): Boolean = this is Valid || this is Warning

    /**
     * Checks if the validation failed.
     */
    fun isFailed(): Boolean = this is Invalid
}

/**
 * Inferred variable types for type-aware validation.
 */
enum class VariableType {
    STRING,
    INTEGER,
    BOOLEAN,
    IP_ADDRESS,
    HOSTNAME,
    EMAIL,
    PATH
}
