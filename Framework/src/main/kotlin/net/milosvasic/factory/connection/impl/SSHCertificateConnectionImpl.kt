package net.milosvasic.factory.connection.impl

import net.milosvasic.factory.connection.*
import net.milosvasic.logger.Log
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * SSH connection implementation using certificate-based authentication.
 *
 * Provides SSH connectivity using SSH certificates (not to be confused with X.509).
 * SSH certificates are signed public keys that enable secure, scalable authentication
 * without needing to distribute public keys to all servers.
 *
 * Requirements:
 * - SSH client installed on the system
 * - SSH certificate file (signed public key)
 * - Corresponding private key
 * - Server configured to accept certificate authentication
 *
 * Configuration properties:
 * - certificatePath: Path to SSH certificate file (required)
 * - keyPath: Path to private key (required via credentials.keyPath)
 * - caKeyPath: Path to CA public key for verification (optional)
 *
 * @author Mail Server Factory Team
 * @since 3.1.0
 */
class SSHCertificateConnectionImpl(config: ConnectionConfig) : BaseConnection(config) {

    private val username = config.credentials?.username ?: "root"
    private val certificatePath: String
    private val keyPath: String
    private val caKeyPath: String?

    init {
        // Certificate path is required
        certificatePath = config.options.getProperty("certificatePath").takeIf { it.isNotEmpty() }
            ?: throw IllegalArgumentException("Certificate path is required for SSH certificate authentication")

        // Private key path is required
        keyPath = config.credentials?.keyPath?.takeIf { it.isNotEmpty() }
            ?: throw IllegalArgumentException("Private key path is required for SSH certificate authentication")

        // CA key path is optional
        caKeyPath = config.options.getProperty("caKeyPath").takeIf { it.isNotEmpty() }

        // Validate certificate file exists
        if (!File(certificatePath).exists()) {
            throw IllegalArgumentException("Certificate file not found: $certificatePath")
        }

        // Validate key file exists
        if (!File(keyPath).exists()) {
            throw IllegalArgumentException("Private key file not found: $keyPath")
        }
    }

    override fun doConnect(): ConnectionResult {
        return try {
            // Test SSH connection with certificate authentication
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
                Log.i("SSH certificate connection established to ${config.host}:${config.port}")
                ConnectionResult.Success("SSH certificate connection established")
            } else {
                ConnectionResult.Failure("SSH certificate connection test failed: $errorOutput")
            }
        } catch (e: Exception) {
            Log.e("SSH certificate connection failed: ${e.message}")
            ConnectionResult.Failure("SSH certificate connection failed: ${e.message}", e)
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
            Log.e("SSH certificate command execution failed: ${e.message}")
            ExecutionResult.failure("SSH certificate execution error: ${e.message}")
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
            Log.e("SSH certificate file upload failed: ${e.message}")
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
            Log.e("SSH certificate file download failed: ${e.message}")
            TransferResult.failure("Download error: ${e.message}")
        }
    }

    override fun doDisconnect() {
        // SSH is stateless (each command creates a new connection)
        Log.d("SSH certificate connection closed")
    }

    /**
     * Builds SSH command with certificate authentication.
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

        // Add private key
        sshCommand.add("-i")
        sshCommand.add(keyPath)

        // Add certificate file
        sshCommand.add("-o")
        sshCommand.add("CertificateFile=$certificatePath")

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
     * Builds SCP upload command with certificate authentication.
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

        // Add private key
        scpCommand.add("-i")
        scpCommand.add(keyPath)

        // Add certificate file
        scpCommand.add("-o")
        scpCommand.add("CertificateFile=$certificatePath")

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
     * Builds SCP download command with certificate authentication.
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

        // Add private key
        scpCommand.add("-i")
        scpCommand.add(keyPath)

        // Add certificate file
        scpCommand.add("-o")
        scpCommand.add("CertificateFile=$certificatePath")

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
            "protocol" to "SSH-Certificate",
            "sshVersion" to "2.0",
            "compression" to config.options.compression.toString(),
            "certificateAuth" to "true",
            "certificatePath" to certificatePath,
            "hasCaKey" to (caKeyPath != null).toString()
        )
    }
}
