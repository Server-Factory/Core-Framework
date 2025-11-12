package net.milosvasic.factory.connection

import net.milosvasic.factory.connection.impl.SSHAgentConnectionImpl
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach

/**
 * Unit tests for SSHAgentConnectionImpl.
 *
 * @author Mail Server Factory Team
 * @since 3.1.0
 */
class SSHAgentConnectionTest {

    private lateinit var config: ConnectionConfig

    @BeforeEach
    fun setUp() {
        config = ConnectionConfigBuilder()
            .type(ConnectionType.SSH_AGENT)
            .host("test.example.com")
            .port(22)
            .credentials(Credentials("testuser", agentSocket = "/tmp/ssh-agent.sock"))
            .build()
    }

    @Test
    @DisplayName("Test SSH Agent connection creation")
    fun testSSHAgentConnectionCreation() {
        val connection = SSHAgentConnectionImpl(config)
        assertNotNull(connection)
    }

    @Test
    @DisplayName("Test connection metadata")
    fun testConnectionMetadata() {
        val connection = SSHAgentConnectionImpl(config)
        val metadata = connection.getMetadata()

        assertEquals(ConnectionType.SSH_AGENT, metadata.type)
        assertEquals("test.example.com", metadata.host)
        assertEquals(22, metadata.port)
    }

    @Test
    @DisplayName("Test metadata contains agent socket")
    fun testMetadataContainsAgentSocket() {
        val connection = SSHAgentConnectionImpl(config)
        val metadata = connection.getMetadata()

        assertTrue(metadata.properties.containsKey("agentSocket"))
        assertEquals("/tmp/ssh-agent.sock", metadata.properties["agentSocket"])
    }

    @Test
    @DisplayName("Test metadata shows SSH Agent auth method")
    fun testMetadataShowsSSHAgentAuthMethod() {
        val connection = SSHAgentConnectionImpl(config)
        val metadata = connection.getMetadata()

        assertEquals("SSH Agent", metadata.properties["authMethod"])
    }

    @Test
    @DisplayName("Test agent forwarding enabled in metadata")
    fun testAgentForwardingEnabledInMetadata() {
        val connection = SSHAgentConnectionImpl(config)
        val metadata = connection.getMetadata()

        assertEquals("enabled", metadata.properties["agentForwarding"])
    }

    @Test
    @DisplayName("Test isConnected returns false before connection")
    fun testIsConnectedBeforeConnection() {
        val connection = SSHAgentConnectionImpl(config)
        assertFalse(connection.isConnected())
    }

    @Test
    @DisplayName("Test execute fails when not connected")
    fun testExecuteFailsWhenNotConnected() {
        val connection = SSHAgentConnectionImpl(config)
        val result = connection.execute("echo test")

        assertFalse(result.success)
        assertTrue(result.errorOutput.contains("Not connected"))
    }

    @Test
    @DisplayName("Test upload fails when not connected")
    fun testUploadFailsWhenNotConnected() {
        val connection = SSHAgentConnectionImpl(config)
        val result = connection.uploadFile("/tmp/test.txt", "/tmp/remote.txt")

        assertFalse(result.success)
    }

    @Test
    @DisplayName("Test download fails when not connected")
    fun testDownloadFailsWhenNotConnected() {
        val connection = SSHAgentConnectionImpl(config)
        val result = connection.downloadFile("/tmp/remote.txt", "/tmp/local.txt")

        assertFalse(result.success)
    }

    @Test
    @DisplayName("Test health check when not connected")
    fun testHealthCheckWhenNotConnected() {
        val connection = SSHAgentConnectionImpl(config)
        val health = connection.getHealth()

        assertFalse(health.isHealthy)
    }

    @Test
    @DisplayName("Test config validation")
    fun testConfigValidation() {
        val connection = SSHAgentConnectionImpl(config)
        val result = connection.validateConfig()

        assertTrue(result.isSuccess())
    }

    @Test
    @DisplayName("Test connection without agent socket uses environment")
    fun testConnectionWithoutAgentSocketUsesEnvironment() {
        val noSocketConfig = ConnectionConfigBuilder()
            .type(ConnectionType.SSH_AGENT)
            .host("test.example.com")
            .port(22)
            .credentials(Credentials("testuser")) // No agent socket specified
            .build()

        val connection = SSHAgentConnectionImpl(noSocketConfig)
        assertNotNull(connection)
        // Would use SSH_AUTH_SOCK environment variable
    }

    @Test
    @DisplayName("Test disconnect when not connected")
    fun testDisconnectWhenNotConnected() {
        val connection = SSHAgentConnectionImpl(config)

        assertDoesNotThrow {
            connection.disconnect()
        }
    }

    @Test
    @DisplayName("Test close releases resources")
    fun testCloseReleasesResources() {
        val connection = SSHAgentConnectionImpl(config)

        assertDoesNotThrow {
            connection.close()
        }

        assertFalse(connection.isConnected())
    }

    @Test
    @DisplayName("Test metadata display name")
    fun testMetadataDisplayName() {
        val connection = SSHAgentConnectionImpl(config)
        val metadata = connection.getMetadata()

        assertEquals("testuser@test.example.com:22", metadata.displayName)
    }

    @Test
    @DisplayName("Test metadata properties contain protocol")
    fun testMetadataPropertiesContainProtocol() {
        val connection = SSHAgentConnectionImpl(config)
        val metadata = connection.getMetadata()

        assertEquals("SSH", metadata.properties["protocol"])
    }

    @Test
    @DisplayName("Test connection with invalid host")
    fun testConnectionWithInvalidHost() {
        val invalidConfig = ConnectionConfigBuilder()
            .type(ConnectionType.SSH_AGENT)
            .host("")
            .port(22)
            .credentials(Credentials("testuser", agentSocket = "/tmp/ssh-agent.sock"))
            .build()

        val connection = SSHAgentConnectionImpl(invalidConfig)
        val validation = connection.validateConfig()

        assertTrue(validation.isFailed())
    }

    @Test
    @DisplayName("Test connection with custom options")
    fun testConnectionWithCustomOptions() {
        val optionsConfig = ConnectionConfigBuilder()
            .type(ConnectionType.SSH_AGENT)
            .host("test.example.com")
            .port(22)
            .credentials(Credentials("testuser", agentSocket = "/tmp/ssh-agent.sock"))
            .options(ConnectionOptions(
                timeout = 90,
                strictHostKeyChecking = false
            ))
            .build()

        val connection = SSHAgentConnectionImpl(optionsConfig)
        val metadata = connection.getMetadata()

        assertEquals("false", metadata.properties["strictHostKeyChecking"])
    }

    @Test
    @DisplayName("Test SSH Agent connection validation")
    fun testSSHAgentConnectionValidation() {
        val config = ConnectionConfigBuilder()
            .type(ConnectionType.SSH_AGENT)
            .host("test.example.com")
            .port(22)
            .credentials(Credentials("testuser", agentSocket = "/tmp/ssh-agent.sock"))
            .build()
            
        val result = config.validate()
        assertTrue(result.isSuccess())
    }

    @Test
    @DisplayName("Test metadata to map conversion")
    fun testMetadataToMapConversion() {
        val connection = SSHAgentConnectionImpl(config)
        val metadata = connection.getMetadata()
        val map = metadata.toMap()

        assertEquals("SSH_AGENT", map["type"])
        assertEquals("test.example.com", map["host"])
        assertEquals("22", map["port"])
    }
}
