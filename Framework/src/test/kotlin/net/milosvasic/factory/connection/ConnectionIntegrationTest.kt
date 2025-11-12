package net.milosvasic.factory.connection

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

/**
 * Integration tests for connection mechanisms.
 *
 * Tests cross-component functionality, security integration,
 * and end-to-end workflows.
 *
 * @author Mail Server Factory Team
 * @since 3.1.0
 */
@Tag("integration")
class ConnectionIntegrationTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    @DisplayName("Test factory creates all 12 connection types")
    fun testFactoryCreatesAll12ConnectionTypes() {
        val connectionTypes = listOf(
            createSSHConfig(),
            createSSHAgentConfig(),
            createSSHCertificateConfig(),
            createSSHBastionConfig(),
            createWinRMConfig(),
            createAnsibleConfig(),
            createDockerConfig(),
            createKubernetesConfig(),
            createAWSSSMConfig(),
            createAzureSerialConfig(),
            createGCPOSLoginConfig(),
            createLocalConfig()
        )

        connectionTypes.forEach { config ->
            val connection = ConnectionFactory.createConnection(config)
            assertNotNull(connection)
            assertEquals(config.type, connection.getMetadata().type)
        }

        assertEquals(12, connectionTypes.size)
    }

    @Test
    @DisplayName("Test all connection types support required operations")
    fun testAllConnectionTypesSupportRequiredOperations() {
        val configs = listOf(createLocalConfig(), createDockerConfig())

        configs.forEach { config ->
            val connection = ConnectionFactory.createConnection(config)

            // All connections must support these operations
            assertNotNull(connection.getMetadata())
            assertNotNull(connection.getHealth())
            assertNotNull(connection.validateConfig())

            // Can call these without throwing exceptions
            assertDoesNotThrow { connection.isConnected() }
            assertDoesNotThrow { connection.disconnect() }
        }
    }

    @Test
    @DisplayName("Test connection lifecycle for Local connection")
    fun testConnectionLifecycleForLocalConnection() {
        val config = ConnectionConfigBuilder()
            .type(ConnectionType.LOCAL)
            .host("localhost")
            .port(0)
            .options(ConnectionOptions(properties = mapOf(
                "workingDirectory" to tempDir.toString()
            )))
            .build()

        val connection = ConnectionFactory.createConnection(config)

        // Before connection
        assertFalse(connection.isConnected())

        // Connect
        val connectResult = connection.connect()
        assertTrue(connectResult.isSuccess())
        assertTrue(connection.isConnected())

        // Execute command
        val executeResult = connection.execute("echo 'Integration Test'")
        assertTrue(executeResult.success)
        assertTrue(executeResult.output.contains("Integration Test"))

        // Upload file
        val sourceFile = File(tempDir.toFile(), "upload-test.txt")
        sourceFile.writeText("Upload content")
        val uploadResult = connection.uploadFile(
            sourceFile.absolutePath,
            File(tempDir.toFile(), "uploaded.txt").absolutePath
        )
        assertTrue(uploadResult.success)

        // Download file
        val downloadResult = connection.downloadFile(
            File(tempDir.toFile(), "uploaded.txt").absolutePath,
            File(tempDir.toFile(), "downloaded.txt").absolutePath
        )
        assertTrue(downloadResult.success)

        // Health check
        val health = connection.getHealth()
        assertTrue(health.isHealthy)

        // Disconnect
        connection.disconnect()
        assertFalse(connection.isConnected())
    }

    @Test
    @DisplayName("Test connection validation prevents invalid configs")
    fun testConnectionValidationPreventsInvalidConfigs() {
        val invalidConfigs = listOf(
            // Invalid host
            ConnectionConfigBuilder()
                .type(ConnectionType.SSH)
                .host("")
                .port(22)
                .credentials(Credentials("user", password = "pass"))
                .build(),

            // Invalid port
            ConnectionConfigBuilder()
                .type(ConnectionType.SSH)
                .host("test.example.com")
                .port(70000)
                .credentials(Credentials("user", password = "pass"))
                .build(),

            // Missing credentials auth
            ConnectionConfigBuilder()
                .type(ConnectionType.SSH)
                .host("test.example.com")
                .port(22)
                .credentials(Credentials("user")) // No auth method
                .build(),

            // Missing bastion config
            ConnectionConfigBuilder()
                .type(ConnectionType.SSH_BASTION)
                .host("target.internal")
                .port(22)
                .credentials(Credentials("user", password = "pass"))
                .build(),

            // Missing container config
            ConnectionConfigBuilder()
                .type(ConnectionType.DOCKER)
                .host("unix:///var/run/docker.sock")
                .port(0)
                .build(),

            // Missing cloud config
            ConnectionConfigBuilder()
                .type(ConnectionType.AWS_SSM)
                .host("i-1234567890")
                .port(0)
                .build()
        )

        invalidConfigs.forEach { config ->
            val validation = config.validate()
            assertTrue(validation.isFailed(), "Config should fail validation: ${config.type}")

            assertThrows<ConnectionException> {
                ConnectionFactory.createConnection(config)
            }
        }
    }

    @Test
    @DisplayName("Test connection factory registration and tracking")
    fun testConnectionFactoryRegistrationAndTracking() {
        val initialCount = ConnectionFactory.getConnectionCount()

        val configs = listOf(
            createLocalConfig(),
            createDockerConfig()
        )

        configs.forEach { config ->
            ConnectionFactory.createConnection(config)
        }

        val newCount = ConnectionFactory.getConnectionCount()
        assertTrue(newCount >= initialCount + configs.size)

        val activeConnections = ConnectionFactory.getActiveConnections()
        assertNotNull(activeConnections)
        assertTrue(activeConnections.size >= configs.size)
    }

    @Test
    @DisplayName("Test connection metadata consistency")
    fun testConnectionMetadataConsistency() {
        val config = createLocalConfig()
        val connection = ConnectionFactory.createConnection(config)

        val metadata = connection.getMetadata()

        // Metadata should match config
        assertEquals(config.type, metadata.type)
        assertEquals(config.host, metadata.host)
        assertEquals(config.port, metadata.port)

        // Metadata should be convertible to map
        val map = metadata.toMap()
        assertEquals(config.type.name, map["type"])
        assertEquals(config.host, map["host"])
        assertEquals(config.port.toString(), map["port"])
    }

    @Test
    @DisplayName("Test connection health check consistency")
    fun testConnectionHealthCheckConsistency() {
        val config = createLocalConfig()
        val connection = ConnectionFactory.createConnection(config)

        // Before connection - unhealthy
        val healthBefore = connection.getHealth()
        assertFalse(healthBefore.isHealthy)
        assertTrue(healthBefore.message.contains("Not connected"))

        // After connection - healthy
        connection.connect()
        val healthAfter = connection.getHealth()
        assertTrue(healthAfter.isHealthy)
        assertTrue(healthAfter.latencyMs >= 0)

        connection.disconnect()
    }

    @Test
    @DisplayName("Test multiple connections can coexist")
    fun testMultipleConnectionsCanCoexist() {
        val config1 = ConnectionConfigBuilder()
            .type(ConnectionType.LOCAL)
            .host("localhost")
            .port(0)
            .options(ConnectionOptions(properties = mapOf(
                "workingDirectory" to tempDir.toString()
            )))
            .build()

        val config2 = ConnectionConfigBuilder()
            .type(ConnectionType.LOCAL)
            .host("localhost")
            .port(0)
            .options(ConnectionOptions(properties = mapOf(
                "workingDirectory" to tempDir.toString()
            )))
            .build()

        val connection1 = ConnectionFactory.createConnection(config1)
        val connection2 = ConnectionFactory.createConnection(config2)

        connection1.connect()
        connection2.connect()

        assertTrue(connection1.isConnected())
        assertTrue(connection2.isConnected())

        // Both can execute independently
        val result1 = connection1.execute("echo 'Connection 1'")
        val result2 = connection2.execute("echo 'Connection 2'")

        assertTrue(result1.success)
        assertTrue(result2.success)
        assertTrue(result1.output.contains("Connection 1"))
        assertTrue(result2.output.contains("Connection 2"))

        connection1.disconnect()
        connection2.disconnect()
    }

    @Test
    @DisplayName("Test connection options propagate correctly")
    fun testConnectionOptionsPropagateCorrectly() {
        val options = ConnectionOptions(
            timeout = 60,
            retries = 5,
            healthCheck = true,
            compression = true,
            strictHostKeyChecking = false,
            properties = mapOf("custom" to "value")
        )

        val config = ConnectionConfigBuilder()
            .type(ConnectionType.LOCAL)
            .host("localhost")
            .port(0)
            .options(options)
            .build()

        val connection = ConnectionFactory.createConnection(config)

        assertEquals(60, config.options.timeout)
        assertEquals(5, config.options.retries)
        assertTrue(config.options.healthCheck)
        assertTrue(config.options.compression)
        assertFalse(config.options.strictHostKeyChecking)
        assertEquals("value", config.options.getProperty("custom"))
    }

    @Test
    @DisplayName("Test connection error handling")
    fun testConnectionErrorHandling() {
        val config = createLocalConfig()
        val connection = ConnectionFactory.createConnection(config)

        connection.connect()

        // Execute invalid command
        val result = connection.execute("nonexistent-command-12345")

        assertNotNull(result)
        // Should fail gracefully
        assertTrue(!result.success || result.exitCode != 0)

        connection.disconnect()
    }

    @Test
    @DisplayName("Test connection timeout handling")
    fun testConnectionTimeoutHandling() {
        val config = createLocalConfig()
        val connection = ConnectionFactory.createConnection(config)

        connection.connect()

        // Execute command that times out
        val result = connection.execute("sleep 10", timeout = 1)

        assertNotNull(result)
        assertFalse(result.success)
        assertTrue(result.errorOutput.contains("timed out") || result.errorOutput.contains("timeout"))

        connection.disconnect()
    }

    @Test
    @DisplayName("Test file transfer operations work correctly")
    fun testFileTransferOperationsWorkCorrectly() {
        val config = createLocalConfig()
        val connection = ConnectionFactory.createConnection(config)

        connection.connect()

        // Create test file
        val sourceFile = File(tempDir.toFile(), "transfer-test.txt")
        val content = "Transfer test content with special chars: !@#$%^&*()"
        sourceFile.writeText(content)

        // Upload
        val destPath = File(tempDir.toFile(), "transferred.txt").absolutePath
        val uploadResult = connection.uploadFile(sourceFile.absolutePath, destPath)

        assertTrue(uploadResult.success)
        assertTrue(uploadResult.bytesTransferred > 0)

        // Verify file exists and content matches
        val destFile = File(destPath)
        assertTrue(destFile.exists())
        assertEquals(content, destFile.readText())

        // Download
        val downloadPath = File(tempDir.toFile(), "downloaded.txt").absolutePath
        val downloadResult = connection.downloadFile(destPath, downloadPath)

        assertTrue(downloadResult.success)
        assertEquals(uploadResult.bytesTransferred, downloadResult.bytesTransferred)

        // Verify downloaded file
        val downloadedFile = File(downloadPath)
        assertTrue(downloadedFile.exists())
        assertEquals(content, downloadedFile.readText())

        connection.disconnect()
    }

    @Test
    @DisplayName("Test connection builder fluent API")
    fun testConnectionBuilderFluentAPI() {
        val config = ConnectionConfigBuilder()
            .type(ConnectionType.SSH)
            .host("test.example.com")
            .port(2222)
            .credentials(Credentials("testuser", password = "testpass"))
            .options(ConnectionOptions(timeout = 90, retries = 3))
            .build()

        assertEquals(ConnectionType.SSH, config.type)
        assertEquals("test.example.com", config.host)
        assertEquals(2222, config.port)
        assertEquals("testuser", config.credentials?.username)
        assertEquals(90, config.options.timeout)
        assertEquals(3, config.options.retries)
    }

    @Test
    @DisplayName("Test connection types have correct helper methods")
    fun testConnectionTypesHaveCorrectHelperMethods() {
        assertTrue(ConnectionType.SSH.isSSH())
        assertTrue(ConnectionType.SSH_AGENT.isSSH())
        assertTrue(ConnectionType.SSH_CERTIFICATE.isSSH())
        assertTrue(ConnectionType.SSH_BASTION.isSSH())

        assertTrue(ConnectionType.DOCKER.isContainer())
        assertTrue(ConnectionType.KUBERNETES.isContainer())

        assertTrue(ConnectionType.AWS_SSM.isCloud())
        assertTrue(ConnectionType.AZURE_SERIAL.isCloud())
        assertTrue(ConnectionType.GCP_OS_LOGIN.isCloud())

        assertTrue(ConnectionType.SSH.isRemote())
        assertFalse(ConnectionType.LOCAL.isRemote())
    }

    // Helper methods to create configs
    private fun createSSHConfig() = ConnectionConfigBuilder()
        .type(ConnectionType.SSH)
        .host("test.example.com")
        .port(22)
        .credentials(Credentials("user", password = "pass"))
        .build()

    private fun createSSHAgentConfig() = ConnectionConfigBuilder()
        .type(ConnectionType.SSH_AGENT)
        .host("test.example.com")
        .port(22)
        .credentials(Credentials("user", agentSocket = "/tmp/ssh-agent.sock"))
        .build()

    private fun createSSHCertificateConfig() = ConnectionConfigBuilder()
        .type(ConnectionType.SSH_CERTIFICATE)
        .host("test.example.com")
        .port(22)
        .credentials(Credentials("user", keyPath = "/path/to/key", certificatePath = "/path/to/cert"))
        .build()

    private fun createSSHBastionConfig(): ConnectionConfig {
        val bastion = ConnectionConfigBuilder()
            .type(ConnectionType.SSH)
            .host("bastion.example.com")
            .port(22)
            .credentials(Credentials("bastion", password = "pass"))
            .build()

        return ConnectionConfigBuilder()
            .type(ConnectionType.SSH_BASTION)
            .host("target.internal")
            .port(22)
            .credentials(Credentials("user", password = "pass"))
            .bastionConfig(bastion)
            .build()
    }

    private fun createWinRMConfig() = ConnectionConfigBuilder()
        .type(ConnectionType.WINRM)
        .host("windows.example.com")
        .port(5985)
        .credentials(Credentials("Administrator", password = "pass"))
        .build()

    private fun createAnsibleConfig() = ConnectionConfigBuilder()
        .type(ConnectionType.ANSIBLE)
        .host("servers")
        .port(22)
        .credentials(Credentials("ansible", keyPath = "/path/to/key"))
        .build()

    private fun createDockerConfig() = ConnectionConfigBuilder()
        .type(ConnectionType.DOCKER)
        .host("unix:///var/run/docker.sock")
        .port(0)
        .containerConfig(ContainerConfig(
            containerType = ContainerType.DOCKER,
            containerName = "test-container"
        ))
        .build()

    private fun createKubernetesConfig() = ConnectionConfigBuilder()
        .type(ConnectionType.KUBERNETES)
        .host("k8s-cluster.example.com")
        .port(443)
        .containerConfig(ContainerConfig(
            containerType = ContainerType.KUBERNETES,
            namespace = "default",
            podSelector = "app=test"
        ))
        .build()

    private fun createAWSSSMConfig() = ConnectionConfigBuilder()
        .type(ConnectionType.AWS_SSM)
        .host("i-1234567890")
        .port(0)
        .cloudConfig(CloudConfig(
            provider = CloudProvider.AWS,
            region = "us-east-1",
            instanceId = "i-1234567890"
        ))
        .build()

    private fun createAzureSerialConfig() = ConnectionConfigBuilder()
        .type(ConnectionType.AZURE_SERIAL)
        .host("test-vm")
        .port(0)
        .cloudConfig(CloudConfig(
            provider = CloudProvider.AZURE,
            subscriptionId = "12345678-1234-1234-1234-123456789012",
            resourceGroup = "test-rg",
            vmName = "test-vm"
        ))
        .build()

    private fun createGCPOSLoginConfig() = ConnectionConfigBuilder()
        .type(ConnectionType.GCP_OS_LOGIN)
        .host("test-instance")
        .port(22)
        .cloudConfig(CloudConfig(
            provider = CloudProvider.GCP,
            project = "test-project",
            zone = "us-central1-a",
            instanceId = "test-instance"
        ))
        .build()

    private fun createLocalConfig() = ConnectionConfigBuilder()
        .type(ConnectionType.LOCAL)
        .host("localhost")
        .port(0)
        .options(ConnectionOptions(properties = mapOf(
            "workingDirectory" to tempDir.toString()
        )))
        .build()
}
