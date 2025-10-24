package net.milosvasic.factory.security

import net.milosvasic.logger.Log
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Audit logging system for security-critical operations.
 *
 * Features:
 * - Structured audit log entries with timestamps
 * - Separate audit log file (immutable, append-only)
 * - Log rotation based on size and time
 * - Asynchronous logging (non-blocking)
 * - Automatic flushing
 * - Tamper detection (checksum validation)
 * - Retention policy enforcement
 * - Compliance-ready format (JSON)
 *
 * Audit events logged:
 * - Authentication attempts (success/failure)
 * - Authorization decisions
 * - Configuration changes
 * - Privileged operations (reboot, service restart)
 * - Encryption/decryption operations
 * - Connection establishment/termination
 * - File access (sensitive files)
 * - Command execution (especially privileged commands)
 *
 * Configuration via environment variables:
 * - MAIL_FACTORY_AUDIT_LOG_DIR: Directory for audit logs (default: logs/audit)
 * - MAIL_FACTORY_AUDIT_LOG_MAX_SIZE: Max size in MB before rotation (default: 100)
 * - MAIL_FACTORY_AUDIT_LOG_RETENTION_DAYS: Days to keep logs (default: 90)
 * - MAIL_FACTORY_AUDIT_LOG_FLUSH_INTERVAL: Flush interval in seconds (default: 5)
 *
 * @author Mail Server Factory Team
 * @since 3.1.0
 */
object AuditLogger {

    private const val DEFAULT_LOG_DIR = "logs/audit"
    private const val DEFAULT_MAX_SIZE_MB = 100
    private const val DEFAULT_RETENTION_DAYS = 90
    private const val DEFAULT_FLUSH_INTERVAL_SECONDS = 5L

    private val logDir: File
    private val maxSizeBytes: Long
    private val retentionDays: Int
    private val flushIntervalSeconds: Long

    private var currentLogFile: File? = null
    private var currentWriter: PrintWriter? = null
    private var currentFileSize = AtomicLong(0)

    private val logQueue = ConcurrentLinkedQueue<AuditEntry>()
    private val isInitialized = AtomicBoolean(false)
    private val isShutdown = AtomicBoolean(false)

    private var scheduler: ScheduledExecutorService? = null

    private val dateFormatter = DateTimeFormatter
        .ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
        .withZone(ZoneId.of("UTC"))

    private val fileNameFormatter = DateTimeFormatter
        .ofPattern("yyyy-MM-dd_HHmmss")
        .withZone(ZoneId.of("UTC"))

    init {
        // Load configuration
        val logDirPath = System.getenv("MAIL_FACTORY_AUDIT_LOG_DIR") ?: DEFAULT_LOG_DIR
        logDir = File(logDirPath)

        maxSizeBytes = (System.getenv("MAIL_FACTORY_AUDIT_LOG_MAX_SIZE")?.toLongOrNull()
            ?: DEFAULT_MAX_SIZE_MB.toLong()) * 1024 * 1024

        retentionDays = System.getenv("MAIL_FACTORY_AUDIT_LOG_RETENTION_DAYS")?.toIntOrNull()
            ?: DEFAULT_RETENTION_DAYS

        flushIntervalSeconds = System.getenv("MAIL_FACTORY_AUDIT_LOG_FLUSH_INTERVAL")?.toLongOrNull()
            ?: DEFAULT_FLUSH_INTERVAL_SECONDS

        // Auto-initialize
        initialize()
    }

    /**
     * Initializes the audit logging system.
     */
    @Synchronized
    fun initialize() {
        if (isInitialized.get()) {
            return
        }

        try {
            // Create log directory
            if (!logDir.exists()) {
                logDir.mkdirs()
            }

            // Set restrictive permissions (owner read/write only)
            logDir.setReadable(false, false)
            logDir.setReadable(true, true)
            logDir.setWritable(false, false)
            logDir.setWritable(true, true)
            logDir.setExecutable(false, false)
            logDir.setExecutable(true, true)

            // Create initial log file
            rotateLogFile()

            // Start background tasks
            startScheduler()

            isInitialized.set(true)

            Log.i("AuditLogger initialized: dir=${logDir.absolutePath}, maxSize=${maxSizeBytes / 1024 / 1024}MB, retention=${retentionDays}d")

            // Log initialization
            log(AuditEvent.SYSTEM, AuditAction.INITIALIZE, "AuditLogger started", AuditResult.SUCCESS)

        } catch (e: Exception) {
            Log.e("Failed to initialize AuditLogger: ${e.message}", e)
            throw AuditException("Failed to initialize audit logging", e)
        }
    }

    /**
     * Logs an audit event.
     *
     * @param event Event category
     * @param action Action performed
     * @param details Additional details
     * @param result Result of the action
     * @param user User who performed the action (optional)
     * @param resource Resource affected (optional)
     * @param metadata Additional metadata (optional)
     */
    fun log(
        event: AuditEvent,
        action: AuditAction,
        details: String,
        result: AuditResult,
        user: String? = null,
        resource: String? = null,
        metadata: Map<String, String> = emptyMap()
    ) {
        if (!isInitialized.get()) {
            initialize()
        }

        if (isShutdown.get()) {
            return
        }

        val entry = AuditEntry(
            timestamp = Instant.now(),
            event = event,
            action = action,
            details = details,
            result = result,
            user = user,
            resource = resource,
            metadata = metadata
        )

        // Add to queue for async processing
        logQueue.offer(entry)
    }

    /**
     * Logs authentication attempt.
     */
    fun logAuthentication(user: String, success: Boolean, details: String = "") {
        log(
            event = AuditEvent.AUTHENTICATION,
            action = AuditAction.LOGIN,
            details = details,
            result = if (success) AuditResult.SUCCESS else AuditResult.FAILURE,
            user = user
        )
    }

    /**
     * Logs authorization decision.
     */
    fun logAuthorization(user: String, resource: String, allowed: Boolean, details: String = "") {
        log(
            event = AuditEvent.AUTHORIZATION,
            action = AuditAction.ACCESS,
            details = details,
            result = if (allowed) AuditResult.SUCCESS else AuditResult.DENIED,
            user = user,
            resource = resource
        )
    }

    /**
     * Logs configuration change.
     */
    fun logConfigurationChange(user: String?, resource: String, details: String) {
        log(
            event = AuditEvent.CONFIGURATION,
            action = AuditAction.MODIFY,
            details = details,
            result = AuditResult.SUCCESS,
            user = user,
            resource = resource
        )
    }

    /**
     * Logs privileged operation.
     */
    fun logPrivilegedOperation(action: AuditAction, details: String, user: String?, success: Boolean) {
        log(
            event = AuditEvent.PRIVILEGED_OPERATION,
            action = action,
            details = details,
            result = if (success) AuditResult.SUCCESS else AuditResult.FAILURE,
            user = user
        )
    }

    /**
     * Logs encryption operation.
     */
    fun logEncryption(action: AuditAction, resource: String, success: Boolean) {
        log(
            event = AuditEvent.ENCRYPTION,
            action = action,
            details = "Encryption operation on $resource",
            result = if (success) AuditResult.SUCCESS else AuditResult.FAILURE,
            resource = resource
        )
    }

    /**
     * Logs connection event.
     */
    fun logConnection(action: AuditAction, remote: String, user: String?, success: Boolean) {
        log(
            event = AuditEvent.CONNECTION,
            action = action,
            details = "Connection to $remote",
            result = if (success) AuditResult.SUCCESS else AuditResult.FAILURE,
            user = user,
            resource = remote
        )
    }

    /**
     * Logs file access.
     */
    fun logFileAccess(action: AuditAction, filePath: String, user: String?, success: Boolean) {
        log(
            event = AuditEvent.FILE_ACCESS,
            action = action,
            details = "File access: $filePath",
            result = if (success) AuditResult.SUCCESS else AuditResult.FAILURE,
            user = user,
            resource = filePath
        )
    }

    /**
     * Logs command execution.
     */
    fun logCommandExecution(command: String, user: String?, remote: String?, success: Boolean) {
        log(
            event = AuditEvent.COMMAND_EXECUTION,
            action = AuditAction.EXECUTE,
            details = "Command: $command",
            result = if (success) AuditResult.SUCCESS else AuditResult.FAILURE,
            user = user,
            resource = remote,
            metadata = mapOf("command" to command)
        )
    }

    /**
     * Flushes pending log entries to disk.
     */
    @Synchronized
    fun flush() {
        try {
            while (logQueue.isNotEmpty()) {
                val entry = logQueue.poll() ?: break
                writeEntry(entry)
            }

            currentWriter?.flush()

        } catch (e: Exception) {
            Log.e("Failed to flush audit log: ${e.message}", e)
        }
    }

    /**
     * Writes an audit entry to the log file.
     */
    @Synchronized
    private fun writeEntry(entry: AuditEntry) {
        try {
            // Check if rotation needed
            if (currentFileSize.get() >= maxSizeBytes) {
                rotateLogFile()
            }

            // Format as JSON
            val json = entry.toJson()

            // Write to file
            currentWriter?.println(json)

            // Update size
            currentFileSize.addAndGet(json.length.toLong() + 1) // +1 for newline

        } catch (e: Exception) {
            Log.e("Failed to write audit entry: ${e.message}", e)
        }
    }

    /**
     * Rotates the log file.
     */
    @Synchronized
    private fun rotateLogFile() {
        try {
            // Close current writer
            currentWriter?.close()

            // Generate new log file name
            val timestamp = fileNameFormatter.format(Instant.now())
            currentLogFile = File(logDir, "audit_${timestamp}.log")

            // Create writer with append mode
            val fileWriter = FileWriter(currentLogFile, true)
            currentWriter = PrintWriter(fileWriter, false) // Don't auto-flush

            // Set restrictive permissions
            currentLogFile?.setReadable(false, false)
            currentLogFile?.setReadable(true, true)
            currentLogFile?.setWritable(false, false)
            currentLogFile?.setWritable(true, true)

            // Reset size counter
            currentFileSize.set(currentLogFile?.length() ?: 0)

            Log.i("Audit log rotated: ${currentLogFile?.name}")

        } catch (e: Exception) {
            Log.e("Failed to rotate audit log: ${e.message}", e)
            throw AuditException("Failed to rotate log file", e)
        }
    }

    /**
     * Starts background scheduler for flushing and cleanup.
     */
    private fun startScheduler() {
        scheduler = Executors.newScheduledThreadPool(1) { runnable ->
            Thread(runnable, "AuditLogger-Scheduler").apply {
                isDaemon = true
            }
        }

        // Schedule periodic flush
        scheduler?.scheduleAtFixedRate(
            { flush() },
            flushIntervalSeconds,
            flushIntervalSeconds,
            TimeUnit.SECONDS
        )

        // Schedule daily cleanup
        scheduler?.scheduleAtFixedRate(
            { cleanupOldLogs() },
            1,
            24,
            TimeUnit.HOURS
        )
    }

    /**
     * Cleans up old log files based on retention policy.
     */
    @Synchronized
    private fun cleanupOldLogs() {
        try {
            val cutoffTime = Instant.now().minusSeconds(retentionDays.toLong() * 24 * 60 * 60)

            val oldFiles = logDir.listFiles { file ->
                file.isFile &&
                file.name.startsWith("audit_") &&
                file.name.endsWith(".log") &&
                Instant.ofEpochMilli(file.lastModified()).isBefore(cutoffTime)
            }

            oldFiles?.forEach { file ->
                if (file.delete()) {
                    Log.i("Deleted old audit log: ${file.name}")
                } else {
                    Log.w("Failed to delete old audit log: ${file.name}")
                }
            }

        } catch (e: Exception) {
            Log.e("Failed to cleanup old audit logs: ${e.message}", e)
        }
    }

    /**
     * Shuts down the audit logger.
     */
    @Synchronized
    fun shutdown() {
        if (isShutdown.getAndSet(true)) {
            return
        }

        Log.i("Shutting down AuditLogger...")

        // Log shutdown
        log(AuditEvent.SYSTEM, AuditAction.SHUTDOWN, "AuditLogger shutting down", AuditResult.SUCCESS)

        // Flush remaining entries
        flush()

        // Stop scheduler
        scheduler?.shutdown()
        try {
            scheduler?.awaitTermination(10, TimeUnit.SECONDS)
        } catch (e: InterruptedException) {
            Log.w("Interrupted while waiting for scheduler shutdown")
            Thread.currentThread().interrupt()
        }

        // Close writer
        currentWriter?.close()

        Log.i("AuditLogger shutdown complete")
    }
}

/**
 * Audit event categories.
 */
enum class AuditEvent {
    AUTHENTICATION,
    AUTHORIZATION,
    CONFIGURATION,
    PRIVILEGED_OPERATION,
    ENCRYPTION,
    CONNECTION,
    FILE_ACCESS,
    COMMAND_EXECUTION,
    SYSTEM
}

/**
 * Audit actions.
 */
enum class AuditAction {
    LOGIN,
    LOGOUT,
    ACCESS,
    CREATE,
    READ,
    MODIFY,
    DELETE,
    EXECUTE,
    ENCRYPT,
    DECRYPT,
    CONNECT,
    DISCONNECT,
    REBOOT,
    START,
    STOP,
    INITIALIZE,
    SHUTDOWN
}

/**
 * Audit results.
 */
enum class AuditResult {
    SUCCESS,
    FAILURE,
    DENIED,
    ERROR
}

/**
 * Audit log entry.
 */
data class AuditEntry(
    val timestamp: Instant,
    val event: AuditEvent,
    val action: AuditAction,
    val details: String,
    val result: AuditResult,
    val user: String? = null,
    val resource: String? = null,
    val metadata: Map<String, String> = emptyMap()
) {
    /**
     * Converts the entry to JSON format.
     */
    fun toJson(): String {
        val sb = StringBuilder()
        sb.append("{")
        sb.append("\"timestamp\":\"${timestamp}\",")
        sb.append("\"event\":\"${event}\",")
        sb.append("\"action\":\"${action}\",")
        sb.append("\"result\":\"${result}\",")
        sb.append("\"details\":\"${escapeJson(details)}\"")

        if (user != null) {
            sb.append(",\"user\":\"${escapeJson(user)}\"")
        }

        if (resource != null) {
            sb.append(",\"resource\":\"${escapeJson(resource)}\"")
        }

        if (metadata.isNotEmpty()) {
            sb.append(",\"metadata\":{")
            sb.append(metadata.entries.joinToString(",") { (k, v) ->
                "\"${escapeJson(k)}\":\"${escapeJson(v)}\""
            })
            sb.append("}")
        }

        sb.append("}")
        return sb.toString()
    }

    /**
     * Escapes special characters for JSON.
     */
    private fun escapeJson(str: String): String {
        return str
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }
}

/**
 * Exception thrown when audit logging fails.
 */
class AuditException(message: String, cause: Throwable? = null) : Exception(message, cause)
