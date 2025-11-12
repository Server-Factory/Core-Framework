package net.milosvasic.factory.connection.impl

import net.milosvasic.factory.connection.*
import net.milosvasic.factory.validation.ValidationResult
import net.milosvasic.logger.Log
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.Base64

/**
 * Windows Remote Management (WinRM) connection implementation.
 *
 * Provides remote execution on Windows servers using the WinRM protocol.
 * Uses the pywinrm Python library via CLI for cross-platform compatibility.
 *
 * Requirements:
 * - Python 3 installed on the system
 * - pywinrm library installed (pip install pywinrm)
 * - WinRM service enabled on target Windows server
 * - Windows server must have appropriate firewall rules
 *
 * Configuration properties:
 * - transport: Authentication transport (basic, ntlm, kerberos, credssp) (default: ntlm)
 * - useSSL: Use HTTPS instead of HTTP (default: true)
 * - serverCertValidation: Validate server certificate (default: ignore)
 * - messageEncryption: Enable message encryption (default: auto)
 *
 * @author Mail Server Factory Team
 * @since 3.1.0
 */
class WinRMConnectionImpl(config: ConnectionConfig) : BaseConnection(config) {

    private val username: String
    private val password: String
    private val transport: String
    private val useSSL: Boolean
    private val serverCertValidation: String
    private val messageEncryption: String
    private val winrmPort: Int

    init {
        username = config.credentials?.username
            ?: throw IllegalArgumentException("Username is required for WinRM connection")

        password = config.credentials?.password
            ?: throw IllegalArgumentException("Password is required for WinRM connection")

        // Support both "authType" and "transport" property names (authType preferred)
        transport = config.options.getProperty("authType")
            ?: config.options.getProperty("transport", "ntlm")

        // Support both "useHttps" and "useSSL" property names (useHttps preferred)
        useSSL = config.options.getProperty("useHttps")?.toBoolean()
            ?: config.options.getProperty("useSSL", "true").toBoolean()

        serverCertValidation = config.options.getProperty("serverCertValidation", "ignore")
        messageEncryption = config.options.getProperty("messageEncryption", "auto")

        // Default WinRM ports: 5985 (HTTP), 5986 (HTTPS)
        winrmPort = if (config.port != 22) {
            config.port
        } else {
            if (useSSL) 5986 else 5985
        }
    }

    override fun doConnect(): ConnectionResult {
        return try {
            // Test WinRM connection with a simple command
            val testResult = executeWinRMCommand("echo connected", 30)

            if (testResult.success && testResult.output.contains("connected")) {
                Log.i("WinRM connection established to ${config.host}:$winrmPort")
                ConnectionResult.Success("WinRM connection established")
            } else {
                ConnectionResult.Failure("WinRM connection test failed: ${testResult.errorOutput}")
            }
        } catch (e: Exception) {
            Log.e("WinRM connection failed: ${e.message}")
            ConnectionResult.Failure("WinRM connection failed: ${e.message}", e)
        }
    }

    override fun doExecute(command: String, timeout: Int): ExecutionResult {
        return try {
            executeWinRMCommand(command, timeout)
        } catch (e: Exception) {
            Log.e("WinRM command execution failed: ${e.message}")
            ExecutionResult.failure("WinRM execution error: ${e.message}")
        }
    }

    override fun doUploadFile(localPath: String, remotePath: String): TransferResult {
        return try {
            val source = File(localPath)
            if (!source.exists()) {
                return TransferResult.failure("Source file not found: $localPath")
            }

            // Read file content and encode to base64
            val fileBytes = source.readBytes()
            val base64Content = Base64.getEncoder().encodeToString(fileBytes)

            // PowerShell command to decode and write file
            val psCommand = """
                |${'$'}bytes = [System.Convert]::FromBase64String('$base64Content')
                |[System.IO.File]::WriteAllBytes('$remotePath', ${'$'}bytes)
            """.trimMargin()

            val result = executeWinRMCommand(psCommand, 300)

            if (result.success) {
                TransferResult.success(source.length())
            } else {
                TransferResult.failure("WinRM file upload failed: ${result.errorOutput}")
            }
        } catch (e: Exception) {
            Log.e("WinRM file upload failed: ${e.message}")
            TransferResult.failure("Upload error: ${e.message}")
        }
    }

    override fun doDownloadFile(remotePath: String, localPath: String): TransferResult {
        return try {
            val destination = File(localPath)
            destination.parentFile?.mkdirs()

            // PowerShell command to read file and encode to base64
            val psCommand = """
                |${'$'}bytes = [System.IO.File]::ReadAllBytes('$remotePath')
                |[System.Convert]::ToBase64String(${'$'}bytes)
            """.trimMargin()

            val result = executeWinRMCommand(psCommand, 300)

            if (result.success && result.output.isNotEmpty()) {
                // Decode base64 content and write to local file
                val base64Content = result.output.trim()
                val fileBytes = Base64.getDecoder().decode(base64Content)
                destination.writeBytes(fileBytes)

                TransferResult.success(destination.length())
            } else {
                TransferResult.failure("WinRM file download failed: ${result.errorOutput}")
            }
        } catch (e: Exception) {
            Log.e("WinRM file download failed: ${e.message}")
            TransferResult.failure("Download error: ${e.message}")
        }
    }

    override fun doDisconnect() {
        // WinRM is stateless (each command creates a new session)
        Log.d("WinRM connection closed")
    }

    override fun validateConfig(): ValidationResult {
        val baseValidation = super.validateConfig()
        if (baseValidation.isFailed()) {
            return baseValidation
        }

        // Validate that authType is specified
        val authType = config.options.getProperty("authType")
            ?: config.options.getProperty("transport")

        if (authType.isNullOrEmpty()) {
            return ValidationResult.Invalid("WinRM connection requires 'authType' property (NTLM, Basic, Kerberos, or CredSSP)")
        }

        // Validate auth type is one of the supported values
        val validAuthTypes = listOf("ntlm", "basic", "kerberos", "credssp")
        if (!validAuthTypes.contains(authType.lowercase())) {
            return ValidationResult.Invalid("Invalid auth type: $authType. Must be one of: NTLM, Basic, Kerberos, CredSSP")
        }

        return ValidationResult.Valid
    }

    /**
     * Executes a WinRM command using Python winrm library.
     */
    private fun executeWinRMCommand(command: String, timeout: Int): ExecutionResult {
        val startTime = System.currentTimeMillis()

        // Create Python script to execute WinRM command
        val pythonScript = createWinRMPythonScript(command)

        val process = ProcessBuilder("python3", "-c", pythonScript)
            .redirectErrorStream(false)
            .start()

        val completed = process.waitFor(timeout.toLong(), TimeUnit.SECONDS)

        if (!completed) {
            process.destroy()
            return ExecutionResult.failure("WinRM command timed out after $timeout seconds")
        }

        val output = process.inputStream.bufferedReader().readText()
        val errorOutput = process.errorStream.bufferedReader().readText()
        val exitCode = process.exitValue()
        val duration = System.currentTimeMillis() - startTime

        return ExecutionResult(
            success = exitCode == 0,
            output = output,
            errorOutput = errorOutput,
            exitCode = exitCode,
            duration = duration
        )
    }

    /**
     * Creates a Python script for WinRM command execution.
     */
    private fun createWinRMPythonScript(command: String): String {
        val protocol = if (useSSL) "https" else "http"
        val endpoint = "$protocol://${config.host}:$winrmPort/wsman"

        // Escape command for Python string
        val escapedCommand = command.replace("\\", "\\\\").replace("'", "\\'")

        return """
import sys
try:
    import winrm
    session = winrm.Session(
        '$endpoint',
        auth=('$username', '$password'),
        transport='$transport',
        server_cert_validation='$serverCertValidation',
        message_encryption='$messageEncryption'
    )
    result = session.run_ps('$escapedCommand')
    sys.stdout.write(result.std_out.decode('utf-8'))
    sys.stderr.write(result.std_err.decode('utf-8'))
    sys.exit(result.status_code)
except ImportError:
    sys.stderr.write('Error: pywinrm library not installed. Run: pip install pywinrm\n')
    sys.exit(1)
except Exception as e:
    sys.stderr.write(f'WinRM error: {str(e)}\n')
    sys.exit(1)
""".trimIndent()
    }

    override fun buildMetadataProperties(): Map<String, String> {
        return super.buildMetadataProperties() + mapOf(
            "protocol" to "WinRM",
            "authMethod" to "WinRM",
            "authType" to transport.uppercase(),
            "transport" to transport,
            "useHttps" to useSSL.toString(),
            "useSSL" to useSSL.toString(),
            "port" to winrmPort.toString(),
            "serverCertValidation" to serverCertValidation,
            "messageEncryption" to messageEncryption
        )
    }
}
