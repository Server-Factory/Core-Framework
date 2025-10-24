package net.milosvasic.factory.security

import net.milosvasic.logger.Log
import java.io.File
import java.util.Properties

/**
 * Secure configuration manager that handles encrypted credentials.
 *
 * Supports two modes:
 * 1. Encrypted credentials in JSON: passwords stored as "encrypted:salt:iv:ciphertext"
 * 2. Environment variables: credentials loaded from secure environment
 *
 * Usage:
 * ```
 * // Load master key from environment
 * val masterKey = SecureConfiguration.getMasterKey()
 *
 * // Decrypt password
 * val password = SecureConfiguration.decryptPassword(encryptedPassword, masterKey)
 * ```
 *
 * Environment variables:
 * - MAIL_FACTORY_MASTER_KEY: Master encryption key (required for encrypted passwords)
 * - MAIL_FACTORY_DB_PASSWORD: Database password (alternative to encrypted config)
 * - MAIL_FACTORY_SSH_PASSWORD: SSH password (alternative to encrypted config)
 * - MAIL_FACTORY_DOCKER_PASSWORD: Docker Hub password (alternative to encrypted config)
 *
 * @author Mail Server Factory Team
 * @since 3.1.0
 */
object SecureConfiguration {

    private const val ENV_MASTER_KEY = "MAIL_FACTORY_MASTER_KEY"
    private const val ENV_DB_PASSWORD = "MAIL_FACTORY_DB_PASSWORD"
    private const val ENV_SSH_PASSWORD = "MAIL_FACTORY_SSH_PASSWORD"
    private const val ENV_DOCKER_PASSWORD = "MAIL_FACTORY_DOCKER_PASSWORD"
    private const val ENV_SECRETS_FILE = "MAIL_FACTORY_SECRETS_FILE"

    private const val ENCRYPTED_PREFIX = "encrypted:"

    private val secrets: MutableMap<String, String> = mutableMapOf()

    init {
        loadSecretsFromEnvironment()
        loadSecretsFromFile()
    }

    /**
     * Gets the master encryption key from environment.
     *
     * @return Master key for decryption
     * @throws SecureConfigurationException if master key not found
     */
    fun getMasterKey(): String {
        val masterKey = System.getenv(ENV_MASTER_KEY)
        if (masterKey.isNullOrEmpty()) {
            throw SecureConfigurationException(
                "Master key not found. Set environment variable $ENV_MASTER_KEY"
            )
        }
        return masterKey
    }

    /**
     * Checks if a password value is encrypted.
     *
     * @param value The password value to check
     * @return true if the value is encrypted
     */
    fun isEncrypted(value: String): Boolean {
        return value.startsWith(ENCRYPTED_PREFIX)
    }

    /**
     * Decrypts a password if it's encrypted, otherwise returns as-is.
     *
     * Encrypted format: "encrypted:salt:iv:ciphertext"
     *
     * @param password The password (encrypted or plain)
     * @param masterKey The master encryption key
     * @return Decrypted password
     * @throws DecryptionException if decryption fails
     */
    fun decryptPassword(password: String, masterKey: String): String {
        if (!isEncrypted(password)) {
            Log.w("Password is not encrypted - using plain text (INSECURE)")
            return password
        }

        // Remove "encrypted:" prefix
        val encryptedData = password.removePrefix(ENCRYPTED_PREFIX)

        return try {
            Encryption.decrypt(encryptedData, masterKey)
        } catch (e: Exception) {
            throw DecryptionException("Failed to decrypt password: ${e.message}", e)
        }
    }

    /**
     * Gets a secret from environment variables or secrets file.
     *
     * Priority:
     * 1. Environment variable
     * 2. Secrets file
     * 3. null (not found)
     *
     * @param key The secret key to retrieve
     * @return The secret value or null if not found
     */
    fun getSecret(key: String): String? {
        // Check environment first
        val envValue = System.getenv(key)
        if (!envValue.isNullOrEmpty()) {
            return envValue
        }

        // Check secrets map
        return secrets[key]
    }

    /**
     * Gets a secret or throws exception if not found.
     *
     * @param key The secret key to retrieve
     * @return The secret value
     * @throws SecureConfigurationException if secret not found
     */
    fun requireSecret(key: String): String {
        return getSecret(key)
            ?: throw SecureConfigurationException("Required secret not found: $key")
    }

    /**
     * Gets database password from environment or configuration.
     *
     * @param configPassword Password from config file (may be encrypted)
     * @return Decrypted password
     */
    fun getDatabasePassword(configPassword: String?): String {
        // Try environment first
        val envPassword = getSecret(ENV_DB_PASSWORD)
        if (envPassword != null) {
            return envPassword
        }

        // Use config password
        if (configPassword.isNullOrEmpty()) {
            throw SecureConfigurationException("Database password not found in config or environment")
        }

        return if (isEncrypted(configPassword)) {
            decryptPassword(configPassword, getMasterKey())
        } else {
            configPassword
        }
    }

    /**
     * Gets SSH password from environment or configuration.
     *
     * @param configPassword Password from config file (may be encrypted)
     * @return Decrypted password
     */
    fun getSshPassword(configPassword: String?): String {
        // Try environment first
        val envPassword = getSecret(ENV_SSH_PASSWORD)
        if (envPassword != null) {
            return envPassword
        }

        // Use config password
        if (configPassword.isNullOrEmpty()) {
            throw SecureConfigurationException("SSH password not found in config or environment")
        }

        return if (isEncrypted(configPassword)) {
            decryptPassword(configPassword, getMasterKey())
        } else {
            configPassword
        }
    }

    /**
     * Gets Docker Hub password from environment or configuration.
     *
     * @param configPassword Password from config file (may be encrypted)
     * @return Decrypted password
     */
    fun getDockerPassword(configPassword: String?): String {
        // Try environment first
        val envPassword = getSecret(ENV_DOCKER_PASSWORD)
        if (envPassword != null) {
            return envPassword
        }

        // Use config password
        if (configPassword.isNullOrEmpty()) {
            throw SecureConfigurationException("Docker password not found in config or environment")
        }

        return if (isEncrypted(configPassword)) {
            decryptPassword(configPassword, getMasterKey())
        } else {
            configPassword
        }
    }

    /**
     * Loads secrets from environment variables.
     */
    private fun loadSecretsFromEnvironment() {
        // Load all environment variables that start with MAIL_FACTORY_
        System.getenv().forEach { (key, value) ->
            if (key.startsWith("MAIL_FACTORY_")) {
                secrets[key] = value
            }
        }
    }

    /**
     * Loads secrets from a properties file.
     *
     * File location from environment: MAIL_FACTORY_SECRETS_FILE
     */
    private fun loadSecretsFromFile() {
        val secretsFilePath = System.getenv(ENV_SECRETS_FILE) ?: return

        val secretsFile = File(secretsFilePath)
        if (!secretsFile.exists()) {
            Log.w("Secrets file not found: $secretsFilePath")
            return
        }

        try {
            val properties = Properties()
            secretsFile.inputStream().use { input ->
                properties.load(input)
            }

            properties.forEach { key, value ->
                secrets[key.toString()] = value.toString()
            }

            Log.i("Loaded ${properties.size} secrets from $secretsFilePath")
        } catch (e: Exception) {
            Log.e("Failed to load secrets file: ${e.message}", e)
        }
    }

    /**
     * Clears all cached secrets from memory.
     *
     * Call this when secrets are no longer needed.
     */
    fun clearSecrets() {
        secrets.clear()
    }

    /**
     * Validates that all required secrets are available.
     *
     * @param requiredKeys List of required secret keys
     * @throws SecureConfigurationException if any required secret is missing
     */
    fun validateRequiredSecrets(requiredKeys: List<String>) {
        val missing = requiredKeys.filter { getSecret(it) == null }
        if (missing.isNotEmpty()) {
            throw SecureConfigurationException(
                "Missing required secrets: ${missing.joinToString(", ")}"
            )
        }
    }
}

/**
 * Exception thrown when secure configuration operations fail.
 */
class SecureConfigurationException(message: String, cause: Throwable? = null) : Exception(message, cause)
