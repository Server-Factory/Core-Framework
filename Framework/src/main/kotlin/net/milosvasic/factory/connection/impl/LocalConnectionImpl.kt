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

    private var workingDirectory: File

    init {
        val workDir = config.options.getProperty("workingDirectory", System.getProperty("user.dir"))
        workingDirectory = File(workDir)
        Log.d("Local connection working directory: ${workingDirectory.absolutePath}")
    }

    override fun doConnect(): ConnectionResult {
        // Validate working directory exists
        if (!workingDirectory.exists()) {
            return ConnectionResult.Failure("Working directory does not exist: ${workingDirectory.absolutePath}")
        }
        if (!workingDirectory.isDirectory) {
            return ConnectionResult.Failure("Working directory is not a directory: ${workingDirectory.absolutePath}")
        }
        return ConnectionResult.Success("Local connection established")
    }

    override fun doExecute(command: String, timeout: Int): ExecutionResult {
        return try {
            val startTime = System.currentTimeMillis()

            // Use shell to execute command (needed for built-ins like exit, echo with pipes, etc.)
            val shell = if (System.getProperty("os.name").lowercase().contains("windows")) {
                listOf("cmd", "/c", command)
            } else {
                listOf("sh", "-c", command)
            }

            val process = ProcessBuilder(shell)
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
                return TransferResult.failure("Source file does not exist: $localPath")
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

    /**
     * Execute a command with environment variables.
     *
     * @param command The command to execute
     * @param env Environment variables to set
     * @return Execution result
     */
    fun executeWithEnvironment(command: String, env: Map<String, String>): ExecutionResult {
        if (!isConnected()) {
            return ExecutionResult.failure("Not connected")
        }

        return try {
            val startTime = System.currentTimeMillis()

            // Use shell to execute command
            val shell = if (System.getProperty("os.name").lowercase().contains("windows")) {
                listOf("cmd", "/c", command)
            } else {
                listOf("sh", "-c", command)
            }

            val processBuilder = ProcessBuilder(shell)
                .directory(workingDirectory)
                .redirectErrorStream(false)

            // Add environment variables
            val environment = processBuilder.environment()
            environment.putAll(env)

            val process = processBuilder.start()

            // Wait for completion
            val completed = process.waitFor(30, java.util.concurrent.TimeUnit.SECONDS)

            if (!completed) {
                process.destroy()
                return ExecutionResult.failure("Command timed out after 30 seconds")
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
            Log.e("Local execution with environment failed: ${e.message}")
            ExecutionResult.failure("Execution error: ${e.message}")
        }
    }

    /**
     * Change the working directory for command execution.
     *
     * @param path The new working directory path
     * @return true if successful, false otherwise
     */
    fun changeWorkingDirectory(path: String): Boolean {
        val newDir = File(path)
        return if (newDir.exists() && newDir.isDirectory) {
            workingDirectory = newDir
            Log.d("Changed working directory to: ${workingDirectory.absolutePath}")
            true
        } else {
            Log.w("Cannot change to directory: $path (does not exist or is not a directory)")
            false
        }
    }

    /**
     * Get the current working directory.
     *
     * @return The current working directory
     */
    fun getWorkingDirectory(): File? {
        return workingDirectory
    }
}
