package net.milosvasic.factory.connection

import net.milosvasic.factory.validation.InputValidator
import net.milosvasic.factory.validation.ValidationResult

/**
 * Connection configuration for all connection types.
 *
 * Provides a unified configuration structure that can be loaded from JSON
 * and used to create any type of connection.
 *
 * @author Mail Server Factory Team
 * @since 3.1.0
 */
data class ConnectionConfig(
    val type: ConnectionType,
    val host: String,
    val port: Int,
    val credentials: Credentials? = null,
    val options: ConnectionOptions = ConnectionOptions(),
    val bastionConfig: ConnectionConfig? = null, // For SSH_BASTION
    val cloudConfig: CloudConfig? = null,        // For cloud connections
    val containerConfig: ContainerConfig? = null // For container connections
) {
    /**
     * Validates the connection configuration.
     */
    fun validate(): ValidationResult {
        // Validate host (except for LOCAL and DOCKER types - Docker uses socket paths)
        val requiresHostValidation = type !in listOf(ConnectionType.LOCAL, ConnectionType.DOCKER)
        if (requiresHostValidation) {
            val hostResult = InputValidator.validateHost(host)
            if (hostResult.isFailed()) {
                return hostResult
            }
        }

        // Validate port (skip for connection types that don't use traditional ports)
        val requiresPort = type !in listOf(
            ConnectionType.LOCAL,
            ConnectionType.DOCKER,
            ConnectionType.AWS_SSM,
            ConnectionType.AZURE_SERIAL
        )

        if (requiresPort && port > 0) {
            val portResult = InputValidator.validatePort(port, allowPrivileged = true)
            if (portResult.isFailed()) {
                return portResult
            }
        }

        // Validate credentials
        if (credentials != null) {
            val credsResult = credentials.validate()
            if (credsResult.isFailed()) {
                return credsResult
            }
        }

        // Type-specific validation
        return when (type) {
            ConnectionType.SSH_BASTION -> validateBastionConfig()
            ConnectionType.DOCKER, ConnectionType.KUBERNETES -> validateContainerConfig()
            ConnectionType.AWS_SSM, ConnectionType.AZURE_SERIAL, ConnectionType.GCP_OS_LOGIN -> validateCloudConfig()
            else -> ValidationResult.Valid
        }
    }

    private fun validateBastionConfig(): ValidationResult {
        if (bastionConfig == null) {
            return ValidationResult.Invalid("Bastion configuration required for SSH_BASTION type")
        }
        return bastionConfig.validate()
    }

    private fun validateContainerConfig(): ValidationResult {
        if (containerConfig == null) {
            return ValidationResult.Invalid("Container configuration required for ${type.name} type")
        }
        return containerConfig.validate()
    }

    private fun validateCloudConfig(): ValidationResult {
        if (cloudConfig == null) {
            return ValidationResult.Invalid("Cloud configuration required for ${type.name} type")
        }
        return cloudConfig.validate()
    }

    /**
     * Gets the display name for this connection.
     */
    fun getDisplayName(): String {
        return when (type) {
            ConnectionType.LOCAL -> "localhost"
            ConnectionType.DOCKER -> "docker://${containerConfig?.containerName ?: host}"
            ConnectionType.KUBERNETES -> "k8s://${containerConfig?.namespace}/${containerConfig?.podSelector}"
            ConnectionType.AWS_SSM -> {
                val instanceId = cloudConfig?.instanceId ?: host
                val region = cloudConfig?.region
                if (region != null) "$instanceId ($region)" else instanceId
            }
            ConnectionType.AZURE_SERIAL -> {
                val vmName = cloudConfig?.vmName ?: host
                val resourceGroup = cloudConfig?.resourceGroup
                if (resourceGroup != null) "$vmName ($resourceGroup)" else vmName
            }
            ConnectionType.GCP_OS_LOGIN -> {
                val instanceId = cloudConfig?.instanceId ?: host
                val project = cloudConfig?.project
                val zone = cloudConfig?.zone
                when {
                    project != null && zone != null -> "$instanceId ($project/$zone)"
                    zone != null -> "$instanceId ($zone)"
                    else -> instanceId
                }
            }
            ConnectionType.ANSIBLE -> "${credentials?.username ?: "user"}@$host"
            else -> "${credentials?.username ?: "user"}@$host:$port"
        }
    }
}

/**
 * Connection credentials.
 */
data class Credentials(
    val username: String,
    val password: String? = null,
    val keyPath: String? = null,
    val certificatePath: String? = null,
    val agentSocket: String? = null,
    val encrypted: Boolean = false
) {
    /**
     * Validates the credentials.
     */
    fun validate(): ValidationResult {
        // Validate username
        val usernameResult = InputValidator.validateUsername(username)
        if (usernameResult.isFailed()) {
            return usernameResult
        }

        // At least one authentication method required
        if (password == null && keyPath == null && certificatePath == null && agentSocket == null) {
            return ValidationResult.Invalid("At least one authentication method required (password, key, certificate, or agent)")
        }

        // Validate key path if provided
        if (keyPath != null) {
            val keyPathResult = InputValidator.validatePath(keyPath, mustExist = false, allowAbsolute = true)
            if (keyPathResult.isFailed()) {
                return keyPathResult
            }
        }

        // Validate certificate path if provided
        if (certificatePath != null) {
            val certPathResult = InputValidator.validatePath(certificatePath, mustExist = false, allowAbsolute = true)
            if (certPathResult.isFailed()) {
                return certPathResult
            }
        }

        return ValidationResult.Valid
    }

    /**
     * Gets the decrypted password.
     *
     * If password starts with "encrypted:", it's considered encrypted and should
     * be decrypted by the calling code. This method just returns the password as-is.
     */
    fun getDecryptedPassword(): String? {
        return password
    }

    /**
     * Checks if the password is encrypted (starts with "encrypted:" prefix).
     */
    fun isPasswordEncrypted(): Boolean {
        return password?.startsWith("encrypted:") == true
    }
}

/**
 * Connection options.
 */
data class ConnectionOptions(
    val timeout: Int = 120,              // Default timeout in seconds
    val retries: Int = 3,                // Number of retry attempts
    val retryDelay: Long = 5000,         // Delay between retries in ms
    val healthCheck: Boolean = true,     // Enable health checking
    val compression: Boolean = false,    // Enable compression
    val keepAlive: Boolean = true,       // Keep connection alive
    val strictHostKeyChecking: Boolean = true, // For SSH connections
    val properties: Map<String, String> = emptyMap() // Additional custom properties
) {
    fun getProperty(key: String, default: String = ""): String {
        return properties[key] ?: default
    }

    fun hasProperty(key: String): Boolean {
        return properties.containsKey(key)
    }
}

/**
 * Cloud connection configuration.
 */
data class CloudConfig(
    val provider: CloudProvider,
    val region: String? = null,
    val instanceId: String? = null,
    val resourceGroup: String? = null,
    val vmName: String? = null,
    val project: String? = null,
    val zone: String? = null,
    val subscriptionId: String? = null,
    val tenantId: String? = null,
    val clientId: String? = null,
    val clientSecret: String? = null,
    val serviceAccountKey: String? = null,
    val profile: String? = null,
    val properties: Map<String, String> = emptyMap()
) {
    fun validate(): ValidationResult {
        return when (provider) {
            CloudProvider.AWS -> validateAWS()
            CloudProvider.AZURE -> validateAzure()
            CloudProvider.GCP -> validateGCP()
        }
    }

    private fun validateAWS(): ValidationResult {
        if (instanceId == null) {
            return ValidationResult.Invalid("AWS instanceId required")
        }
        if (region == null) {
            return ValidationResult.Invalid("AWS region required")
        }
        return ValidationResult.Valid
    }

    private fun validateAzure(): ValidationResult {
        if (subscriptionId == null || resourceGroup == null || vmName == null) {
            return ValidationResult.Invalid("Azure subscriptionId, resourceGroup, and vmName required")
        }
        return ValidationResult.Valid
    }

    private fun validateGCP(): ValidationResult {
        if (project == null || zone == null || instanceId == null) {
            return ValidationResult.Invalid("GCP project, zone, and instance required")
        }
        return ValidationResult.Valid
    }
}

/**
 * Container connection configuration.
 */
data class ContainerConfig(
    val containerType: ContainerType,
    val containerName: String? = null,
    val image: String? = null,
    val namespace: String? = null,
    val podSelector: String? = null,
    val podName: String? = null,
    val containerInPod: String? = null,
    val kubeconfig: String? = null,
    val dockerHost: String? = null,
    val network: String? = null,
    val volumes: List<String> = emptyList(),
    val environmentVariables: Map<String, String> = emptyMap(),
    val properties: Map<String, String> = emptyMap()
) {
    fun validate(): ValidationResult {
        return when (containerType) {
            ContainerType.DOCKER -> validateDocker()
            ContainerType.KUBERNETES -> validateKubernetes()
        }
    }

    private fun validateDocker(): ValidationResult {
        if (containerName == null && image == null) {
            return ValidationResult.Invalid("Docker containerName or image required")
        }
        return ValidationResult.Valid
    }

    private fun validateKubernetes(): ValidationResult {
        if (namespace == null) {
            return ValidationResult.Invalid("Kubernetes namespace required")
        }
        if (podSelector == null && podName == null) {
            return ValidationResult.Invalid("Kubernetes podSelector or podName required")
        }
        return ValidationResult.Valid
    }
}

/**
 * Connection configuration builder for fluent API.
 */
class ConnectionConfigBuilder {
    private var type: ConnectionType = ConnectionType.SSH
    private var host: String = "localhost"
    private var port: Int = 22
    private var credentials: Credentials? = null
    private var options: ConnectionOptions = ConnectionOptions()
    private var bastionConfig: ConnectionConfig? = null
    private var cloudConfig: CloudConfig? = null
    private var containerConfig: ContainerConfig? = null

    fun type(type: ConnectionType) = apply { this.type = type }
    fun host(host: String) = apply { this.host = host }
    fun port(port: Int) = apply { this.port = port }
    fun credentials(credentials: Credentials) = apply { this.credentials = credentials }
    fun options(options: ConnectionOptions) = apply { this.options = options }
    fun bastionConfig(config: ConnectionConfig) = apply { this.bastionConfig = config }
    fun cloudConfig(config: CloudConfig) = apply { this.cloudConfig = config }
    fun containerConfig(config: ContainerConfig) = apply { this.containerConfig = config }

    fun build(): ConnectionConfig {
        return ConnectionConfig(
            type = type,
            host = host,
            port = port,
            credentials = credentials,
            options = options,
            bastionConfig = bastionConfig,
            cloudConfig = cloudConfig,
            containerConfig = containerConfig
        )
    }
}
