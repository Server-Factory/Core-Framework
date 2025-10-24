package net.milosvasic.factory.connection

import java.time.Instant

/**
 * Base connection interface for all connection types.
 *
 * Provides a unified interface for connecting to and executing commands on various
 * systems including SSH, WinRM, Docker, Kubernetes, cloud platforms, and local execution.
 *
 * All connection implementations must:
 * - Support connection lifecycle (connect, disconnect, health check)
 * - Execute commands with timeout support
 * - Transfer files (upload/download)
 * - Provide connection metadata
 * - Be thread-safe
 * - Support resource cleanup (AutoCloseable)
 *
 * @author Mail Server Factory Team
 * @since 3.1.0
 */
interface Connection : AutoCloseable {

    /**
     * Establishes connection to the target system.
     *
     * @return ConnectionResult indicating success or failure
     */
    fun connect(): ConnectionResult

    /**
     * Checks if connection is currently active.
     *
     * @return true if connected and healthy
     */
    fun isConnected(): Boolean

    /**
     * Executes a command on the target system.
     *
     * @param command Command to execute
     * @param timeout Timeout in seconds (default: 120)
     * @return ExecutionResult with output and status
     */
    fun execute(command: String, timeout: Int = 120): ExecutionResult

    /**
     * Uploads a file to the target system.
     *
     * @param localPath Local file path
     * @param remotePath Remote file path
     * @return TransferResult indicating success or failure
     */
    fun uploadFile(localPath: String, remotePath: String): TransferResult

    /**
     * Downloads a file from the target system.
     *
     * @param remotePath Remote file path
     * @param localPath Local file path
     * @return TransferResult indicating success or failure
     */
    fun downloadFile(remotePath: String, localPath: String): TransferResult

    /**
     * Disconnects from the target system.
     */
    fun disconnect()

    /**
     * Gets connection metadata.
     *
     * @return ConnectionMetadata with connection information
     */
    fun getMetadata(): ConnectionMetadata

    /**
     * Gets current connection health status.
     *
     * @return ConnectionHealth with health information
     */
    fun getHealth(): ConnectionHealth

    /**
     * Validates the connection configuration.
     *
     * @return ValidationResult indicating if configuration is valid
     */
    fun validateConfig(): net.milosvasic.factory.validation.ValidationResult

    /**
     * Closes connection and releases resources (implements AutoCloseable).
     */
    override fun close() {
        disconnect()
    }
}

/**
 * Connection result indicating success or failure of connection attempt.
 */
sealed class ConnectionResult {
    data class Success(val message: String) : ConnectionResult()
    data class Failure(val error: String, val exception: Exception? = null) : ConnectionResult()

    fun isSuccess(): Boolean = this is Success
    fun isFailed(): Boolean = this is Failure
}

/**
 * Execution result from running a command.
 */
data class ExecutionResult(
    val success: Boolean,
    val output: String,
    val errorOutput: String = "",
    val exitCode: Int = 0,
    val duration: Long = 0 // milliseconds
) {
    companion object {
        fun success(output: String, exitCode: Int = 0, duration: Long = 0) =
            ExecutionResult(true, output, "", exitCode, duration)

        fun failure(errorOutput: String, exitCode: Int = 1, duration: Long = 0) =
            ExecutionResult(false, "", errorOutput, exitCode, duration)
    }
}

/**
 * File transfer result.
 */
data class TransferResult(
    val success: Boolean,
    val bytesTransferred: Long = 0,
    val message: String = "",
    val duration: Long = 0 // milliseconds
) {
    companion object {
        fun success(bytes: Long, duration: Long = 0) =
            TransferResult(true, bytes, "Transfer successful", duration)

        fun failure(message: String) =
            TransferResult(false, 0, message, 0)
    }
}

/**
 * Connection metadata.
 */
data class ConnectionMetadata(
    val type: ConnectionType,
    val host: String,
    val port: Int,
    val username: String,
    val displayName: String = "$username@$host:$port",
    val properties: Map<String, String> = emptyMap()
) {
    fun toMap(): Map<String, String> {
        return mapOf(
            "type" to type.name,
            "host" to host,
            "port" to port.toString(),
            "username" to username,
            "displayName" to displayName
        ) + properties
    }
}

/**
 * Connection health status.
 */
data class ConnectionHealth(
    val isHealthy: Boolean,
    val latencyMs: Long,
    val lastChecked: Instant = Instant.now(),
    val message: String = "",
    val details: Map<String, String> = emptyMap()
) {
    companion object {
        fun healthy(latencyMs: Long) =
            ConnectionHealth(true, latencyMs, Instant.now(), "Connection is healthy")

        fun unhealthy(message: String) =
            ConnectionHealth(false, -1, Instant.now(), message)
    }
}

