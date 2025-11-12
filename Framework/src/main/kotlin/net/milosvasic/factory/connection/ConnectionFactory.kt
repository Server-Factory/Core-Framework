package net.milosvasic.factory.connection

import net.milosvasic.factory.connection.impl.*
import net.milosvasic.logger.Log

/**
 * Factory for creating Connection instances.
 *
 * Provides centralized creation logic for all connection types with proper
 * configuration validation and initialization.
 *
 * @author Mail Server Factory Team
 * @since 3.1.0
 */
object ConnectionFactory {

    /**
     * Creates a connection instance based on the configuration.
     *
     * @param config Connection configuration
     * @return Connection instance
     * @throws ConnectionException if configuration is invalid
     */
    fun create(config: ConnectionConfig): Connection {
        // Validate configuration
        val validationResult = config.validate()
        if (validationResult.isFailed()) {
            val errorMessage = (validationResult as net.milosvasic.factory.validation.ValidationResult.Invalid).reason
            throw ConnectionException("Invalid connection configuration: $errorMessage")
        }

        Log.i("Creating ${config.type} connection to ${config.getDisplayName()}")

        return when (config.type) {
            ConnectionType.LOCAL -> LocalConnectionImpl(config)
            ConnectionType.SSH -> SSHConnectionImpl(config)
            ConnectionType.SSH_AGENT -> SSHAgentConnectionImpl(config)
            ConnectionType.SSH_CERTIFICATE -> SSHCertificateConnectionImpl(config)
            ConnectionType.SSH_BASTION -> SSHBastionConnectionImpl(config)
            ConnectionType.WINRM -> WinRMConnectionImpl(config)
            ConnectionType.ANSIBLE -> AnsibleConnectionImpl(config)
            ConnectionType.DOCKER -> DockerConnectionImpl(config)
            ConnectionType.KUBERNETES -> KubernetesConnectionImpl(config)
            ConnectionType.AWS_SSM -> AWSSSMConnectionImpl(config)
            ConnectionType.AZURE_SERIAL -> AzureSerialConnectionImpl(config)
            ConnectionType.GCP_OS_LOGIN -> GCPOSLoginConnectionImpl(config)
        }
    }

    /**
     * Creates and connects a connection in one call.
     *
     * @param config Connection configuration
     * @return Connected connection instance
     * @throws ConnectionException if connection fails
     */
    fun createAndConnect(config: ConnectionConfig): Connection {
        val connection = create(config)

        val result = connection.connect()
        if (result.isFailed()) {
            val error = (result as ConnectionResult.Failure).error
            throw ConnectionException("Failed to connect: $error", result.exception)
        }

        return connection
    }

    /**
     * Creates a connection using the builder pattern.
     *
     * @param block Builder configuration block
     * @return Connection instance
     */
    fun build(block: ConnectionConfigBuilder.() -> Unit): Connection {
        val builder = ConnectionConfigBuilder()
        builder.block()
        val config = builder.build()
        return create(config)
    }
}

/**
 * Exception thrown when connection creation or establishment fails.
 */
class ConnectionException(message: String, cause: Throwable? = null) : Exception(message, cause)
