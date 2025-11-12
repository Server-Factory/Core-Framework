package net.milosvasic.factory.connection.impl

import net.milosvasic.factory.connection.*
import net.milosvasic.logger.Log
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Ansible connection implementation for remote execution.
 *
 * Executes commands and manages files using Ansible ad-hoc commands and modules.
 * Provides idempotent, declarative remote management with extensive module support.
 *
 * Requirements:
 * - Ansible installed on the system (ansible, ansible-playbook)
 * - SSH access configured to target host
 * - Ansible inventory file or dynamic inventory
 * - Python installed on target host
 *
 * Configuration properties:
 * - inventoryPath: Path to Ansible inventory file (optional, creates temp if not specified)
 * - connection: Ansible connection type (default: ssh)
 * - becomeMethod: Privilege escalation method (default: sudo)
 * - becomeUser: User to become (default: root)
 * - privateKeyPath: SSH key for authentication (optional)
 * - extraVars: Additional Ansible variables as JSON (optional)
 *
 * @author Mail Server Factory Team
 * @since 3.1.0
 */
class AnsibleConnectionImpl(config: ConnectionConfig) : BaseConnection(config) {

    private val username = config.credentials?.username ?: "root"
    private val inventoryPath: String?
    private val tempInventoryFile: File?
    private val connectionType: String
    private val becomeMethod: String
    private val becomeUser: String
    private val privateKeyPath: String?
    private val extraVars: String?

    init {
        inventoryPath = config.options.getProperty("inventoryPath").takeIf { it.isNotEmpty() }
        connectionType = config.options.getProperty("connection", "ssh")
        becomeMethod = config.options.getProperty("becomeMethod", "sudo")
        becomeUser = config.options.getProperty("becomeUser", "root")
        privateKeyPath = config.credentials?.keyPath.takeIf { it?.isNotEmpty() == true }
        extraVars = config.options.getProperty("extraVars").takeIf { it.isNotEmpty() }

        // Create temporary inventory file if not provided
        tempInventoryFile = if (inventoryPath == null) {
            createTempInventory()
        } else {
            null
        }
    }

    override fun doConnect(): ConnectionResult {
        return try {
            // Test Ansible connection using ping module
            val pingCommand = buildAnsibleCommand("ping", emptyMap())
            val process = ProcessBuilder(pingCommand)
                .redirectErrorStream(false)
                .start()

            val completed = process.waitFor(30, TimeUnit.SECONDS)
            if (!completed) {
                process.destroy()
                return ConnectionResult.Failure("Ansible ping timed out")
            }

            val output = process.inputStream.bufferedReader().readText()
            val errorOutput = process.errorStream.bufferedReader().readText()
            val exitCode = process.exitValue()

            if (exitCode == 0 && output.contains("SUCCESS")) {
                Log.i("Ansible connection established to ${config.host}")
                ConnectionResult.Success("Ansible connection established")
            } else {
                ConnectionResult.Failure("Ansible ping failed: $errorOutput")
            }
        } catch (e: Exception) {
            Log.e("Ansible connection failed: ${e.message}")
            ConnectionResult.Failure("Ansible connection failed: ${e.message}", e)
        }
    }

    override fun doExecute(command: String, timeout: Int): ExecutionResult {
        return try {
            val startTime = System.currentTimeMillis()

            // Use Ansible shell module to execute command
            val moduleArgs = mapOf("_raw_params" to command)
            val ansibleCommand = buildAnsibleCommand("shell", moduleArgs)

            val process = ProcessBuilder(ansibleCommand)
                .redirectErrorStream(false)
                .start()

            val completed = process.waitFor(timeout.toLong(), TimeUnit.SECONDS)

            if (!completed) {
                process.destroy()
                return ExecutionResult.failure("Ansible command timed out after $timeout seconds")
            }

            val output = process.inputStream.bufferedReader().readText()
            val errorOutput = process.errorStream.bufferedReader().readText()
            val exitCode = process.exitValue()
            val duration = System.currentTimeMillis() - startTime

            // Parse Ansible JSON output if available
            val (stdout, stderr) = parseAnsibleOutput(output, errorOutput)

            ExecutionResult(
                success = exitCode == 0,
                output = stdout,
                errorOutput = stderr,
                exitCode = exitCode,
                duration = duration
            )
        } catch (e: Exception) {
            Log.e("Ansible command execution failed: ${e.message}")
            ExecutionResult.failure("Ansible execution error: ${e.message}")
        }
    }

    override fun doUploadFile(localPath: String, remotePath: String): TransferResult {
        return try {
            val source = File(localPath)
            if (!source.exists()) {
                return TransferResult.failure("Source file not found: $localPath")
            }

            // Use Ansible copy module
            val moduleArgs = mapOf(
                "src" to localPath,
                "dest" to remotePath
            )
            val ansibleCommand = buildAnsibleCommand("copy", moduleArgs)

            val process = ProcessBuilder(ansibleCommand)
                .redirectErrorStream(false)
                .start()

            val completed = process.waitFor(300, TimeUnit.SECONDS)

            if (!completed) {
                process.destroy()
                return TransferResult.failure("Ansible copy timed out")
            }

            val output = process.inputStream.bufferedReader().readText()
            val errorOutput = process.errorStream.bufferedReader().readText()
            val exitCode = process.exitValue()

            if (exitCode == 0) {
                TransferResult.success(source.length())
            } else {
                TransferResult.failure("Ansible copy failed: $errorOutput")
            }
        } catch (e: Exception) {
            Log.e("Ansible file upload failed: ${e.message}")
            TransferResult.failure("Upload error: ${e.message}")
        }
    }

    override fun doDownloadFile(remotePath: String, localPath: String): TransferResult {
        return try {
            val destination = File(localPath)
            destination.parentFile?.mkdirs()

            // Use Ansible fetch module
            val moduleArgs = mapOf(
                "src" to remotePath,
                "dest" to localPath,
                "flat" to "yes"
            )
            val ansibleCommand = buildAnsibleCommand("fetch", moduleArgs)

            val process = ProcessBuilder(ansibleCommand)
                .redirectErrorStream(false)
                .start()

            val completed = process.waitFor(300, TimeUnit.SECONDS)

            if (!completed) {
                process.destroy()
                return TransferResult.failure("Ansible fetch timed out")
            }

            val output = process.inputStream.bufferedReader().readText()
            val errorOutput = process.errorStream.bufferedReader().readText()
            val exitCode = process.exitValue()

            if (exitCode == 0 && destination.exists()) {
                TransferResult.success(destination.length())
            } else {
                TransferResult.failure("Ansible fetch failed: $errorOutput")
            }
        } catch (e: Exception) {
            Log.e("Ansible file download failed: ${e.message}")
            TransferResult.failure("Download error: ${e.message}")
        }
    }

    override fun doDisconnect() {
        // Clean up temporary inventory file
        tempInventoryFile?.delete()
        Log.d("Ansible connection closed")
    }

    /**
     * Creates a temporary inventory file for the target host.
     */
    private fun createTempInventory(): File {
        val tempFile = File.createTempFile("ansible-inventory-", ".ini")
        tempFile.deleteOnExit()

        val inventoryContent = buildString {
            appendLine("[targets]")
            append("${config.host}")

            if (config.port != 22) {
                append(" ansible_port=${config.port}")
            }
            if (username != "root") {
                append(" ansible_user=$username")
            }
            privateKeyPath?.let {
                append(" ansible_ssh_private_key_file=$it")
            }
            appendLine()
        }

        tempFile.writeText(inventoryContent)
        return tempFile
    }

    /**
     * Builds an Ansible ad-hoc command.
     */
    private fun buildAnsibleCommand(module: String, moduleArgs: Map<String, String>): List<String> {
        val cmd = mutableListOf("ansible")

        // Add inventory
        cmd.add("-i")
        cmd.add(inventoryPath ?: tempInventoryFile!!.absolutePath)

        // Add target host pattern
        cmd.add(config.host)

        // Add connection type
        cmd.add("--connection")
        cmd.add(connectionType)

        // Add user if specified
        if (username != "root") {
            cmd.add("-u")
            cmd.add(username)
        }

        // Add private key if specified
        privateKeyPath?.let {
            cmd.add("--private-key")
            cmd.add(it)
        }

        // Add become options if needed
        if (becomeUser != username) {
            cmd.add("--become")
            cmd.add("--become-method")
            cmd.add(becomeMethod)
            cmd.add("--become-user")
            cmd.add(becomeUser)
        }

        // Add extra vars if specified
        extraVars?.let {
            cmd.add("-e")
            cmd.add(it)
        }

        // Add module and arguments
        cmd.add("-m")
        cmd.add(module)

        if (moduleArgs.isNotEmpty()) {
            cmd.add("-a")
            val argsString = moduleArgs.entries.joinToString(" ") { (k, v) ->
                if (k == "_raw_params") v else "$k=$v"
            }
            cmd.add(argsString)
        }

        return cmd
    }

    /**
     * Executes an Ansible playbook.
     * 
     * @param playbookPath Path to the playbook file
     * @param extraVars Additional variables to pass to the playbook
     * @param limit Host pattern to limit execution (optional)
     * @param tags List of tags to execute (optional)
     * @param skipTags List of tags to skip (optional)
     * @return ExecutionResult with success status and output
     */
    fun executePlaybook(
        playbookPath: String,
        extraVars: Map<String, Any> = emptyMap(),
        limit: String? = null,
        tags: List<String> = emptyList(),
        skipTags: List<String> = emptyList()
    ): ExecutionResult {
        return try {
            val cmd = mutableListOf("ansible-playbook")
            
            // Add inventory
            cmd.add("-i")
            cmd.add(inventoryPath ?: tempInventoryFile!!.absolutePath)
            
            // Add target host pattern if limiting
            limit?.let {
                cmd.add("--limit")
                cmd.add(it)
            }
            
            // Add connection type
            cmd.add("--connection")
            cmd.add(connectionType)
            
            // Add user if specified
            if (username != "root") {
                cmd.add("-u")
                cmd.add(username)
            }
            
            // Add private key if specified
            privateKeyPath?.let {
                cmd.add("--private-key")
                cmd.add(it)
            }
            
            // Add become options if needed
            if (becomeUser != username) {
                cmd.add("--become")
                cmd.add("--become-method")
                cmd.add(becomeMethod)
                cmd.add("--become-user")
                cmd.add(becomeUser)
            }
            
            // Add extra vars from config and parameters
            val allExtraVars = mutableMapOf<String, Any>()
            
            // Add config extra vars if they're JSON
            extraVars?.let { vars ->
                if (vars.isNotEmpty()) {
                    allExtraVars.putAll(vars)
                }
            }
            
            if (allExtraVars.isNotEmpty()) {
                val varsJson = allExtraVars.entries.joinToString(",") { (k, v) -> "\"$k\":\"$v\"" }
                cmd.add("-e")
                cmd.add("{$varsJson}")
            }
            
            // Add tags if specified
            if (tags.isNotEmpty()) {
                cmd.add("--tags")
                cmd.add(tags.joinToString(","))
            }
            
            // Add skip tags if specified
            if (skipTags.isNotEmpty()) {
                cmd.add("--skip-tags")
                cmd.add(skipTags.joinToString(","))
            }
            
            // Add playbook path
            cmd.add(playbookPath)
            
            val startTime = System.currentTimeMillis()
            val process = ProcessBuilder(cmd)
                .redirectErrorStream(false)
                .start()
            
            val completed = process.waitFor(600, TimeUnit.SECONDS) // 10 minute timeout for playbooks
            
            if (!completed) {
                process.destroy()
                return ExecutionResult.failure("Ansible playbook execution timed out")
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
            Log.e("Ansible playbook execution failed: ${e.message}")
            ExecutionResult.failure("Playbook execution error: ${e.message}")
        }
    }

    /**
     * Parses Ansible output to extract stdout and stderr.
     */
    private fun parseAnsibleOutput(output: String, errorOutput: String): Pair<String, String> {
        // Try to extract output from Ansible's verbose format
        val stdoutPattern = Regex("\"stdout\":\\s*\"([^\"]*?)\"")
        val stderrPattern = Regex("\"stderr\":\\s*\"([^\"]*?)\"")

        val stdout = stdoutPattern.find(output)?.groupValues?.get(1)?.replace("\\n", "\n") ?: output
        val stderr = stderrPattern.find(output)?.groupValues?.get(1)?.replace("\\n", "\n") ?: errorOutput

        return Pair(stdout, stderr)
    }

    override fun buildMetadataProperties(): Map<String, String> {
        return super.buildMetadataProperties() + mapOf(
            "protocol" to "Ansible",
            "connection" to connectionType,
            "becomeMethod" to becomeMethod,
            "becomeUser" to becomeUser,
            "hasInventory" to (inventoryPath != null).toString(),
            "hasExtraVars" to (extraVars != null).toString()
        )
    }
}
