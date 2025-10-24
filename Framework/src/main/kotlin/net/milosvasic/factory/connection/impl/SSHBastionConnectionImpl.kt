package net.milosvasic.factory.connection.impl

import net.milosvasic.factory.connection.*
import net.milosvasic.logger.Log
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * SSH connection implementation through a bastion host (jump server).
 *
 * Provides SSH connectivity to a target server via an intermediate bastion/jump host.
 * This is commonly used for accessing servers in private networks or DMZ environments
 * where direct SSH access is not allowed.
 *
 * Requirements:
 * - SSH client installed on the system
 * - SSH key-based authentication configured for bastion host
 * - SSH key-based authentication configured for target host (optional, can be same key)
 * - Bastion host must allow SSH forwarding
 *
 * Configuration properties:
 * - bastionHost: Bastion server hostname/IP (required)
 * - bastionPort: Bastion SSH port (default: 22)
 * - bastionUser: Username for bastion host (default: root)
 * - bastionKeyPath: SSH key for bastion (optional, uses keyPath if not specified)
 * - proxyCommand: Custom ProxyCommand override (optional, auto-generated if not specified)
 *
 * @author Mail Server Factory Team
 * @since 3.1.0
 */
class SSHBastionConnectionImpl(config: ConnectionConfig) : BaseConnection(config) {

    private val username = config.credentials?.username ?: "root"
    private val keyPath = config.credentials?.keyPath
    private val bastionHost: String
    private val bastionPort: Int
    private val bastionUser: String
    private val bastionKeyPath: String?
    private val proxyCommand: String?

    init {
        bastionHost = config.options.getProperty("bastionHost").takeIf { it.isNotEmpty() }
            ?: throw IllegalArgumentException("Bastion host is required for SSH bastion connection")

        bastionPort = config.options.getProperty("bastionPort", "22").toIntOrNull() ?: 22
        bastionUser = config.options.getProperty("bastionUser", "root")
        bastionKeyPath = config.options.getProperty("bastionKeyPath").takeIf { it.isNotEmpty() }
        proxyCommand = config.options.getProperty("proxyCommand").takeIf { it.isNotEmpty() }
    }

    override fun doConnect(): ConnectionResult {
        return try {
            // Test SSH connection through bastion
            val testCommand = buildSSHCommand("echo 'connected'")
            val process = ProcessBuilder(testCommand)
                .redirectErrorStream(false)
                .start()

            val completed = process.waitFor(30, TimeUnit.SECONDS)
            if (!completed) {
                process.destroy()
                return ConnectionResult.Failure("SSH bastion connection test timed out")
            }

            val output = process.inputStream.bufferedReader().readText()
            val errorOutput = process.errorStream.bufferedReader().readText()
            val exitCode = process.exitValue()

            if (exitCode == 0 && output.contains("connected")) {
                Log.i("SSH bastion connection established to ${config.host}:${config.port} via $bastionHost")
                ConnectionResult.Success("SSH bastion connection established")
            } else {
                ConnectionResult.Failure("SSH bastion connection test failed: $errorOutput")
            }
        } catch (e: Exception) {
            Log.e("SSH bastion connection failed: ${e.message}")
            ConnectionResult.Failure("SSH bastion connection failed: ${e.message}", e)
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
            Log.e("SSH bastion command execution failed: ${e.message}")
            ExecutionResult.failure("SSH bastion execution error: ${e.message}")
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
            Log.e("SSH bastion file upload failed: ${e.message}")
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
            Log.e("SSH bastion file download failed: ${e.message}")
            TransferResult.failure("Download error: ${e.message}")
        }
    }

    override fun doDisconnect() {
        // SSH is stateless (each command creates a new connection)
        Log.d("SSH bastion connection closed")
    }

    /**
     * Builds the ProxyCommand for SSH jump host.
     */
    private fun buildProxyCommand(): String {
        return if (proxyCommand != null) {
            proxyCommand
        } else {
            val keyOption = bastionKeyPath?.let { "-i $it" } ?: (keyPath?.let { "-i $it" } ?: "")
            "ssh -W %h:%p -p $bastionPort $keyOption -o StrictHostKeyChecking=no $bastionUser@$bastionHost"
        }
    }

    /**
     * Builds SSH command with bastion/jump host configuration.
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

        // Add ProxyCommand for bastion
        sshCommand.add("-o")
        sshCommand.add("ProxyCommand=${buildProxyCommand()}")

        // Add port if not default
        if (config.port != 22) {
            sshCommand.add("-p")
            sshCommand.add(config.port.toString())
        }

        // Add SSH key for target host if provided
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
     * Builds SCP upload command through bastion.
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

        // Add ProxyCommand for bastion
        scpCommand.add("-o")
        scpCommand.add("ProxyCommand=${buildProxyCommand()}")

        // Add port if not default
        if (config.port != 22) {
            scpCommand.add("-P")
            scpCommand.add(config.port.toString())
        }

        // Add SSH key for target host if provided
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
     * Builds SCP download command through bastion.
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

        // Add ProxyCommand for bastion
        scpCommand.add("-o")
        scpCommand.add("ProxyCommand=${buildProxyCommand()}")

        // Add port if not default
        if (config.port != 22) {
            scpCommand.add("-P")
            scpCommand.add(config.port.toString())
        }

        // Add SSH key for target host if provided
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
            "protocol" to "SSH-Bastion",
            "sshVersion" to "2.0",
            "compression" to config.options.compression.toString(),
            "bastionHost" to bastionHost,
            "bastionPort" to bastionPort.toString(),
            "bastionUser" to bastionUser,
            "customProxyCommand" to (proxyCommand != null).toString()
        )
    }
}
