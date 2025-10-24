package net.milosvasic.factory.connection.impl

import net.milosvasic.factory.connection.*
import net.milosvasic.logger.Log
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Standard SSH connection implementation using command-line ssh/scp.
 *
 * Provides SSH connectivity using SSH keys (passwordless authentication).
 * Uses the system's ssh command for maximum compatibility.
 *
 * Requirements:
 * - SSH client installed on the system
 * - SSH key-based authentication configured
 * - Known hosts file populated
 *
 * @author Mail Server Factory Team
 * @since 3.1.0
 */
class SSHConnectionImpl(config: ConnectionConfig) : BaseConnection(config) {

    private val username = config.credentials?.username ?: "root"
    private val keyPath = config.credentials?.keyPath

    override fun doConnect(): ConnectionResult {
        return try {
            // Test SSH connection with a simple command
            val testCommand = buildSSHCommand("echo 'connected'")
            val process = ProcessBuilder(testCommand)
                .redirectErrorStream(false)
                .start()

            val completed = process.waitFor(30, TimeUnit.SECONDS)
            if (!completed) {
                process.destroy()
                return ConnectionResult.Failure("SSH connection test timed out")
            }

            val output = process.inputStream.bufferedReader().readText()
            val errorOutput = process.errorStream.bufferedReader().readText()
            val exitCode = process.exitValue()

            if (exitCode == 0 && output.contains("connected")) {
                Log.i("SSH connection established to ${config.host}:${config.port}")
                ConnectionResult.Success("SSH connection established")
            } else {
                ConnectionResult.Failure("SSH connection test failed: $errorOutput")
            }
        } catch (e: Exception) {
            Log.e("SSH connection failed: ${e.message}")
            ConnectionResult.Failure("SSH connection failed: ${e.message}", e)
        }
    }

    override fun doExecute(command: String, timeout: Int): ExecutionResult {
        return try {
            val startTime = System.currentTimeMillis()
            val sshCommand = buildSSHCommand(command)

            val process = ProcessBuilder(sshCommand)
                .redirectErrorStream(false)
                .start()

            val completed = process.waitFor(timeout.toLong(), TimeUnit.SECONDS)

            if (!completed) {
                process.destroy()
                return ExecutionResult.failure("SSH command timed out after $timeout seconds")
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
            Log.e("SSH command execution failed: ${e.message}")
            ExecutionResult.failure("SSH execution error: ${e.message}")
        }
    }

    override fun doUploadFile(localPath: String, remotePath: String): TransferResult {
        return try {
            val source = File(localPath)
            if (!source.exists()) {
                return TransferResult.failure("Source file not found: $localPath")
            }

            val scpCommand = buildSCPUploadCommand(localPath, remotePath)
            val process = ProcessBuilder(scpCommand)
                .redirectErrorStream(false)
                .start()

            val completed = process.waitFor(300, TimeUnit.SECONDS) // 5 minute timeout for large files

            if (!completed) {
                process.destroy()
                return TransferResult.failure("SCP upload timed out")
            }

            val errorOutput = process.errorStream.bufferedReader().readText()
            val exitCode = process.exitValue()

            if (exitCode == 0) {
                TransferResult.success(source.length())
            } else {
                TransferResult.failure("SCP upload failed: $errorOutput")
            }
        } catch (e: Exception) {
            Log.e("SSH file upload failed: ${e.message}")
            TransferResult.failure("Upload error: ${e.message}")
        }
    }

    override fun doDownloadFile(remotePath: String, localPath: String): TransferResult {
        return try {
            val destination = File(localPath)
            destination.parentFile?.mkdirs()

            val scpCommand = buildSCPDownloadCommand(remotePath, localPath)
            val process = ProcessBuilder(scpCommand)
                .redirectErrorStream(false)
                .start()

            val completed = process.waitFor(300, TimeUnit.SECONDS)

            if (!completed) {
                process.destroy()
                return TransferResult.failure("SCP download timed out")
            }

            val errorOutput = process.errorStream.bufferedReader().readText()
            val exitCode = process.exitValue()

            if (exitCode == 0 && destination.exists()) {
                TransferResult.success(destination.length())
            } else {
                TransferResult.failure("SCP download failed: $errorOutput")
            }
        } catch (e: Exception) {
            Log.e("SSH file download failed: ${e.message}")
            TransferResult.failure("Download error: ${e.message}")
        }
    }

    override fun doDisconnect() {
        // SSH is stateless (each command creates a new connection)
        Log.d("SSH connection closed")
    }

    /**
     * Builds SSH command with proper options.
     */
    private fun buildSSHCommand(command: String): List<String> {
        val sshCommand = mutableListOf("ssh")

        // Add SSH options
        sshCommand.add("-o")
        sshCommand.add("StrictHostKeyChecking=no")
        sshCommand.add("-o")
        sshCommand.add("UserKnownHostsFile=/dev/null")
        sshCommand.add("-o")
        sshCommand.add("LogLevel=ERROR")

        // Add port if not default
        if (config.port != 22) {
            sshCommand.add("-p")
            sshCommand.add(config.port.toString())
        }

        // Add SSH key if provided
        keyPath?.let {
            if (it.isNotEmpty()) {
                sshCommand.add("-i")
                sshCommand.add(it)
            }
        }

        // Add compression if enabled
        if (config.options.compression) {
            sshCommand.add("-C")
        }

        // Add connection timeout
        sshCommand.add("-o")
        sshCommand.add("ConnectTimeout=${config.options.timeout}")

        // Add user@host
        sshCommand.add("$username@${config.host}")

        // Add command
        sshCommand.add(command)

        return sshCommand
    }

    /**
     * Builds SCP upload command.
     */
    private fun buildSCPUploadCommand(localPath: String, remotePath: String): List<String> {
        val scpCommand = mutableListOf("scp")

        // Add SCP options
        scpCommand.add("-o")
        scpCommand.add("StrictHostKeyChecking=no")
        scpCommand.add("-o")
        scpCommand.add("UserKnownHostsFile=/dev/null")
        scpCommand.add("-o")
        scpCommand.add("LogLevel=ERROR")

        // Add port if not default
        if (config.port != 22) {
            scpCommand.add("-P")
            scpCommand.add(config.port.toString())
        }

        // Add SSH key if provided
        keyPath?.let {
            if (it.isNotEmpty()) {
                scpCommand.add("-i")
                scpCommand.add(it)
            }
        }

        // Add compression if enabled
        if (config.options.compression) {
            scpCommand.add("-C")
        }

        // Add source and destination
        scpCommand.add(localPath)
        scpCommand.add("$username@${config.host}:$remotePath")

        return scpCommand
    }

    /**
     * Builds SCP download command.
     */
    private fun buildSCPDownloadCommand(remotePath: String, localPath: String): List<String> {
        val scpCommand = mutableListOf("scp")

        // Add SCP options
        scpCommand.add("-o")
        scpCommand.add("StrictHostKeyChecking=no")
        scpCommand.add("-o")
        scpCommand.add("UserKnownHostsFile=/dev/null")
        scpCommand.add("-o")
        scpCommand.add("LogLevel=ERROR")

        // Add port if not default
        if (config.port != 22) {
            scpCommand.add("-P")
            scpCommand.add(config.port.toString())
        }

        // Add SSH key if provided
        keyPath?.let {
            if (it.isNotEmpty()) {
                scpCommand.add("-i")
                scpCommand.add(it)
            }
        }

        // Add compression if enabled
        if (config.options.compression) {
            scpCommand.add("-C")
        }

        // Add source and destination
        scpCommand.add("$username@${config.host}:$remotePath")
        scpCommand.add(localPath)

        return scpCommand
    }

    override fun buildMetadataProperties(): Map<String, String> {
        return super.buildMetadataProperties() + mapOf(
            "protocol" to "SSH",
            "sshVersion" to "2.0",
            "compression" to config.options.compression.toString(),
            "keyAuthentication" to (keyPath?.isNotEmpty() == true).toString()
        )
    }
}
