package net.milosvasic.factory.connection.impl

import net.milosvasic.factory.connection.*
import net.milosvasic.logger.Log
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Google Cloud Platform OS Login connection implementation.
 *
 * Provides SSH connectivity to GCP Compute Engine instances using OS Login.
 * OS Login centralizes SSH key management using IAM and provides automated
 * user provisioning based on Google identity.
 *
 * Requirements:
 * - gcloud CLI installed and configured
 * - OS Login enabled on GCP project and instance
 * - IAM permissions (compute.osLogin or compute.osAdminLogin)
 * - SSH key added to OS Login profile
 *
 * Configuration properties:
 * - instanceName: GCE instance name (required, can use host field)
 * - zone: GCP zone (optional, uses default from gcloud config)
 * - project: GCP project ID (optional, uses default from gcloud config)
 * - username: OS Login username (optional, auto-detected from gcloud)
 * - internalIP: Use internal IP instead of external (default: false)
 *
 * @author Mail Server Factory Team
 * @since 3.1.0
 */
class GCPOSLoginConnectionImpl(config: ConnectionConfig) : BaseConnection(config) {

    private val instanceName: String
    private val zone: String?
    private val project: String?
    private val username: String?
    private val useInternalIP: Boolean

    init {
        // Instance name from host field or properties
        instanceName = config.host.takeIf { it.isNotEmpty() }
            ?: config.options.getProperty("instanceName")
            .takeIf { it.isNotEmpty() }
            ?: throw IllegalArgumentException("GCP instance name is required")

        zone = config.options.getProperty("zone").takeIf { it.isNotEmpty() }
        project = config.options.getProperty("project").takeIf { it.isNotEmpty() }
        username = config.credentials?.username.takeIf { it?.isNotEmpty() == true }
        useInternalIP = config.options.getProperty("internalIP", "false").toBoolean()
    }

    override fun doConnect(): ConnectionResult {
        return try {
            // Test connection using gcloud compute ssh
            val sshCmd = buildGcloudSSHCommand("echo 'connected'")
            val process = ProcessBuilder(sshCmd)
                .redirectErrorStream(false)
                .start()

            val completed = process.waitFor(30, TimeUnit.SECONDS)
            if (!completed) {
                process.destroy()
                return ConnectionResult.Failure("GCP OS Login connection test timed out")
            }

            val output = process.inputStream.bufferedReader().readText()
            val errorOutput = process.errorStream.bufferedReader().readText()
            val exitCode = process.exitValue()

            if (exitCode == 0 && output.contains("connected")) {
                Log.i("GCP OS Login connection established to instance: $instanceName")
                ConnectionResult.Success("GCP OS Login connection established")
            } else {
                ConnectionResult.Failure("GCP OS Login connection test failed: $errorOutput")
            }
        } catch (e: Exception) {
            Log.e("GCP OS Login connection failed: ${e.message}")
            ConnectionResult.Failure("GCP OS Login connection failed: ${e.message}", e)
        }
    }

    override fun doExecute(command: String, timeout: Int): ExecutionResult {
        return try {
            val startTime = System.currentTimeMillis()
            val sshCmd = buildGcloudSSHCommand(command)

            val process = ProcessBuilder(sshCmd)
                .redirectErrorStream(false)
                .start()

            val completed = process.waitFor(timeout.toLong(), TimeUnit.SECONDS)

            if (!completed) {
                process.destroy()
                return ExecutionResult.failure("GCP command timed out after $timeout seconds")
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
            Log.e("GCP OS Login command execution failed: ${e.message}")
            ExecutionResult.failure("GCP OS Login execution error: ${e.message}")
        }
    }

    override fun doUploadFile(localPath: String, remotePath: String): TransferResult {
        return try {
            val source = File(localPath)
            if (!source.exists()) {
                return TransferResult.failure("Source file not found: $localPath")
            }

            // Use gcloud compute scp
            val scpCmd = buildGcloudSCPCommand(localPath, remotePath, upload = true)
            val process = ProcessBuilder(scpCmd)
                .redirectErrorStream(false)
                .start()

            val completed = process.waitFor(300, TimeUnit.SECONDS)

            if (!completed) {
                process.destroy()
                return TransferResult.failure("GCP file upload timed out")
            }

            val errorOutput = process.errorStream.bufferedReader().readText()
            val exitCode = process.exitValue()

            if (exitCode == 0) {
                TransferResult.success(source.length())
            } else {
                TransferResult.failure("GCP file upload failed: $errorOutput")
            }
        } catch (e: Exception) {
            Log.e("GCP OS Login file upload failed: ${e.message}")
            TransferResult.failure("Upload error: ${e.message}")
        }
    }

    override fun doDownloadFile(remotePath: String, localPath: String): TransferResult {
        return try {
            val destination = File(localPath)
            destination.parentFile?.mkdirs()

            // Use gcloud compute scp
            val scpCmd = buildGcloudSCPCommand(remotePath, localPath, upload = false)
            val process = ProcessBuilder(scpCmd)
                .redirectErrorStream(false)
                .start()

            val completed = process.waitFor(300, TimeUnit.SECONDS)

            if (!completed) {
                process.destroy()
                return TransferResult.failure("GCP file download timed out")
            }

            val errorOutput = process.errorStream.bufferedReader().readText()
            val exitCode = process.exitValue()

            if (exitCode == 0 && destination.exists()) {
                TransferResult.success(destination.length())
            } else {
                TransferResult.failure("GCP file download failed: $errorOutput")
            }
        } catch (e: Exception) {
            Log.e("GCP OS Login file download failed: ${e.message}")
            TransferResult.failure("Download error: ${e.message}")
        }
    }

    override fun doDisconnect() {
        // gcloud commands are stateless
        Log.d("GCP OS Login connection closed")
    }

    /**
     * Builds gcloud compute ssh command.
     */
    private fun buildGcloudSSHCommand(command: String): List<String> {
        val cmd = mutableListOf("gcloud", "compute", "ssh")

        // Build instance identifier
        val instanceId = buildInstanceIdentifier()
        cmd.add(instanceId)

        // Add zone if specified
        zone?.let {
            cmd.add("--zone")
            cmd.add(it)
        }

        // Add project if specified
        project?.let {
            cmd.add("--project")
            cmd.add(it)
        }

        // Add internal IP flag if requested
        if (useInternalIP) {
            cmd.add("--internal-ip")
        }

        // Add quiet mode to suppress prompts
        cmd.add("--quiet")

        // Add command
        cmd.add("--command")
        cmd.add(command)

        return cmd
    }

    /**
     * Builds gcloud compute scp command.
     */
    private fun buildGcloudSCPCommand(source: String, destination: String, upload: Boolean): List<String> {
        val cmd = mutableListOf("gcloud", "compute", "scp")

        // Add zone if specified
        zone?.let {
            cmd.add("--zone")
            cmd.add(it)
        }

        // Add project if specified
        project?.let {
            cmd.add("--project")
            cmd.add(it)
        }

        // Add internal IP flag if requested
        if (useInternalIP) {
            cmd.add("--internal-ip")
        }

        // Add quiet mode
        cmd.add("--quiet")

        // Build instance identifier
        val instanceId = buildInstanceIdentifier()

        // Add source and destination
        if (upload) {
            cmd.add(source)
            cmd.add("$instanceId:$destination")
        } else {
            cmd.add("$instanceId:$source")
            cmd.add(destination)
        }

        return cmd
    }

    /**
     * Builds instance identifier for gcloud commands.
     */
    private fun buildInstanceIdentifier(): String {
        return if (username != null) {
            "$username@$instanceName"
        } else {
            instanceName
        }
    }

    override fun buildMetadataProperties(): Map<String, String> {
        return super.buildMetadataProperties() + mapOf(
            "protocol" to "GCP-OSLogin",
            "instanceName" to instanceName,
            "zone" to (zone ?: "default"),
            "project" to (project ?: "default"),
            "username" to (username ?: "auto"),
            "useInternalIP" to useInternalIP.toString()
        )
    }
}
