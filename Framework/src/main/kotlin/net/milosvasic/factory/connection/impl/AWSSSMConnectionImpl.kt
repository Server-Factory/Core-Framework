package net.milosvasic.factory.connection.impl

import net.milosvasic.factory.connection.*
import net.milosvasic.logger.Log
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * AWS Systems Manager (SSM) Session Manager connection implementation.
 *
 * Provides secure, audited remote execution on EC2 instances without requiring
 * SSH keys or open inbound ports. Uses AWS SSM Session Manager for connectivity.
 *
 * Requirements:
 * - AWS CLI v2 installed and configured
 * - Session Manager plugin installed
 * - IAM permissions for SSM (ssm:StartSession, etc.)
 * - EC2 instance with SSM agent running
 * - Instance has appropriate IAM role for SSM
 *
 * Configuration properties:
 * - instanceId: EC2 instance ID (required, can use host field)
 * - region: AWS region (optional, uses default from AWS config)
 * - profile: AWS CLI profile to use (optional)
 * - documentName: SSM document for session (default: AWS-StartInteractiveCommand)
 *
 * @author Mail Server Factory Team
 * @since 3.1.0
 */
class AWSSSMConnectionImpl(config: ConnectionConfig) : BaseConnection(config) {

    private val instanceId: String
    private val region: String?
    private val profile: String?
    private val documentName: String

    init {
        // Instance ID from host field or properties
        instanceId = config.host.takeIf { it.isNotEmpty() && it.startsWith("i-") }
            ?: config.options.getProperty("instanceId")
            .takeIf { it.isNotEmpty() }
            ?: throw IllegalArgumentException("EC2 instance ID required (format: i-xxxxxxxxxxxxxxxxx)")

        region = config.options.getProperty("region").takeIf { it.isNotEmpty() }
        profile = config.options.getProperty("profile").takeIf { it.isNotEmpty() }
        documentName = config.options.getProperty("documentName", "AWS-StartInteractiveCommand")
    }

    override fun doConnect(): ConnectionResult {
        return try {
            // Test SSM connection by checking instance status
            val describeCmd = buildAWSCommand(listOf(
                "ssm", "describe-instance-information",
                "--filters", "Key=InstanceIds,Values=$instanceId",
                "--query", "InstanceInformationList[0].PingStatus",
                "--output", "text"
            ))

            val process = ProcessBuilder(describeCmd)
                .redirectErrorStream(false)
                .start()

            val completed = process.waitFor(30, TimeUnit.SECONDS)
            if (!completed) {
                process.destroy()
                return ConnectionResult.Failure("AWS SSM connection test timed out")
            }

            val output = process.inputStream.bufferedReader().readText().trim()
            val errorOutput = process.errorStream.bufferedReader().readText()
            val exitCode = process.exitValue()

            if (exitCode == 0 && output == "Online") {
                Log.i("AWS SSM connection established to instance: $instanceId")
                ConnectionResult.Success("AWS SSM connection established")
            } else if (exitCode == 0) {
                ConnectionResult.Failure("Instance $instanceId is not online (status: $output)")
            } else {
                ConnectionResult.Failure("AWS SSM connection test failed: $errorOutput")
            }
        } catch (e: Exception) {
            Log.e("AWS SSM connection failed: ${e.message}")
            ConnectionResult.Failure("AWS SSM connection failed: ${e.message}", e)
        }
    }

    override fun doExecute(command: String, timeout: Int): ExecutionResult {
        return try {
            val startTime = System.currentTimeMillis()

            // Use send-command for non-interactive execution
            val sendCmd = buildAWSCommand(listOf(
                "ssm", "send-command",
                "--instance-ids", instanceId,
                "--document-name", "AWS-RunShellScript",
                "--parameters", "commands=$command",
                "--query", "Command.CommandId",
                "--output", "text"
            ))

            val sendProcess = ProcessBuilder(sendCmd)
                .redirectErrorStream(false)
                .start()

            val sendCompleted = sendProcess.waitFor(30, TimeUnit.SECONDS)
            if (!sendCompleted) {
                sendProcess.destroy()
                return ExecutionResult.failure("AWS SSM send-command timed out")
            }

            val commandId = sendProcess.inputStream.bufferedReader().readText().trim()
            if (sendProcess.exitValue() != 0) {
                val error = sendProcess.errorStream.bufferedReader().readText()
                return ExecutionResult.failure("AWS SSM send-command failed: $error")
            }

            // Wait for command completion and get output
            Thread.sleep(2000) // Brief wait for command to start

            val getCmd = buildAWSCommand(listOf(
                "ssm", "get-command-invocation",
                "--command-id", commandId,
                "--instance-id", instanceId,
                "--query", "[StandardOutputContent,StandardErrorContent,Status]",
                "--output", "json"
            ))

            var attempts = 0
            val maxAttempts = timeout / 2

            while (attempts < maxAttempts) {
                val getProcess = ProcessBuilder(getCmd)
                    .redirectErrorStream(false)
                    .start()

                getProcess.waitFor(10, TimeUnit.SECONDS)
                val resultOutput = getProcess.inputStream.bufferedReader().readText()

                if (resultOutput.contains("\"Success\"") || resultOutput.contains("\"Failed\"")) {
                    // Parse JSON output
                    val stdoutMatch = Regex("\"([^\"]*?)\"").findAll(resultOutput).toList()
                    val stdout = if (stdoutMatch.size > 0) stdoutMatch[0].groupValues[1].replace("\\n", "\n") else ""
                    val stderr = if (stdoutMatch.size > 1) stdoutMatch[1].groupValues[1].replace("\\n", "\n") else ""
                    val status = if (stdoutMatch.size > 2) stdoutMatch[2].groupValues[1] else ""

                    val duration = System.currentTimeMillis() - startTime
                    return ExecutionResult(
                        success = status == "Success",
                        output = stdout,
                        errorOutput = stderr,
                        exitCode = if (status == "Success") 0 else 1,
                        duration = duration
                    )
                }

                Thread.sleep(2000)
                attempts++
            }

            ExecutionResult.failure("AWS SSM command timed out after $timeout seconds")
        } catch (e: Exception) {
            Log.e("AWS SSM command execution failed: ${e.message}")
            ExecutionResult.failure("AWS SSM execution error: ${e.message}")
        }
    }

    override fun doUploadFile(localPath: String, remotePath: String): TransferResult {
        return try {
            val source = File(localPath)
            if (!source.exists()) {
                return TransferResult.failure("Source file not found: $localPath")
            }

            // Upload file to S3 bucket temporarily, then copy to instance
            // For simplicity, we'll use base64 encoding and write via command
            val fileContent = source.readText()
            val encodedContent = java.util.Base64.getEncoder().encodeToString(fileContent.toByteArray())

            val writeCommand = "echo '$encodedContent' | base64 -d > $remotePath"
            val result = doExecute(writeCommand, 300)

            if (result.success) {
                TransferResult.success(source.length())
            } else {
                TransferResult.failure("AWS SSM file upload failed: ${result.errorOutput}")
            }
        } catch (e: Exception) {
            Log.e("AWS SSM file upload failed: ${e.message}")
            TransferResult.failure("Upload error: ${e.message}")
        }
    }

    override fun doDownloadFile(remotePath: String, localPath: String): TransferResult {
        return try {
            val destination = File(localPath)
            destination.parentFile?.mkdirs()

            // Read file content via command and decode
            val readCommand = "base64 $remotePath"
            val result = doExecute(readCommand, 300)

            if (result.success && result.output.isNotEmpty()) {
                val decodedContent = java.util.Base64.getDecoder().decode(result.output.trim())
                destination.writeBytes(decodedContent)
                TransferResult.success(destination.length())
            } else {
                TransferResult.failure("AWS SSM file download failed: ${result.errorOutput}")
            }
        } catch (e: Exception) {
            Log.e("AWS SSM file download failed: ${e.message}")
            TransferResult.failure("Download error: ${e.message}")
        }
    }

    override fun doDisconnect() {
        // SSM sessions are stateless
        Log.d("AWS SSM connection closed")
    }

    /**
     * Builds AWS CLI command with region and profile options.
     */
    private fun buildAWSCommand(args: List<String>): List<String> {
        val cmd = mutableListOf("aws")

        // Add region if specified
        region?.let {
            cmd.add("--region")
            cmd.add(it)
        }

        // Add profile if specified
        profile?.let {
            cmd.add("--profile")
            cmd.add(it)
        }

        // Add remaining arguments
        cmd.addAll(args)

        return cmd
    }

    override fun buildMetadataProperties(): Map<String, String> {
        return super.buildMetadataProperties() + mapOf(
            "protocol" to "AWS-SSM",
            "instanceId" to instanceId,
            "region" to (region ?: "default"),
            "profile" to (profile ?: "default"),
            "documentName" to documentName
        )
    }
}
