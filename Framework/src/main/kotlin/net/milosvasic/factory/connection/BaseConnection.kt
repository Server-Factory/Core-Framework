package net.milosvasic.factory.connection

import net.milosvasic.factory.validation.ValidationResult
import net.milosvasic.logger.Log
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Base implementation of Connection interface providing common functionality.
 *
 * Subclasses must implement:
 * - doConnect()
 * - doExecute()
 * - doUploadFile()
 * - doDownloadFile()
 * - doDisconnect()
 *
 * @property config Connection configuration
 *
 * @author Mail Server Factory Team
 * @since 3.1.0
 */
abstract class BaseConnection(
    protected val config: ConnectionConfig
) : Connection {

    protected val connected = AtomicBoolean(false)
    protected var lastHealthCheck: Instant = Instant.now()
    protected var lastLatencyMs: Long = 0

    override fun connect(): ConnectionResult {
        return try {
            Log.i("Connecting to ${config.host}:${config.port} via ${config.type}")

            if (connected.get()) {
                return ConnectionResult.Success("Already connected")
            }

            val result = doConnect()

            if (result.isSuccess()) {
                connected.set(true)
                Log.i("Successfully connected to ${config.host}")
            } else {
                Log.e("Failed to connect to ${config.host}: ${(result as ConnectionResult.Failure).error}")
            }

            result
        } catch (e: Exception) {
            Log.e("Connection error: ${e.message}")
            ConnectionResult.Failure("Connection failed: ${e.message}", e)
        }
    }

    override fun isConnected(): Boolean {
        return connected.get()
    }

    override fun execute(command: String, timeout: Int): ExecutionResult {
        if (!connected.get()) {
            Log.w("Attempted to execute command while not connected")
            return ExecutionResult.failure("Not connected")
        }

        return try {
            Log.d("Executing: $command")
            val startTime = System.currentTimeMillis()
            val result = doExecute(command, timeout)
            val duration = System.currentTimeMillis() - startTime

            if (result.success) {
                Log.d("Command executed successfully (${duration}ms)")
            } else {
                Log.w("Command failed: ${result.errorOutput}")
            }

            result.copy(duration = duration)
        } catch (e: Exception) {
            Log.e("Execution error: ${e.message}")
            ExecutionResult.failure("Execution failed: ${e.message}")
        }
    }

    override fun uploadFile(localPath: String, remotePath: String): TransferResult {
        if (!connected.get()) {
            Log.w("Attempted to upload file while not connected")
            return TransferResult.failure("Not connected")
        }

        return try {
            Log.i("Uploading $localPath to $remotePath")
            val startTime = System.currentTimeMillis()
            val result = doUploadFile(localPath, remotePath)
            val duration = System.currentTimeMillis() - startTime

            if (result.success) {
                Log.i("Upload successful (${result.bytesTransferred} bytes, ${duration}ms)")
            } else {
                Log.e("Upload failed: ${result.message}")
            }

            result.copy(duration = duration)
        } catch (e: Exception) {
            Log.e("Upload error: ${e.message}")
            TransferResult.failure("Upload failed: ${e.message}")
        }
    }

    override fun downloadFile(remotePath: String, localPath: String): TransferResult {
        if (!connected.get()) {
            Log.w("Attempted to download file while not connected")
            return TransferResult.failure("Not connected")
        }

        return try {
            Log.i("Downloading $remotePath to $localPath")
            val startTime = System.currentTimeMillis()
            val result = doDownloadFile(remotePath, localPath)
            val duration = System.currentTimeMillis() - startTime

            if (result.success) {
                Log.i("Download successful (${result.bytesTransferred} bytes, ${duration}ms)")
            } else {
                Log.e("Download failed: ${result.message}")
            }

            result.copy(duration = duration)
        } catch (e: Exception) {
            Log.e("Download error: ${e.message}")
            TransferResult.failure("Download failed: ${e.message}")
        }
    }

    override fun disconnect() {
        if (!connected.get()) {
            Log.d("Already disconnected")
            return
        }

        try {
            Log.i("Disconnecting from ${config.host}")
            doDisconnect()
            connected.set(false)
            Log.i("Disconnected successfully")
        } catch (e: Exception) {
            Log.e("Disconnect error: ${e.message}")
            connected.set(false)
        }
    }

    override fun getMetadata(): ConnectionMetadata {
        return ConnectionMetadata(
            type = config.type,
            host = config.host,
            port = config.port,
            username = config.credentials?.username ?: "unknown",
            displayName = config.getDisplayName(),
            properties = buildMetadataProperties()
        )
    }

    override fun getHealth(): ConnectionHealth {
        lastHealthCheck = Instant.now()

        if (!connected.get()) {
            return ConnectionHealth.unhealthy("Not connected")
        }

        return try {
            val startTime = System.currentTimeMillis()
            val result = execute("echo ping", timeout = 5)
            lastLatencyMs = System.currentTimeMillis() - startTime

            if (result.success) {
                ConnectionHealth.healthy(lastLatencyMs)
            } else {
                ConnectionHealth.unhealthy("Health check command failed: ${result.errorOutput}")
            }
        } catch (e: Exception) {
            ConnectionHealth.unhealthy("Health check failed: ${e.message}")
        }
    }

    override fun validateConfig(): ValidationResult {
        return config.validate()
    }

    override fun close() {
        disconnect()
    }

    /**
     * Builds connection-specific metadata properties.
     * Subclasses can override to add specific properties.
     */
    protected open fun buildMetadataProperties(): Map<String, String> {
        return mapOf(
            "protocol" to config.type.name,
            "authMethod" to (config.credentials?.let {
                when {
                    it.certificatePath != null -> "Certificate"
                    it.keyPath != null -> "SSH Key"
                    it.agentSocket != null -> "SSH Agent"
                    it.password != null -> "Password"
                    else -> "Unknown"
                }
            } ?: "None")
        )
    }

    // Abstract methods that subclasses must implement

    /**
     * Performs the actual connection logic.
     */
    protected abstract fun doConnect(): ConnectionResult

    /**
     * Executes a command on the target system.
     */
    protected abstract fun doExecute(command: String, timeout: Int): ExecutionResult

    /**
     * Uploads a file to the target system.
     */
    protected abstract fun doUploadFile(localPath: String, remotePath: String): TransferResult

    /**
     * Downloads a file from the target system.
     */
    protected abstract fun doDownloadFile(remotePath: String, localPath: String): TransferResult

    /**
     * Performs the actual disconnect logic.
     */
    protected abstract fun doDisconnect()
}
