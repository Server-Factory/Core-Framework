package net.milosvasic.factory.remote

import net.milosvasic.logger.Log
import net.milosvasic.factory.connection.Connection as NewConnection
import net.milosvasic.factory.connection.ConnectionConfig
import net.milosvasic.factory.connection.ConnectionResult
import net.milosvasic.factory.connection.ConnectionType
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Connection pool manager for connections with lifecycle management.
 *
 * Features:
 * - Connection reuse to avoid creating multiple connections to same host
 * - Health monitoring with automatic reconnection
 * - Idle connection cleanup
 * - Reference counting for proper resource management
 * - Graceful shutdown with connection draining
 * - Configurable pool size limits
 * - Thread-safe operations
 * - Supports all connection types (SSH, Docker, Kubernetes, etc.)
 *
 * Usage:
 * ```kotlin
 * // Get or create connection
 * val connection = ConnectionPool.getConnection(config)
 *
 * // Use connection
 * connection.execute("echo test")
 *
 * // Release connection (decrements reference count)
 * ConnectionPool.releaseConnection(config)
 *
 * // Shutdown pool when done
 * ConnectionPool.shutdown()
 * ```
 *
 * Configuration via environment variables:
 * - MAIL_FACTORY_POOL_MAX_SIZE: Maximum pool size (default: 10)
 * - MAIL_FACTORY_POOL_IDLE_TIMEOUT: Idle timeout in seconds (default: 300)
 * - MAIL_FACTORY_POOL_HEALTH_CHECK_INTERVAL: Health check interval in seconds (default: 60)
 *
 * @author Mail Server Factory Team
 * @since 3.1.0
 */
object ConnectionPool {

    private const val DEFAULT_MAX_POOL_SIZE = 10
    private const val DEFAULT_IDLE_TIMEOUT_SECONDS = 300L // 5 minutes
    private const val DEFAULT_HEALTH_CHECK_INTERVAL_SECONDS = 60L // 1 minute

    private val maxPoolSize: Int
    private val idleTimeoutMillis: Long
    private val healthCheckIntervalMillis: Long

    init {
        maxPoolSize = System.getenv("MAIL_FACTORY_POOL_MAX_SIZE")?.toIntOrNull()
            ?: DEFAULT_MAX_POOL_SIZE

        idleTimeoutMillis = (System.getenv("MAIL_FACTORY_POOL_IDLE_TIMEOUT")?.toLongOrNull()
            ?: DEFAULT_IDLE_TIMEOUT_SECONDS) * 1000

        healthCheckIntervalMillis = (System.getenv("MAIL_FACTORY_POOL_HEALTH_CHECK_INTERVAL")?.toLongOrNull()
            ?: DEFAULT_HEALTH_CHECK_INTERVAL_SECONDS) * 1000

        Log.i("ConnectionPool initialized: maxSize=$maxPoolSize, idleTimeout=${idleTimeoutMillis / 1000}s, healthCheck=${healthCheckIntervalMillis / 1000}s")
    }

    // Pool of connections keyed by connection identifier
    private val connections = ConcurrentHashMap<String, PooledConnection>()

    // Scheduled executor for health checks and idle connection cleanup
    private var scheduler: ScheduledExecutorService? = null

    // Pool state
    private val isShutdown = AtomicBoolean(false)
    private val activeConnections = AtomicInteger(0)

    /**
     * Gets or creates a connection from the pool.
     *
     * @param config Connection configuration
     * @return NewConnection (reused or newly created)
     * @throws IllegalStateException if pool is shutdown or full
     */
    @Synchronized
    fun getConnection(config: ConnectionConfig): NewConnection {
        if (isShutdown.get()) {
            throw IllegalStateException("ConnectionPool is shutdown")
        }

        // Start scheduler if not already running
        if (scheduler == null) {
            startScheduler()
        }

        val key = getConnectionKey(config)

        // Get existing connection or create new one
        val pooled = connections.computeIfAbsent(key) {
            if (activeConnections.get() >= maxPoolSize) {
                // Pool is full - try to evict idle connections
                evictIdleConnections()

                if (activeConnections.get() >= maxPoolSize) {
                    throw IllegalStateException("ConnectionPool is full (max: $maxPoolSize)")
                }
            }

            Log.i("Creating new connection to ${config.host}")
            createPooledConnection(config)
        }

        // Increment reference count
        pooled.acquire()

        // Verify connection is healthy
        if (!pooled.isHealthy()) {
            Log.w("Connection to ${config.host} is unhealthy, reconnecting...")
            pooled.reconnect()
        }

        Log.v("Connection acquired: ${config.host} (refs: ${pooled.refCount.get()})")

        return pooled.connection
    }

    /**
     * Releases a connection back to the pool.
     *
     * Decrements reference count. When count reaches zero, connection becomes eligible for cleanup.
     *
     * @param config The connection configuration
     */
    @Synchronized
    fun releaseConnection(config: ConnectionConfig) {
        val key = getConnectionKey(config)

        connections[key]?.let { pooled ->
            pooled.release()
            Log.v("Connection released: ${config.host} (refs: ${pooled.refCount.get()})")

            // If no more references and past idle timeout, close immediately
            if (pooled.refCount.get() == 0 && pooled.isIdle()) {
                Log.i("Closing idle connection to ${config.host}")
                removeConnection(key, pooled)
            }
        }
    }

    /**
     * Forces closure of a specific connection.
     *
     * @param config The connection configuration
     */
    @Synchronized
    fun closeConnection(config: ConnectionConfig) {
        val key = getConnectionKey(config)

        connections[key]?.let { pooled ->
            Log.i("Force closing connection to ${config.host}")
            removeConnection(key, pooled)
        }
    }

    /**
     * Gets pool statistics.
     *
     * @return ConnectionPoolStats with current pool state
     */
    fun getStats(): ConnectionPoolStats {
        val total = connections.size
        val active = connections.count { it.value.refCount.get() > 0 }
        val idle = total - active
        val healthy = connections.count { it.value.isHealthy() }

        return ConnectionPoolStats(
            totalConnections = total,
            activeConnections = active,
            idleConnections = idle,
            healthyConnections = healthy,
            maxPoolSize = maxPoolSize
        )
    }

    /**
     * Shuts down the connection pool gracefully.
     *
     * Closes all idle connections immediately and drains active connections.
     *
     * @param timeoutSeconds Maximum time to wait for active connections to drain
     */
    @Synchronized
    fun shutdown(timeoutSeconds: Long = 30) {
        if (isShutdown.getAndSet(true)) {
            return // Already shutdown
        }

        Log.i("Shutting down ConnectionPool...")

        // Stop scheduler
        scheduler?.shutdown()

        try {
            // Wait for scheduler to terminate
            scheduler?.awaitTermination(5, TimeUnit.SECONDS)
        } catch (e: InterruptedException) {
            Log.w("Interrupted while waiting for scheduler shutdown")
            Thread.currentThread().interrupt()
        }

        // Close all connections
        val startTime = System.currentTimeMillis()
        val timeoutMillis = timeoutSeconds * 1000

        while (connections.isNotEmpty() && System.currentTimeMillis() - startTime < timeoutMillis) {
            val iterator = connections.entries.iterator()
            while (iterator.hasNext()) {
                val entry = iterator.next()
                val pooled = entry.value

                if (pooled.refCount.get() == 0) {
                    // No active references - close immediately
                    Log.i("Closing connection to ${pooled.config.host}")
                    pooled.close()
                    activeConnections.decrementAndGet()
                    iterator.remove()
                } else {
                    // Active connection - wait for release
                    Log.v("Waiting for connection to ${pooled.config.host} to be released (refs: ${pooled.refCount.get()})")
                }
            }

            if (connections.isNotEmpty()) {
                Thread.sleep(1000)
            }
        }

        // Force close any remaining connections
        connections.forEach { (_, pooled) ->
            Log.w("Force closing connection to ${pooled.config.host} (refs: ${pooled.refCount.get()})")
            pooled.close()
        }

        connections.clear()
        activeConnections.set(0)

        Log.i("ConnectionPool shutdown complete")
    }

    /**
     * Creates a pooled connection wrapper.
     */
    private fun createPooledConnection(config: ConnectionConfig): PooledConnection {
        // Create connection based on type
        val connection = when (config.type) {
            ConnectionType.SSH -> net.milosvasic.factory.connection.impl.SSHConnectionImpl(config)
            ConnectionType.SSH_AGENT -> net.milosvasic.factory.connection.impl.SSHAgentConnectionImpl(config)
            ConnectionType.SSH_CERTIFICATE -> net.milosvasic.factory.connection.impl.SSHCertificateConnectionImpl(config)
            ConnectionType.SSH_BASTION -> net.milosvasic.factory.connection.impl.SSHBastionConnectionImpl(config)
            ConnectionType.DOCKER -> net.milosvasic.factory.connection.impl.DockerConnectionImpl(config)
            ConnectionType.KUBERNETES -> net.milosvasic.factory.connection.impl.KubernetesConnectionImpl(config)
            ConnectionType.WINRM -> net.milosvasic.factory.connection.impl.WinRMConnectionImpl(config)
            ConnectionType.ANSIBLE -> net.milosvasic.factory.connection.impl.AnsibleConnectionImpl(config)
            ConnectionType.AWS_SSM -> net.milosvasic.factory.connection.impl.AWSSSMConnectionImpl(config)
            ConnectionType.AZURE_SERIAL -> net.milosvasic.factory.connection.impl.AzureSerialConnectionImpl(config)
            ConnectionType.GCP_OS_LOGIN -> net.milosvasic.factory.connection.impl.GCPOSLoginConnectionImpl(config)
            ConnectionType.LOCAL -> net.milosvasic.factory.connection.impl.LocalConnectionImpl(config)
        }

        // Connect immediately
        val result = connection.connect()
        if (result.isFailed()) {
            throw IllegalStateException("Failed to connect: ${(result as ConnectionResult.Failure).error}")
        }

        activeConnections.incrementAndGet()
        return PooledConnection(config, connection)
    }

    /**
     * Removes a connection from the pool.
     */
    private fun removeConnection(key: String, pooled: PooledConnection) {
        pooled.close()
        connections.remove(key)
        activeConnections.decrementAndGet()
    }

    /**
     * Gets a unique key for a connection.
     */
    private fun getConnectionKey(config: ConnectionConfig): String {
        return "${config.host}:${config.port}:${config.credentials?.username ?: "default"}"
    }

    /**
     * Starts the background scheduler for health checks and cleanup.
     */
    private fun startScheduler() {
        scheduler = Executors.newScheduledThreadPool(1) { runnable ->
            Thread(runnable, "ConnectionPool-Scheduler").apply {
                isDaemon = true
            }
        }

        // Schedule health checks
        scheduler?.scheduleAtFixedRate(
            { performHealthChecks() },
            healthCheckIntervalMillis,
            healthCheckIntervalMillis,
            TimeUnit.MILLISECONDS
        )

        // Schedule idle connection cleanup
        scheduler?.scheduleAtFixedRate(
            { evictIdleConnections() },
            idleTimeoutMillis,
            idleTimeoutMillis,
            TimeUnit.MILLISECONDS
        )

        Log.i("ConnectionPool scheduler started")
    }

    /**
     * Performs health checks on all connections.
     */
    private fun performHealthChecks() {
        Log.v("Performing connection health checks...")

        connections.forEach { (_, pooled) ->
            try {
                if (!pooled.isHealthy()) {
                    Log.w("Connection to ${pooled.config.host} failed health check")

                    // Attempt reconnection for active connections
                    if (pooled.refCount.get() > 0) {
                        Log.i("Attempting to reconnect active connection to ${pooled.config.host}")
                        pooled.reconnect()
                    }
                }
            } catch (e: Exception) {
                Log.e("Health check failed for ${pooled.config.host}: ${e.message}")
            }
        }
    }

    /**
     * Evicts idle connections that exceed the idle timeout.
     */
    private fun evictIdleConnections() {
        Log.v("Checking for idle connections to evict...")

        val iterator = connections.entries.iterator()
        var evicted = 0

        while (iterator.hasNext()) {
            val entry = iterator.next()
            val pooled = entry.value

            if (pooled.refCount.get() == 0 && pooled.isIdle()) {
                Log.i("Evicting idle connection to ${pooled.config.host} (idle for ${pooled.getIdleTimeSeconds()}s)")
                pooled.close()
                activeConnections.decrementAndGet()
                iterator.remove()
                evicted++
            }
        }

        if (evicted > 0) {
            Log.i("Evicted $evicted idle connections")
        }
    }

    /**
     * Wrapper for a pooled connection with lifecycle management.
     */
    private class PooledConnection(
        val config: ConnectionConfig,
        val connection: NewConnection
    ) {
        val refCount = AtomicInteger(0)
        private var lastAccessTime = System.currentTimeMillis()

        /**
         * Acquires the connection (increments reference count).
         */
        fun acquire() {
            refCount.incrementAndGet()
            lastAccessTime = System.currentTimeMillis()
        }

        /**
         * Releases the connection (decrements reference count).
         */
        fun release() {
            refCount.decrementAndGet()
            lastAccessTime = System.currentTimeMillis()
        }

        /**
         * Checks if connection is healthy.
         */
        fun isHealthy(): Boolean {
            return try {
                // Check if connected
                if (!connection.isConnected()) {
                    return false
                }

                // Execute simple command to verify connectivity
                val result = connection.execute("echo ping", timeout = 5)
                result.success && result.output.contains("ping")
            } catch (e: Exception) {
                false
            }
        }

        /**
         * Reconnects the connection.
         */
        fun reconnect() {
            try {
                connection.disconnect()
                val result = connection.connect()
                if (result.isSuccess()) {
                    Log.i("Reconnected to ${config.host}")
                } else {
                    Log.e("Reconnection failed to ${config.host}: ${(result as ConnectionResult.Failure).error}")
                    throw Exception("Reconnection failed")
                }
            } catch (e: Exception) {
                Log.e("Reconnection failed to ${config.host}: ${e.message}")
                throw e
            }
        }

        /**
         * Checks if connection is idle (no references and past timeout).
         */
        fun isIdle(): Boolean {
            val idleTime = System.currentTimeMillis() - lastAccessTime
            return refCount.get() == 0 && idleTime > idleTimeoutMillis
        }

        /**
         * Gets idle time in seconds.
         */
        fun getIdleTimeSeconds(): Long {
            return (System.currentTimeMillis() - lastAccessTime) / 1000
        }

        /**
         * Closes the connection.
         */
        fun close() {
            try {
                connection.disconnect()
                Log.v("Closed connection to ${config.host}")
            } catch (e: Exception) {
                Log.e("Error closing connection to ${config.host}: ${e.message}")
            }
        }
    }
}

/**
 * Connection pool statistics.
 */
data class ConnectionPoolStats(
    val totalConnections: Int,
    val activeConnections: Int,
    val idleConnections: Int,
    val healthyConnections: Int,
    val maxPoolSize: Int
) {
    override fun toString(): String {
        return "ConnectionPool: total=$totalConnections, active=$activeConnections, idle=$idleConnections, healthy=$healthyConnections, max=$maxPoolSize"
    }
}
