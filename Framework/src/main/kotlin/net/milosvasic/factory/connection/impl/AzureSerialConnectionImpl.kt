package net.milosvasic.factory.connection.impl

import net.milosvasic.factory.connection.*
import net.milosvasic.logger.Log
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Azure Serial Console connection implementation.
 *
 * Provides emergency console access to Azure VMs through the serial console.
 * Useful for troubleshooting VMs when network connectivity is unavailable.
 *
 * Requirements:
 * - Azure CLI installed and configured
 * - Azure VM with serial console enabled
 * - Boot diagnostics enabled on the VM
 * - Appropriate Azure RBAC permissions
 * - VM must support serial console (most Linux distributions)
 *
 * Configuration properties:
 * - vmName: Azure VM name (required, can use host field)
 * - resourceGroup: Azure resource group (required)
 * - subscription: Azure subscription ID (optional, uses default)
 *
 * Note: Serial console is primarily for emergency access and troubleshooting.
 * For regular operations, SSH or other connection types are recommended.
 *
 * @author Mail Server Factory Team
 * @since 3.1.0
 */
class AzureSerialConnectionImpl(config: ConnectionConfig) : BaseConnection(config) {

    private val vmName: String
    private val resourceGroup: String
    private val subscription: String?

    init {
        // VM name from host field or properties
        vmName = config.host.takeIf { it.isNotEmpty() }
            ?: config.options.getProperty("vmName")
            .takeIf { it.isNotEmpty() }
            ?: throw IllegalArgumentException("Azure VM name is required")

        resourceGroup = config.options.getProperty("resourceGroup").takeIf { it.isNotEmpty() }
            ?: throw IllegalArgumentException("Azure resource group is required")

        subscription = config.options.getProperty("subscription").takeIf { it.isNotEmpty() }
    }

    override fun doConnect(): ConnectionResult {
        return try {
            // Check if VM exists and serial console is available
            val showCmd = buildAzCommand(listOf(
                "vm", "show",
                "--name", vmName,
                "--resource-group", resourceGroup,
                "--query", "provisioningState",
                "--output", "tsv"
            ))

            val process = ProcessBuilder(showCmd)
                .redirectErrorStream(false)
                .start()

            val completed = process.waitFor(30, TimeUnit.SECONDS)
            if (!completed) {
                process.destroy()
                return ConnectionResult.Failure("Azure VM check timed out")
            }

            val output = process.inputStream.bufferedReader().readText().trim()
            val errorOutput = process.errorStream.bufferedReader().readText()
            val exitCode = process.exitValue()

            if (exitCode == 0 && output == "Succeeded") {
                Log.i("Azure serial console connection available for VM: $vmName")
                ConnectionResult.Success("Azure serial console connection established")
            } else if (exitCode == 0) {
                ConnectionResult.Failure("VM $vmName is not in succeeded state (status: $output)")
            } else {
                ConnectionResult.Failure("Azure serial console connection test failed: $errorOutput")
            }
        } catch (e: Exception) {
            Log.e("Azure serial console connection failed: ${e.message}")
            ConnectionResult.Failure("Azure serial console connection failed: ${e.message}", e)
        }
    }

    override fun doExecute(command: String, timeout: Int): ExecutionResult {
        return try {
            val startTime = System.currentTimeMillis()

            // Use run-command for command execution
            val runCmd = buildAzCommand(listOf(
                "vm", "run-command", "invoke",
                "--name", vmName,
                "--resource-group", resourceGroup,
                "--command-id", "RunShellScript",
                "--scripts", command
            ))

            val process = ProcessBuilder(runCmd)
                .redirectErrorStream(false)
                .start()

            val completed = process.waitFor(timeout.toLong(), TimeUnit.SECONDS)

            if (!completed) {
                process.destroy()
                return ExecutionResult.failure("Azure run-command timed out after $timeout seconds")
            }

            val output = process.inputStream.bufferedReader().readText()
            val errorOutput = process.errorStream.bufferedReader().readText()
            val exitCode = process.exitValue()
            val duration = System.currentTimeMillis() - startTime

            // Parse Azure run-command output
            val (stdout, stderr) = parseAzureOutput(output)

            ExecutionResult(
                success = exitCode == 0,
                output = stdout,
                errorOutput = stderr.ifEmpty { errorOutput },
                exitCode = exitCode,
                duration = duration
            )
        } catch (e: Exception) {
            Log.e("Azure run-command execution failed: ${e.message}")
            ExecutionResult.failure("Azure run-command error: ${e.message}")
        }
    }

    override fun doUploadFile(localPath: String, remotePath: String): TransferResult {
        return try {
            val source = File(localPath)
            if (!source.exists()) {
                return TransferResult.failure("Source file not found: $localPath")
            }

            // Read file and encode to base64
            val fileContent = source.readBytes()
            val encodedContent = java.util.Base64.getEncoder().encodeToString(fileContent)

            // Use run-command to write file
            val writeScript = """
                echo '$encodedContent' | base64 -d > $remotePath
                chmod 644 $remotePath
            """.trimIndent()

            val result = doExecute(writeScript, 300)

            if (result.success) {
                TransferResult.success(source.length())
            } else {
                TransferResult.failure("Azure file upload failed: ${result.errorOutput}")
            }
        } catch (e: Exception) {
            Log.e("Azure file upload failed: ${e.message}")
            TransferResult.failure("Upload error: ${e.message}")
        }
    }

    override fun doDownloadFile(remotePath: String, localPath: String): TransferResult {
        return try {
            val destination = File(localPath)
            destination.parentFile?.mkdirs()

            // Use run-command to read file
            val readScript = "base64 $remotePath"
            val result = doExecute(readScript, 300)

            if (result.success && result.output.isNotEmpty()) {
                val decodedContent = java.util.Base64.getDecoder().decode(result.output.trim())
                destination.writeBytes(decodedContent)
                TransferResult.success(destination.length())
            } else {
                TransferResult.failure("Azure file download failed: ${result.errorOutput}")
            }
        } catch (e: Exception) {
            Log.e("Azure file download failed: ${e.message}")
            TransferResult.failure("Download error: ${e.message}")
        }
    }

    override fun doDisconnect() {
        // Azure CLI commands are stateless
        Log.d("Azure serial console connection closed")
    }

    /**
     * Builds Azure CLI command with subscription.
     */
    private fun buildAzCommand(args: List<String>): List<String> {
        val cmd = mutableListOf("az")

        // Add subscription if specified
        subscription?.let {
            cmd.add("--subscription")
            cmd.add(it)
        }

        // Add remaining arguments
        cmd.addAll(args)

        return cmd
    }

    /**
     * Parses Azure run-command output to extract stdout and stderr.
     */
    private fun parseAzureOutput(output: String): Pair<String, String> {
        // Azure run-command returns JSON with value array containing stdout/stderr
        val stdoutPattern = Regex("\"message\":\\s*\"([^\"]*?)\"")
        val stderrPattern = Regex("\"stderr\":\\s*\"([^\"]*?)\"")

        val stdoutMatch = stdoutPattern.find(output)
        val stderrMatch = stderrPattern.find(output)

        val stdout = stdoutMatch?.groupValues?.get(1)?.replace("\\n", "\n") ?: output
        val stderr = stderrMatch?.groupValues?.get(1)?.replace("\\n", "\n") ?: ""

        return Pair(stdout, stderr)
    }

    override fun buildMetadataProperties(): Map<String, String> {
        return super.buildMetadataProperties() + mapOf(
            "protocol" to "Azure-Serial",
            "vmName" to vmName,
            "resourceGroup" to resourceGroup,
            "subscription" to (subscription ?: "default")
        )
    }
}
