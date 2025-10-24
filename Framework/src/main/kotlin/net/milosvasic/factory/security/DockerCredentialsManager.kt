package net.milosvasic.factory.security

import net.milosvasic.logger.Log
import net.milosvasic.factory.connection.Connection
import net.milosvasic.factory.validation.InputValidator
import net.milosvasic.factory.validation.ValidationResult
import java.io.File
import java.util.Base64

/**
 * Secure Docker credentials manager.
 *
 * Features:
 * - Encrypted Docker Hub credentials
 * - Secure credential storage on remote host
 * - Docker credential helper integration
 * - Credential validation
 * - Audit logging
 * - Cleanup after use
 *
 * This addresses P1 Issue #8: Secure Docker credentials handling
 *
 * Docker credentials can be stored:
 * 1. Encrypted in configuration (recommended)
 * 2. In environment variables
 * 3. Using Docker credential helpers
 * 4. In secure credential store
 *
 * @author Mail Server Factory Team
 * @since 3.1.0
 */
object DockerCredentialsManager {

    private const val DOCKER_CONFIG_DIR = ".docker"
    private const val DOCKER_CONFIG_FILE = "config.json"
    private const val DEFAULT_REGISTRY = "https://index.docker.io/v1/"

    /**
     * Docker login with encrypted credentials.
     *
     * @param connection Connection to remote host
     * @param username Docker Hub username
     * @param password Docker Hub password (encrypted or plain)
     * @param registry Docker registry URL (default: Docker Hub)
     * @param masterKey Master encryption key
     * @return true if login successful
     */
    fun login(
        connection: Connection,
        username: String,
        password: String,
        registry: String = DEFAULT_REGISTRY,
        masterKey: String? = null
    ): Boolean {
        try {
            Log.i("Performing Docker login...")

            // Validate username
            val usernameResult = InputValidator.validateUsername(username)
            if (usernameResult.isFailed()) {
                Log.e("Invalid Docker username: ${(usernameResult as ValidationResult.Invalid).reason}")
                AuditLogger.logAuthentication(username, success = false, details = "Invalid username")
                return false
            }

            // Decrypt password if encrypted
            val decryptedPassword = if (SecureConfiguration.isEncrypted(password)) {
                if (masterKey == null) {
                    Log.e("Encrypted password provided but no master key available")
                    return false
                }
                SecureConfiguration.decryptPassword(password.removePrefix("encrypted:"), masterKey)
            } else {
                Log.w("Using plain text Docker password (INSECURE)")
                password
            }

            // Validate password
            if (decryptedPassword.isEmpty()) {
                Log.e("Docker password cannot be empty")
                return false
            }

            // Validate registry
            if (registry.isEmpty()) {
                Log.e("Docker registry cannot be empty")
                return false
            }

            // Perform login using stdin to avoid password in process list
            val loginCommand = "echo '${escapeForShell(decryptedPassword)}' | docker login -u '${escapeForShell(username)}' --password-stdin '$registry'"

            val result = connection.execute(loginCommand)

            if (result.success) {
                Log.i("Docker login successful for user: $username")

                // Log successful authentication
                AuditLogger.logAuthentication(
                    user = username,
                    success = true,
                    details = "Docker Hub authentication to $registry"
                )

                // Secure the credentials file
                secureDockerConfig(connection)

                return true
            } else {
                Log.e("Docker login failed: ${result.output}")

                // Log failed authentication
                AuditLogger.logAuthentication(
                    user = username,
                    success = false,
                    details = "Docker Hub authentication failed"
                )

                return false
            }

        } catch (e: Exception) {
            Log.e("Docker login error: ${e.message}", e)
            AuditLogger.logAuthentication(
                user = username,
                success = false,
                details = "Docker login error: ${e.message}"
            )
            return false
        }
    }

    /**
     * Docker logout from registry.
     *
     * @param connection Connection to remote host
     * @param registry Docker registry URL
     * @return true if logout successful
     */
    fun logout(connection: Connection, registry: String = DEFAULT_REGISTRY): Boolean {
        try {
            Log.i("Performing Docker logout...")

            val result = connection.execute("docker logout '$registry'")

            if (result.success) {
                Log.i("Docker logout successful")

                AuditLogger.log(
                    event = AuditEvent.AUTHENTICATION,
                    action = AuditAction.LOGOUT,
                    details = "Docker Hub logout from $registry",
                    result = AuditResult.SUCCESS
                )

                return true
            } else {
                Log.w("Docker logout failed (may not have been logged in)")
                return false
            }

        } catch (e: Exception) {
            Log.e("Docker logout error: ${e.message}", e)
            return false
        }
    }

    /**
     * Configures Docker to use credential helper.
     *
     * @param connection Connection to remote host
     * @param helper Credential helper name (e.g., "pass", "secretservice", "osxkeychain")
     * @return true if configured successfully
     */
    fun configureCredentialHelper(connection: Connection, helper: String): Boolean {
        try {
            Log.i("Configuring Docker credential helper: $helper")

            // Validate helper name
            if (!helper.matches(Regex("^[a-zA-Z0-9-]+$"))) {
                Log.e("Invalid credential helper name: $helper")
                return false
            }

            // Check if helper is available
            val helperPath = "docker-credential-$helper"
            val checkResult = connection.execute("which $helperPath")

            if (!checkResult.success) {
                Log.e("Credential helper not found: $helperPath")
                Log.i("Install with: apt-get install docker-credential-$helper")
                return false
            }

            // Update Docker config
            val configJson = """
                {
                  "credsStore": "$helper"
                }
            """.trimIndent()

            val configDir = "\$HOME/$DOCKER_CONFIG_DIR"
            val configFile = "$configDir/$DOCKER_CONFIG_FILE"

            // Create config directory
            connection.execute("mkdir -p '$configDir'")
            connection.execute("chmod 700 '$configDir'")

            // Write config
            val writeCommand = "cat > '$configFile' << 'EOF'\n$configJson\nEOF"
            connection.execute(writeCommand)

            // Secure permissions
            connection.execute("chmod 600 '$configFile'")

            Log.i("Docker credential helper configured: $helper")

            AuditLogger.logConfigurationChange(
                user = "system",
                resource = "docker_config",
                details = "Configured credential helper: $helper"
            )

            return true

        } catch (e: Exception) {
            Log.e("Failed to configure credential helper: ${e.message}", e)
            return false
        }
    }

    /**
     * Secures Docker configuration file permissions.
     *
     * @param connection Connection to remote host
     */
    fun secureDockerConfig(connection: Connection) {
        try {
            val configDir = "\$HOME/$DOCKER_CONFIG_DIR"
            val configFile = "$configDir/$DOCKER_CONFIG_FILE"

            // Set restrictive permissions on config directory
            connection.execute("chmod 700 '$configDir' 2>/dev/null || true")

            // Set restrictive permissions on config file
            connection.execute("chmod 600 '$configFile' 2>/dev/null || true")

            Log.v("Docker config permissions secured")

        } catch (e: Exception) {
            Log.w("Failed to secure Docker config permissions: ${e.message}")
        }
    }

    /**
     * Removes Docker credentials from remote host.
     *
     * @param connection Connection to remote host
     * @return true if cleanup successful
     */
    fun cleanup(connection: Connection): Boolean {
        try {
            Log.i("Cleaning up Docker credentials...")

            val configFile = "\$HOME/$DOCKER_CONFIG_DIR/$DOCKER_CONFIG_FILE"

            // Check if config exists
            val checkResult = connection.execute("test -f '$configFile' && echo 'exists' || echo 'not_found'")

            if (checkResult.output.trim() == "exists") {
                // Securely delete credentials
                connection.execute("shred -u '$configFile' 2>/dev/null || rm -f '$configFile'")

                Log.i("Docker credentials removed")

                AuditLogger.log(
                    event = AuditEvent.FILE_ACCESS,
                    action = AuditAction.DELETE,
                    details = "Docker credentials file deleted",
                    result = AuditResult.SUCCESS,
                    resource = configFile
                )

                return true
            } else {
                Log.v("No Docker credentials to clean up")
                return true
            }

        } catch (e: Exception) {
            Log.e("Failed to cleanup Docker credentials: ${e.message}", e)
            return false
        }
    }

    /**
     * Validates Docker credentials without logging in.
     *
     * @param username Docker Hub username
     * @param password Docker Hub password
     * @return ValidationResult
     */
    fun validateCredentials(username: String, password: String): ValidationResult {
        // Validate username
        val usernameResult = InputValidator.validateUsername(username)
        if (usernameResult.isFailed()) {
            return usernameResult
        }

        // Validate password
        if (password.isEmpty()) {
            return ValidationResult.Invalid("Password cannot be empty")
        }

        if (password.length < 8) {
            return ValidationResult.Warning("Password is short (< 8 characters)")
        }

        return ValidationResult.Valid
    }

    /**
     * Gets Docker credentials from configuration with fallback to environment.
     *
     * @param configUsername Username from config
     * @param configPassword Password from config (may be encrypted)
     * @return Pair of (username, decrypted password)
     */
    fun getCredentials(configUsername: String?, configPassword: String?): Pair<String, String>? {
        try {
            // Try environment first
            val envUsername = SecureConfiguration.getSecret("MAIL_FACTORY_DOCKER_USERNAME")
            val envPassword = SecureConfiguration.getSecret("MAIL_FACTORY_DOCKER_PASSWORD")

            if (envUsername != null && envPassword != null) {
                Log.i("Using Docker credentials from environment")
                return Pair(envUsername, envPassword)
            }

            // Fall back to config
            if (configUsername.isNullOrEmpty() || configPassword.isNullOrEmpty()) {
                Log.e("Docker credentials not found in environment or configuration")
                return null
            }

            // Decrypt password if needed
            val password = if (SecureConfiguration.isEncrypted(configPassword)) {
                val masterKey = SecureConfiguration.getMasterKey()
                SecureConfiguration.decryptPassword(
                    configPassword.removePrefix("encrypted:"),
                    masterKey
                )
            } else {
                Log.w("Using plain text Docker password from configuration (INSECURE)")
                configPassword
            }

            return Pair(configUsername, password)

        } catch (e: Exception) {
            Log.e("Failed to get Docker credentials: ${e.message}", e)
            return null
        }
    }

    /**
     * Checks if Docker is logged in to registry.
     *
     * @param connection Connection to remote host
     * @param registry Docker registry URL
     * @return true if logged in
     */
    fun isLoggedIn(connection: Connection, registry: String = DEFAULT_REGISTRY): Boolean {
        return try {
            val configFile = "\$HOME/$DOCKER_CONFIG_DIR/$DOCKER_CONFIG_FILE"

            val checkResult = connection.execute("test -f '$configFile' && grep -q '\"$registry\"' '$configFile' && echo 'yes' || echo 'no'")

            checkResult.output.trim() == "yes"

        } catch (e: Exception) {
            false
        }
    }

    /**
     * Escapes string for safe use in shell.
     */
    private fun escapeForShell(input: String): String {
        return input.replace("'", "'\\''")
    }

    /**
     * Docker credential configuration for JSON.
     */
    data class DockerCredentials(
        val username: String,
        val password: String,
        val registry: String = DEFAULT_REGISTRY
    ) {
        /**
         * Validates the credentials.
         */
        fun validate(): ValidationResult {
            return validateCredentials(username, password)
        }

        /**
         * Returns credentials with encrypted password.
         */
        fun encrypt(masterKey: String): DockerCredentials {
            val encrypted = if (!SecureConfiguration.isEncrypted(password)) {
                "encrypted:${Encryption.encrypt(password, masterKey)}"
            } else {
                password
            }

            return copy(password = encrypted)
        }
    }
}
