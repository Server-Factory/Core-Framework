package net.milosvasic.factory.connection

/**
 * Enumeration of all supported connection types.
 *
 * @since 3.1.0
 */
enum class ConnectionType {
    SSH,
    SSH_AGENT,
    SSH_CERTIFICATE,
    SSH_BASTION,
    WINRM,
    ANSIBLE,
    DOCKER,
    KUBERNETES,
    AWS_SSM,
    AZURE_SERIAL,
    GCP_OS_LOGIN,
    LOCAL;

    /**
     * Check if this connection type is remote (requires network connection).
     */
    fun isRemote(): Boolean {
        return this != LOCAL
    }

    /**
     * Check if this connection type uses SSH protocol.
     */
    fun isSSH(): Boolean {
        return this in listOf(SSH, SSH_AGENT, SSH_CERTIFICATE, SSH_BASTION, ANSIBLE)
    }

    /**
     * Check if this connection type uses cloud APIs.
     */
    fun isCloud(): Boolean {
        return this in listOf(AWS_SSM, AZURE_SERIAL, GCP_OS_LOGIN)
    }

    /**
     * Check if this connection type uses containers.
     */
    fun isContainer(): Boolean {
        return this in listOf(DOCKER, KUBERNETES)
    }
}

/**
 * Cloud provider types.
 *
 * @since 3.1.0
 */
enum class CloudProvider {
    AWS,
    AZURE,
    GCP
}

/**
 * Container platform types.
 *
 * @since 3.1.0
 */
enum class ContainerType {
    DOCKER,
    KUBERNETES
}
