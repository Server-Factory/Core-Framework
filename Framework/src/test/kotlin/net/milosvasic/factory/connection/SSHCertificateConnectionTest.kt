package net.milosvasic.factory.connection

import net.milosvasic.factory.connection.impl.SSHCertificateConnectionImpl
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach

/**
 * Unit tests for SSHCertificateConnectionImpl.
 *
 * @author Mail Server Factory Team
 * @since 3.1.0
 */
class SSHCertificateConnectionTest {

    private lateinit var config: ConnectionConfig

    @BeforeEach
    fun setUp() {
        config = ConnectionConfigBuilder()
            .type(ConnectionType.SSH_CERTIFICATE)
            .host("test.example.com")
            .port(22)
            .credentials(Credentials(
                username = "testuser",
                keyPath = "/path/to/key",
                certificatePath = "/path/to/cert"
            ))
            .build()
    }

    @Test
    @DisplayName("Test SSH Certificate connection creation")
    fun testSSHCertificateConnectionCreation() {
        val connection = SSHCertificateConnectionImpl(config)
        assertNotNull(connection)
    }

    @Test
    @DisplayName("Test connection metadata")
    fun testConnectionMetadata() {
        val connection = SSHCertificateConnectionImpl(config)
        val metadata = connection.getMetadata()

        assertEquals(ConnectionType.SSH_CERTIFICATE, metadata.type)
        assertEquals("test.example.com", metadata.host)
        assertEquals(22, metadata.port)
    }

    @Test
    @DisplayName("Test metadata shows SSH Certificate auth method")
    fun testMetadataShowsSSHCertificateAuthMethod() {
        val connection = SSHCertificateConnectionImpl(config)
        val metadata = connection.getMetadata()

        assertEquals("SSH Certificate", metadata.properties["authMethod"])
    }

    @Test
    @DisplayName("Test metadata contains certificate path")
    fun testMetadataContainsCertificatePath() {
        val connection = SSHCertificateConnectionImpl(config)
        val metadata = connection.getMetadata()

        assertEquals("/path/to/cert", metadata.properties["certificatePath"])
    }

    @Test
    @DisplayName("Test metadata contains key path")
    fun testMetadataContainsKeyPath() {
        val connection = SSHCertificateConnectionImpl(config)
        val metadata = connection.getMetadata()

        assertEquals("/path/to/key", metadata.properties["keyPath"])
    }

    @Test
    @DisplayName("Test isConnected returns false before connection")
    fun testIsConnectedBeforeConnection() {
        val connection = SSHCertificateConnectionImpl(config)
        assertFalse(connection.isConnected())
    }

    @Test
    @DisplayName("Test execute fails when not connected")
    fun testExecuteFailsWhenNotConnected() {
        val connection = SSHCertificateConnectionImpl(config)
        val result = connection.execute("echo test")

        assertFalse(result.success)
        assertTrue(result.errorOutput.contains("Not connected"))
    }

    @Test
    @DisplayName("Test upload fails when not connected")
    fun testUploadFailsWhenNotConnected() {
        val connection = SSHCertificateConnectionImpl(config)
        val result = connection.uploadFile("/tmp/test.txt", "/tmp/remote.txt")

        assertFalse(result.success)
    }

    @Test
    @DisplayName("Test download fails when not connected")
    fun testDownloadFailsWhenNotConnected() {
        val connection = SSHCertificateConnectionImpl(config)
        val result = connection.downloadFile("/tmp/remote.txt", "/tmp/local.txt")

        assertFalse(result.success)
    }

    @Test
    @DisplayName("Test health check when not connected")
    fun testHealthCheckWhenNotConnected() {
        val connection = SSHCertificateConnectionImpl(config)
        val health = connection.getHealth()

        assertFalse(health.isHealthy)
    }

    @Test
    @DisplayName("Test config validation with valid certificate config")
    fun testConfigValidationWithValidCertificateConfig() {
        val connection = SSHCertificateConnectionImpl(config)
        val result = connection.validateConfig()

        assertTrue(result.isSuccess())
    }

    @Test
    @DisplayName("Test config validation fails without certificate path")
    fun testConfigValidationFailsWithoutCertificatePath() {
        val invalidConfig = ConnectionConfigBuilder()
            .type(ConnectionType.SSH_CERTIFICATE)
            .host("test.example.com")
            .port(22)
            .credentials(Credentials(
                username = "testuser",
                keyPath = "/path/to/key"
                // Missing certificatePath
            ))
            .build()

        val connection = SSHCertificateConnectionImpl(invalidConfig)
        val result = connection.validateConfig()

        assertTrue(result.isFailed())
    }

    @Test
    @DisplayName("Test config validation fails without key path")
    fun testConfigValidationFailsWithoutKeyPath() {
        val invalidConfig = ConnectionConfigBuilder()
            .type(ConnectionType.SSH_CERTIFICATE)
            .host("test.example.com")
            .port(22)
            .credentials(Credentials(
                username = "testuser",
                certificatePath = "/path/to/cert"
                // Missing keyPath
            ))
            .build()

        val connection = SSHCertificateConnectionImpl(invalidConfig)
        val result = connection.validateConfig()

        assertTrue(result.isFailed())
    }

    @Test
    @DisplayName("Test disconnect when not connected")
    fun testDisconnectWhenNotConnected() {
        val connection = SSHCertificateConnectionImpl(config)

        assertDoesNotThrow {
            connection.disconnect()
        }
    }

    @Test
    @DisplayName("Test close releases resources")
    fun testCloseReleasesResources() {
        val connection = SSHCertificateConnectionImpl(config)

        assertDoesNotThrow {
            connection.close()
        }

        assertFalse(connection.isConnected())
    }

    @Test
    @DisplayName("Test metadata display name")
    fun testMetadataDisplayName() {
        val connection = SSHCertificateConnectionImpl(config)
        val metadata = connection.getMetadata()

        assertEquals("testuser@test.example.com:22", metadata.displayName)
    }

    @Test
    @DisplayName("Test metadata properties contain protocol")
    fun testMetadataPropertiesContainProtocol() {
        val connection = SSHCertificateConnectionImpl(config)
        val metadata = connection.getMetadata()

        assertEquals("SSH", metadata.properties["protocol"])
    }

    @Test
    @DisplayName("Test connection with invalid host")
    fun testConnectionWithInvalidHost() {
        val invalidConfig = ConnectionConfigBuilder()
            .type(ConnectionType.SSH_CERTIFICATE)
            .host("")
            .port(22)
            .credentials(Credentials(
                username = "testuser",
                keyPath = "/path/to/key",
                certificatePath = "/path/to/cert"
            ))
            .build()

        val connection = SSHCertificateConnectionImpl(invalidConfig)
        val validation = connection.validateConfig()

        assertTrue(validation.isFailed())
    }

    @Test
    @DisplayName("Test metadata to map conversion")
    fun testMetadataToMapConversion() {
        val connection = SSHCertificateConnectionImpl(config)
        val metadata = connection.getMetadata()
        val map = metadata.toMap()

        assertEquals("SSH_CERTIFICATE", map["type"])
        assertEquals("test.example.com", map["host"])
        assertEquals("22", map["port"])
    }

    @Test
    @DisplayName("Test certificate info extraction from metadata")
    fun testCertificateInfoExtractionFromMetadata() {
        val connection = SSHCertificateConnectionImpl(config)
        val metadata = connection.getMetadata()

        // Should contain certificate-related properties
        assertTrue(metadata.properties.containsKey("certificatePath"))
        assertTrue(metadata.properties.containsKey("keyPath"))
        assertTrue(metadata.properties.containsKey("authMethod"))
    }
}
