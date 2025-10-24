package net.milosvasic.factory.connection.impl

import net.milosvasic.factory.connection.*
import net.milosvasic.logger.Log
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Local command execution connection (no remote connection).
 *
 * Executes commands on the same machine using ProcessBuilder.
 * Useful for testing and local deployments.
 *
 * @author Mail Server Factory Team
 * @since 3.1.0
 */
class LocalConnectionImpl(config: ConnectionConfig) : BaseConnection(config) {

    private val workingDirectory: File

    init {
        val workDir = config.options.getProperty("workingDirectory", System.getProperty("user.dir"))
        workingDirectory = File(workDir).apply {
            if (!exists()) {
                mkdirs()
            }
        }
        Log.d("Local connection working directory: ${workingDirectory.absolutePath}")
    }

    override fun doConnect(): ConnectionResult {
        // Local connection is always "connected"
        return ConnectionResult.Success("Local connection established")
    }

    override fun doExecute(command: String, timeout: Int): ExecutionResult {
        return try {
            val startTime = System.currentTimeMillis()

            // Split command for ProcessBuilder
            val parts = command.split("\\s+".toRegex())
            val process = ProcessBuilder(parts)
                .directory(workingDirectory)
                .redirectErrorStream(false)
                .start()

            // Wait for completion with timeout
            val completed = process.waitFor(timeout.toLong(), java.util.concurrent.TimeUnit.SECONDS)

            if (!completed) {
                process.destroy()
                return ExecutionResult.failure("Command timed out after $timeout seconds")
            }

            val output = process.inputStream.bufferedReader().readText()
            val errorOutput = process.errorStream.bufferedReader().readText()
            val exitCode = process.exitValue()
            val duration = System.currentTimeMillis() - startTime

            ExecutionResult(
                success = exitCode == 0,
                output = output,
                errorOutput = errorOutput,
                exitCode = exitCode,
                duration = duration
            )
        } catch (e: Exception) {
            Log.e("Local execution failed: ${e.message}")
            ExecutionResult.failure("Local execution error: ${e.message}")
        }
    }

    override fun doUploadFile(localPath: String, remotePath: String): TransferResult {
        return try {
            val source = File(localPath)
            val destination = File(remotePath)

            if (!source.exists()) {
                return TransferResult.failure("Source file not found: $localPath")
            }

            // Ensure destination directory exists
            destination.parentFile?.mkdirs()

            // Copy file
            Files.copy(
                source.toPath(),
                destination.toPath(),
                StandardCopyOption.REPLACE_EXISTING
            )

            TransferResult.success(source.length())
        } catch (e: Exception) {
            Log.e("Local file upload failed: ${e.message}")
            TransferResult.failure("Upload error: ${e.message}")
        }
    }

    override fun doDownloadFile(remotePath: String, localPath: String): TransferResult {
        // For local connection, download is just a copy
        return doUploadFile(remotePath, localPath)
    }

    override fun doDisconnect() {
        // Nothing to disconnect for local execution
        Log.d("Local connection closed")
    }

    override fun buildMetadataProperties(): Map<String, String> {
        return super.buildMetadataProperties() + mapOf(
            "workingDirectory" to workingDirectory.absolutePath,
            "os" to System.getProperty("os.name"),
            "osVersion" to System.getProperty("os.version"),
            "architecture" to System.getProperty("os.arch")
        )
    }
}
