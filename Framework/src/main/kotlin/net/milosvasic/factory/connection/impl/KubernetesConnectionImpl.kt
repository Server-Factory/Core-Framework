package net.milosvasic.factory.connection.impl

import net.milosvasic.factory.connection.*
import net.milosvasic.logger.Log
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Kubernetes pod execution connection.
 *
 * Executes commands in Kubernetes pods using kubectl exec.
 * Useful for managing containerized workloads in Kubernetes clusters.
 *
 * Requirements:
 * - kubectl CLI installed and configured
 * - Access to target Kubernetes cluster
 * - Pod must exist and be running
 *
 * Configuration properties:
 * - podName: Target pod name (required, can use host field)
 * - namespace: Kubernetes namespace (optional, default: default)
 * - container: Container name in pod (optional, uses first container if not specified)
 * - context: Kubernetes context to use (optional)
 *
 * @author Mail Server Factory Team
 * @since 3.1.0
 */
class KubernetesConnectionImpl(config: ConnectionConfig) : BaseConnection(config) {

    private val podName: String
    private val namespace: String
    private val container: String?
    private val context: String?

    init {
        podName = config.host.takeIf { it.isNotEmpty() }
            ?: config.options.getProperty("podName")
            .takeIf { it.isNotEmpty() }
            ?: throw IllegalArgumentException("Pod name required")

        namespace = config.options.getProperty("namespace", "default")
        container = config.options.getProperty("container").takeIf { it.isNotEmpty() }
        context = config.options.getProperty("context").takeIf { it.isNotEmpty() }
    }

    override fun doConnect(): ConnectionResult {
        return try {
            // Check if pod exists and is running
            val getCmd = buildKubectlCommand(listOf("get", "pod", podName, "-o", "jsonpath={.status.phase}"))
            val process = ProcessBuilder(getCmd)
                .redirectErrorStream(false)
                .start()

            val completed = process.waitFor(15, TimeUnit.SECONDS)
            if (!completed) {
                process.destroy()
                return ConnectionResult.Failure("kubectl get pod timed out")
            }

            val output = process.inputStream.bufferedReader().readText().trim()
            val exitCode = process.exitValue()

            if (exitCode == 0 && output == "Running") {
                Log.i("Kubernetes connection established to pod: $podName in namespace: $namespace")
                ConnectionResult.Success("Kubernetes pod connection established")
            } else if (exitCode == 0) {
                ConnectionResult.Failure("Pod $podName exists but is not running (status: $output)")
            } else {
                ConnectionResult.Failure("Pod $podName not found in namespace $namespace")
            }
        } catch (e: Exception) {
            Log.e("Kubernetes connection failed: ${e.message}")
            ConnectionResult.Failure("Kubernetes connection failed: ${e.message}", e)
        }
    }

    override fun doExecute(command: String, timeout: Int): ExecutionResult {
        return try {
            val startTime = System.currentTimeMillis()
            val kubectlCmd = buildKubectlExecCommand(command)

            val process = ProcessBuilder(kubectlCmd)
                .redirectErrorStream(false)
                .start()

            val completed = process.waitFor(timeout.toLong(), TimeUnit.SECONDS)

            if (!completed) {
                process.destroy()
                return ExecutionResult.failure("kubectl exec timed out after $timeout seconds")
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
            Log.e("kubectl exec failed: ${e.message}")
            ExecutionResult.failure("kubectl exec error: ${e.message}")
        }
    }

    override fun doUploadFile(localPath: String, remotePath: String): TransferResult {
        return try {
            val source = File(localPath)
            if (!source.exists()) {
                return TransferResult.failure("Source file not found: $localPath")
            }

            // Use kubectl cp to copy file into pod
            val cpCmd = buildKubectlCpCommand(localPath, remotePath, upload = true)
            val process = ProcessBuilder(cpCmd)
                .redirectErrorStream(false)
                .start()

            val completed = process.waitFor(300, TimeUnit.SECONDS)

            if (!completed) {
                process.destroy()
                return TransferResult.failure("kubectl cp timed out")
            }

            val errorOutput = process.errorStream.bufferedReader().readText()
            val exitCode = process.exitValue()

            if (exitCode == 0) {
                TransferResult.success(source.length())
            } else {
                TransferResult.failure("kubectl cp failed: $errorOutput")
            }
        } catch (e: Exception) {
            Log.e("Kubernetes file upload failed: ${e.message}")
            TransferResult.failure("Upload error: ${e.message}")
        }
    }

    override fun doDownloadFile(remotePath: String, localPath: String): TransferResult {
        return try {
            val destination = File(localPath)
            destination.parentFile?.mkdirs()

            // Use kubectl cp to copy file from pod
            val cpCmd = buildKubectlCpCommand(remotePath, localPath, upload = false)
            val process = ProcessBuilder(cpCmd)
                .redirectErrorStream(false)
                .start()

            val completed = process.waitFor(300, TimeUnit.SECONDS)

            if (!completed) {
                process.destroy()
                return TransferResult.failure("kubectl cp timed out")
            }

            val errorOutput = process.errorStream.bufferedReader().readText()
            val exitCode = process.exitValue()

            if (exitCode == 0 && destination.exists()) {
                TransferResult.success(destination.length())
            } else {
                TransferResult.failure("kubectl cp failed: $errorOutput")
            }
        } catch (e: Exception) {
            Log.e("Kubernetes file download failed: ${e.message}")
            TransferResult.failure("Download error: ${e.message}")
        }
    }

    override fun doDisconnect() {
        // kubectl is stateless
        Log.d("Kubernetes connection closed")
    }

    /**
     * Builds kubectl command with context and namespace.
     */
    private fun buildKubectlCommand(args: List<String>): List<String> {
        val cmd = mutableListOf("kubectl")

        // Add context if specified
        context?.let {
            cmd.add("--context")
            cmd.add(it)
        }

        // Add namespace
        cmd.add("--namespace")
        cmd.add(namespace)

        // Add remaining arguments
        cmd.addAll(args)

        return cmd
    }

    /**
     * Builds kubectl exec command.
     */
    private fun buildKubectlExecCommand(command: String): List<String> {
        val args = mutableListOf("exec", "-i", podName)

        // Add container if specified
        container?.let {
            args.add("-c")
            args.add(it)
        }

        // Add command separator and shell command
        args.add("--")
        args.add("sh")
        args.add("-c")
        args.add(command)

        return buildKubectlCommand(args)
    }

    /**
     * Builds kubectl cp command.
     */
    private fun buildKubectlCpCommand(source: String, destination: String, upload: Boolean): List<String> {
        val args = mutableListOf("cp")

        // Add container if specified
        container?.let {
            args.add("-c")
            args.add(it)
        }

        // Add source and destination
        if (upload) {
            args.add(source)
            args.add("$podName:$destination")
        } else {
            args.add("$podName:$source")
            args.add(destination)
        }

        return buildKubectlCommand(args)
    }

    override fun buildMetadataProperties(): Map<String, String> {
        return super.buildMetadataProperties() + mapOf(
            "protocol" to "Kubernetes",
            "podName" to podName,
            "namespace" to namespace,
            "container" to (container ?: "default"),
            "context" to (context ?: "current")
        )
    }
}
