package net.milosvasic.factory.connection.impl

import net.milosvasic.factory.connection.*
import net.milosvasic.logger.Log
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * SSH connection implementation using ssh-agent for authentication.
 *
 * Provides SSH connectivity using the ssh-agent for key management.
 * This allows passwordless authentication without specifying key files,
 * as the agent handles key storage and authentication.
 *
 * Requirements:
 * - SSH client installed on the system
 * - ssh-agent running with loaded keys
 * - SSH_AUTH_SOCK environment variable set
 *
 * Configuration properties:
 * - agentSocket: Override SSH_AUTH_SOCK path (optional)
 * - forwardAgent: Enable agent forwarding (default: false)
 *
 * @author Mail Server Factory Team
 * @since 3.1.0
 */
class SSHAgentConnectionImpl(config: ConnectionConfig) : BaseConnection(config) {

    private val username = config.credentials?.username ?: "root"
    private val agentSocket: String?
    private val forwardAgent: Boolean

    init {
        // Get agent socket from config or environment
        agentSocket = config.options.getProperty("agentSocket").takeIf { it.isNotEmpty() }
            ?: System.getenv("SSH_AUTH_SOCK")

        forwardAgent = config.options.getProperty("forwardAgent", "false").toBoolean()

        if (agentSocket.isNullOrEmpty()) {
            Log.w("SSH_AUTH_SOCK not set - ssh-agent may not be available")
        }
    }

    override fun doConnect(): ConnectionResult {
        return try {
            // First check if ssh-agent is running
            if (!isAgentRunning()) {
                return ConnectionResult.Failure("ssh-agent is not running or SSH_AUTH_SOCK is not set")
            }

            // Test SSH connection with agent authentication
            val testCommand = buildSSHCommand("echo 'connected'")
            val process = ProcessBuilder(testCommand)
                .apply {
                    environment()["SSH_AUTH_SOCK"] = agentSocket
                }
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
                Log.i("SSH agent connection established to ${config.host}:${config.port}")
                ConnectionResult.Success("SSH agent connection established")
            } else {
                ConnectionResult.Failure("SSH agent connection test failed: $errorOutput")
            }
        } catch (e: Exception) {
            Log.e("SSH agent connection failed: ${e.message}")
            ConnectionResult.Failure("SSH agent connection failed: ${e.message}", e)
        }
    }

    override fun doExecute(command: String, timeout: Int): ExecutionResult {
        return try {
            val startTime = System.currentTimeMillis()
            val sshCommand = buildSSHCommand(command)

            val process = ProcessBuilder(sshCommand)
                .apply {
                    agentSocket?.let { environment()["SSH_AUTH_SOCK"] = it }
                }
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
            Log.e("SSH agent command execution failed: ${e.message}")
            ExecutionResult.failure("SSH agent execution error: ${e.message}")
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
                .apply {
                    agentSocket?.let { environment()["SSH_AUTH_SOCK"] = it }
                }
                .redirectErrorStream(false)
                .start()

            val completed = process.waitFor(300, TimeUnit.SECONDS)

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
            Log.e("SSH agent file upload failed: ${e.message}")
            TransferResult.failure("Upload error: ${e.message}")
        }
    }

    override fun doDownloadFile(remotePath: String, localPath: String): TransferResult {
        return try {
            val destination = File(localPath)
            destination.parentFile?.mkdirs()

            val scpCommand = buildSCPDownloadCommand(remotePath, localPath)
            val process = ProcessBuilder(scpCommand)
                .apply {
                    agentSocket?.let { environment()["SSH_AUTH_SOCK"] = it }
                }
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
            Log.e("SSH agent file download failed: ${e.message}")
            TransferResult.failure("Download error: ${e.message}")
        }
    }

    override fun doDisconnect() {
        // SSH is stateless (each command creates a new connection)
        Log.d("SSH agent connection closed")
    }

    /**
     * Checks if ssh-agent is running.
     */
    private fun isAgentRunning(): Boolean {
        if (agentSocket.isNullOrEmpty()) {
            return false
        }

        return try {
            val process = ProcessBuilder("ssh-add", "-l")
                .apply {
                    environment()["SSH_AUTH_SOCK"] = agentSocket
                }
                .redirectErrorStream(false)
                .start()

            val completed = process.waitFor(5, TimeUnit.SECONDS)
            if (!completed) {
                process.destroy()
                return false
            }

            // Exit code 0 = keys loaded, 1 = no keys, 2 = agent not running
            process.exitValue() in 0..1
        } catch (e: Exception) {
            Log.e("Failed to check ssh-agent status: ${e.message}")
            false
        }
    }

    /**
     * Builds SSH command with agent authentication.
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

        // Enable agent forwarding if requested
        if (forwardAgent) {
            sshCommand.add("-A")
        }

        // Add port if not default
        if (config.port != 22) {
            sshCommand.add("-p")
            sshCommand.add(config.port.toString())
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
            "protocol" to "SSH-Agent",
            "sshVersion" to "2.0",
            "compression" to config.options.compression.toString(),
            "agentForwarding" to forwardAgent.toString(),
            "agentSocket" to (agentSocket ?: "not-set")
        )
    }
}
