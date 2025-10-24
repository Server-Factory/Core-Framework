package net.milosvasic.factory.connection

import net.milosvasic.factory.connection.impl.WinRMConnectionImpl
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach

/**
 * Unit tests for WinRMConnectionImpl.
 *
 * @author Mail Server Factory Team
 * @since 3.1.0
 */
class WinRMConnectionTest {

    private lateinit var config: ConnectionConfig

    @BeforeEach
    fun setUp() {
        config = ConnectionConfigBuilder()
            .type(ConnectionType.WINRM)
            .host("windows-mail.example.com")
            .port(5985)
            .credentials(Credentials(
                username = "Administrator",
                password = "encrypted:AES256:IV:salt:pass"
            ))
            .options(ConnectionOptions(properties = mapOf(
                "useHttps" to "false",
                "authType" to "NTLM"
            )))
            .build()
    }

    @Test
    @DisplayName("Test WinRM connection creation")
    fun testWinRMConnectionCreation() {
        val connection = WinRMConnectionImpl(config)
        assertNotNull(connection)
    }

    @Test
    @DisplayName("Test connection metadata")
    fun testConnectionMetadata() {
        val connection = WinRMConnectionImpl(config)
        val metadata = connection.getMetadata()

        assertEquals(ConnectionType.WINRM, metadata.type)
        assertEquals("windows-mail.example.com", metadata.host)
        assertEquals(5985, metadata.port)
    }

    @Test
    @DisplayName("Test metadata shows WinRM auth method")
    fun testMetadataShowsWinRMAuthMethod() {
        val connection = WinRMConnectionImpl(config)
        val metadata = connection.getMetadata()

        assertEquals("WinRM", metadata.properties["authMethod"])
    }

    @Test
    @DisplayName("Test metadata contains auth type")
    fun testMetadataContainsAuthType() {
        val connection = WinRMConnectionImpl(config)
        val metadata = connection.getMetadata()

        assertEquals("NTLM", metadata.properties["authType"])
    }

    @Test
    @DisplayName("Test metadata contains use HTTPS flag")
    fun testMetadataContainsUseHttpsFlag() {
        val connection = WinRMConnectionImpl(config)
        val metadata = connection.getMetadata()

        assertEquals("false", metadata.properties["useHttps"])
    }

    @Test
    @DisplayName("Test HTTPS configuration")
    fun testHttpsConfiguration() {
        val httpsConfig = ConnectionConfigBuilder()
            .type(ConnectionType.WINRM)
            .host("windows-mail.example.com")
            .port(5986)
            .credentials(Credentials(
                username = "Administrator",
                password = "encrypted:AES256:IV:salt:pass"
            ))
            .options(ConnectionOptions(properties = mapOf(
                "useHttps" to "true",
                "authType" to "NTLM"
            )))
            .build()

        val connection = WinRMConnectionImpl(httpsConfig)
        val metadata = connection.getMetadata()

        assertEquals(5986, metadata.port)
        assertEquals("true", metadata.properties["useHttps"])
    }

    @Test
    @DisplayName("Test isConnected returns false before connection")
    fun testIsConnectedBeforeConnection() {
        val connection = WinRMConnectionImpl(config)
        assertFalse(connection.isConnected())
    }

    @Test
    @DisplayName("Test execute fails when not connected")
    fun testExecuteFailsWhenNotConnected() {
        val connection = WinRMConnectionImpl(config)
        val result = connection.execute("Get-Process")

        assertFalse(result.success)
        assertTrue(result.errorOutput.contains("Not connected"))
    }

    @Test
    @DisplayName("Test upload fails when not connected")
    fun testUploadFailsWhenNotConnected() {
        val connection = WinRMConnectionImpl(config)
        val result = connection.uploadFile("C:\\tmp\\test.txt", "C:\\remote\\test.txt")

        assertFalse(result.success)
    }

    @Test
    @DisplayName("Test download fails when not connected")
    fun testDownloadFailsWhenNotConnected() {
        val connection = WinRMConnectionImpl(config)
        val result = connection.downloadFile("C:\\remote\\test.txt", "C:\\local\\test.txt")

        assertFalse(result.success)
    }

    @Test
    @DisplayName("Test health check when not connected")
    fun testHealthCheckWhenNotConnected() {
        val connection = WinRMConnectionImpl(config)
        val health = connection.getHealth()

        assertFalse(health.isHealthy)
    }

    @Test
    @DisplayName("Test config validation with valid WinRM config")
    fun testConfigValidationWithValidWinRMConfig() {
        val connection = WinRMConnectionImpl(config)
        val result = connection.validateConfig()

        assertTrue(result.isSuccess())
    }

    @Test
    @DisplayName("Test config validation fails without auth type")
    fun testConfigValidationFailsWithoutAuthType() {
        val invalidConfig = ConnectionConfigBuilder()
            .type(ConnectionType.WINRM)
            .host("windows-mail.example.com")
            .port(5985)
            .credentials(Credentials(
                username = "Administrator",
                password = "encrypted:AES256:IV:salt:pass"
            ))
            .options(ConnectionOptions(properties = mapOf(
                "useHttps" to "false"
                // Missing authType
            )))
            .build()

        val connection = WinRMConnectionImpl(invalidConfig)
        val result = connection.validateConfig()

        assertTrue(result.isFailed())
    }

    @Test
    @DisplayName("Test config validation with different auth types")
    fun testConfigValidationWithDifferentAuthTypes() {
        val authTypes = listOf("NTLM", "Basic", "Kerberos", "CredSSP")

        authTypes.forEach { authType ->
            val testConfig = ConnectionConfigBuilder()
                .type(ConnectionType.WINRM)
                .host("windows-mail.example.com")
                .port(5985)
                .credentials(Credentials(
                    username = "Administrator",
                    password = "encrypted:AES256:IV:salt:pass"
                ))
                .options(ConnectionOptions(properties = mapOf(
                    "useHttps" to "false",
                    "authType" to authType
                )))
                .build()

            val connection = WinRMConnectionImpl(testConfig)
            val result = connection.validateConfig()
            assertTrue(result.isSuccess(), "Auth type $authType should be valid")
        }
    }

    @Test
    @DisplayName("Test disconnect when not connected")
    fun testDisconnectWhenNotConnected() {
        val connection = WinRMConnectionImpl(config)

        assertDoesNotThrow {
            connection.disconnect()
        }
    }

    @Test
    @DisplayName("Test close releases resources")
    fun testCloseReleasesResources() {
        val connection = WinRMConnectionImpl(config)

        assertDoesNotThrow {
            connection.close()
        }

        assertFalse(connection.isConnected())
    }

    @Test
    @DisplayName("Test metadata display name")
    fun testMetadataDisplayName() {
        val connection = WinRMConnectionImpl(config)
        val metadata = connection.getMetadata()

        assertEquals("Administrator@windows-mail.example.com:5985", metadata.displayName)
    }

    @Test
    @DisplayName("Test metadata properties contain protocol")
    fun testMetadataPropertiesContainProtocol() {
        val connection = WinRMConnectionImpl(config)
        val metadata = connection.getMetadata()

        assertEquals("WinRM", metadata.properties["protocol"])
    }

    @Test
    @DisplayName("Test metadata to map conversion")
    fun testMetadataToMapConversion() {
        val connection = WinRMConnectionImpl(config)
        val metadata = connection.getMetadata()
        val map = metadata.toMap()

        assertEquals("WINRM", map["type"])
        assertEquals("windows-mail.example.com", map["host"])
        assertEquals("5985", map["port"])
        assertEquals("NTLM", map["authType"])
    }

    @Test
    @DisplayName("Test WinRM info extraction from metadata")
    fun testWinRMInfoExtractionFromMetadata() {
        val connection = WinRMConnectionImpl(config)
        val metadata = connection.getMetadata()

        // Should contain WinRM-related properties
        assertTrue(metadata.properties.containsKey("authType"))
        assertTrue(metadata.properties.containsKey("useHttps"))
        assertTrue(metadata.properties.containsKey("authMethod"))
        assertTrue(metadata.properties.containsKey("protocol"))
    }
}
