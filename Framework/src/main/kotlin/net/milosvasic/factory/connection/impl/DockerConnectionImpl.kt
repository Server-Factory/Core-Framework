package net.milosvasic.factory.connection.impl

import net.milosvasic.factory.connection.*
import net.milosvasic.logger.Log
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Docker container execution connection.
 *
 * Executes commands inside Docker containers using docker exec.
 * Useful for managing containerized services and applications.
 *
 * Requirements:
 * - Docker CLI installed
 * - Docker daemon running
 * - Container must exist and be running
 *
 * Configuration properties:
 * - containerId or containerName: Target container
 * - workdir: Working directory inside container (optional)
 * - user: User to execute as (optional, default: root)
 *
 * @author Mail Server Factory Team
 * @since 3.1.0
 */
class DockerConnectionImpl(config: ConnectionConfig) : BaseConnection(config) {

    private val containerId: String
    private val workdir: String?
    private val execUser: String

    init {
        // Container ID from host field or properties
        containerId = config.host.takeIf { it.isNotEmpty() }
            ?: config.options.getProperty("containerId")
            .takeIf { it.isNotEmpty() }
            ?: config.options.getProperty("containerName")
            .takeIf { it.isNotEmpty() }
            ?: throw IllegalArgumentException("Container ID or name required")

        workdir = config.options.getProperty("workdir").takeIf { it.isNotEmpty() }
        execUser = config.options.getProperty("user", "root")
    }

    override fun doConnect(): ConnectionResult {
        return try {
            // Check if container exists and is running
            val inspectCmd = listOf("docker", "inspect", "--format={{.State.Running}}", containerId)
            val process = ProcessBuilder(inspectCmd)
                .redirectErrorStream(false)
                .start()

            val completed = process.waitFor(10, TimeUnit.SECONDS)
            if (!completed) {
                process.destroy()
                return ConnectionResult.Failure("Docker inspect timed out")
            }

            val output = process.inputStream.bufferedReader().readText().trim()
            val exitCode = process.exitValue()

            if (exitCode == 0 && output == "true") {
                Log.i("Docker connection established to container: $containerId")
                ConnectionResult.Success("Docker container connection established")
            } else if (exitCode == 0 && output == "false") {
                ConnectionResult.Failure("Container $containerId exists but is not running")
            } else {
                ConnectionResult.Failure("Container $containerId not found")
            }
        } catch (e: Exception) {
            Log.e("Docker connection failed: ${e.message}")
            ConnectionResult.Failure("Docker connection failed: ${e.message}", e)
        }
    }

    override fun doExecute(command: String, timeout: Int): ExecutionResult {
        return try {
            val startTime = System.currentTimeMillis()
            val dockerCmd = buildDockerExecCommand(command)

            val process = ProcessBuilder(dockerCmd)
                .redirectErrorStream(false)
                .start()

            val completed = process.waitFor(timeout.toLong(), TimeUnit.SECONDS)

            if (!completed) {
                process.destroy()
                return ExecutionResult.failure("Docker exec timed out after $timeout seconds")
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
            Log.e("Docker exec failed: ${e.message}")
            ExecutionResult.failure("Docker exec error: ${e.message}")
        }
    }

    override fun doUploadFile(localPath: String, remotePath: String): TransferResult {
        return try {
            val source = File(localPath)
            if (!source.exists()) {
                return TransferResult.failure("Source file not found: $localPath")
            }

            // Use docker cp to copy file into container
            val cpCmd = listOf("docker", "cp", localPath, "$containerId:$remotePath")
            val process = ProcessBuilder(cpCmd)
                .redirectErrorStream(false)
                .start()

            val completed = process.waitFor(300, TimeUnit.SECONDS)

            if (!completed) {
                process.destroy()
                return TransferResult.failure("Docker cp timed out")
            }

            val errorOutput = process.errorStream.bufferedReader().readText()
            val exitCode = process.exitValue()

            if (exitCode == 0) {
                TransferResult.success(source.length())
            } else {
                TransferResult.failure("Docker cp failed: $errorOutput")
            }
        } catch (e: Exception) {
            Log.e("Docker file upload failed: ${e.message}")
            TransferResult.failure("Upload error: ${e.message}")
        }
    }

    override fun doDownloadFile(remotePath: String, localPath: String): TransferResult {
        return try {
            val destination = File(localPath)
            destination.parentFile?.mkdirs()

            // Use docker cp to copy file from container
            val cpCmd = listOf("docker", "cp", "$containerId:$remotePath", localPath)
            val process = ProcessBuilder(cpCmd)
                .redirectErrorStream(false)
                .start()

            val completed = process.waitFor(300, TimeUnit.SECONDS)

            if (!completed) {
                process.destroy()
                return TransferResult.failure("Docker cp timed out")
            }

            val errorOutput = process.errorStream.bufferedReader().readText()
            val exitCode = process.exitValue()

            if (exitCode == 0 && destination.exists()) {
                TransferResult.success(destination.length())
            } else {
                TransferResult.failure("Docker cp failed: $errorOutput")
            }
        } catch (e: Exception) {
            Log.e("Docker file download failed: ${e.message}")
            TransferResult.failure("Download error: ${e.message}")
        }
    }

    override fun doDisconnect() {
        // Docker exec is stateless
        Log.d("Docker connection closed")
    }

    /**
     * Builds docker exec command.
     */
    private fun buildDockerExecCommand(command: String): List<String> {
        val dockerCmd = mutableListOf("docker", "exec")

        // Add interactive flag for proper output
        dockerCmd.add("-i")

        // Add user if specified
        if (execUser != "root") {
            dockerCmd.add("--user")
            dockerCmd.add(execUser)
        }

        // Add working directory if specified
        workdir?.let {
            dockerCmd.add("--workdir")
            dockerCmd.add(it)
        }

        // Add container ID
        dockerCmd.add(containerId)

        // Add shell and command
        dockerCmd.add("sh")
        dockerCmd.add("-c")
        dockerCmd.add(command)

        return dockerCmd
    }

    override fun buildMetadataProperties(): Map<String, String> {
        return super.buildMetadataProperties() + mapOf(
            "protocol" to "Docker",
            "containerId" to containerId,
            "user" to execUser,
            "workdir" to (workdir ?: "default")
        )
    }
}
