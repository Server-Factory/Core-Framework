package net.milosvasic.factory.connection

import net.milosvasic.factory.connection.impl.AzureSerialConnectionImpl
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach

/**
 * Unit tests for AzureSerialConnectionImpl.
 *
 * @author Mail Server Factory Team
 * @since 3.1.0
 */
class AzureSerialConnectionTest {

    private lateinit var config: ConnectionConfig

    @BeforeEach
    fun setUp() {
        config = ConnectionConfigBuilder()
            .type(ConnectionType.AZURE_SERIAL)
            .host("mail-vm")
            .port(0)
            .cloudConfig(CloudConfig(
                provider = CloudProvider.AZURE,
                subscriptionId = "12345678-1234-1234-1234-123456789012",
                resourceGroup = "mail-production-rg",
                vmName = "mail-vm"
            ))
            .build()
    }

    @Test
    @DisplayName("Test Azure Serial connection creation")
    fun testAzureSerialConnectionCreation() {
        val connection = AzureSerialConnectionImpl(config)
        assertNotNull(connection)
    }

    @Test
    @DisplayName("Test connection metadata")
    fun testConnectionMetadata() {
        val connection = AzureSerialConnectionImpl(config)
        val metadata = connection.getMetadata()

        assertEquals(ConnectionType.AZURE_SERIAL, metadata.type)
        assertEquals("mail-vm", metadata.host)
        assertEquals(0, metadata.port)
    }

    @Test
    @DisplayName("Test metadata shows Azure Serial auth method")
    fun testMetadataShowsAzureSerialAuthMethod() {
        val connection = AzureSerialConnectionImpl(config)
        val metadata = connection.getMetadata()

        assertEquals("Azure Serial Console", metadata.properties["authMethod"])
    }

    @Test
    @DisplayName("Test metadata contains subscription ID")
    fun testMetadataContainsSubscriptionId() {
        val connection = AzureSerialConnectionImpl(config)
        val metadata = connection.getMetadata()

        assertEquals("12345678-1234-1234-1234-123456789012", metadata.properties["subscriptionId"])
    }

    @Test
    @DisplayName("Test metadata contains resource group")
    fun testMetadataContainsResourceGroup() {
        val connection = AzureSerialConnectionImpl(config)
        val metadata = connection.getMetadata()

        assertEquals("mail-production-rg", metadata.properties["resourceGroup"])
    }

    @Test
    @DisplayName("Test metadata contains VM name")
    fun testMetadataContainsVMName() {
        val connection = AzureSerialConnectionImpl(config)
        val metadata = connection.getMetadata()

        assertEquals("mail-vm", metadata.properties["vmName"])
    }

    @Test
    @DisplayName("Test metadata contains cloud provider")
    fun testMetadataContainsCloudProvider() {
        val connection = AzureSerialConnectionImpl(config)
        val metadata = connection.getMetadata()

        assertEquals("AZURE", metadata.properties["cloudProvider"])
    }

    @Test
    @DisplayName("Test isConnected returns false before connection")
    fun testIsConnectedBeforeConnection() {
        val connection = AzureSerialConnectionImpl(config)
        assertFalse(connection.isConnected())
    }

    @Test
    @DisplayName("Test execute fails when not connected")
    fun testExecuteFailsWhenNotConnected() {
        val connection = AzureSerialConnectionImpl(config)
        val result = connection.execute("systemctl status postfix")

        assertFalse(result.success)
        assertTrue(result.errorOutput.contains("Not connected"))
    }

    @Test
    @DisplayName("Test upload fails when not connected")
    fun testUploadFailsWhenNotConnected() {
        val connection = AzureSerialConnectionImpl(config)
        val result = connection.uploadFile("/tmp/test.txt", "/tmp/remote.txt")

        assertFalse(result.success)
    }

    @Test
    @DisplayName("Test download fails when not connected")
    fun testDownloadFailsWhenNotConnected() {
        val connection = AzureSerialConnectionImpl(config)
        val result = connection.downloadFile("/tmp/remote.txt", "/tmp/local.txt")

        assertFalse(result.success)
    }

    @Test
    @DisplayName("Test health check when not connected")
    fun testHealthCheckWhenNotConnected() {
        val connection = AzureSerialConnectionImpl(config)
        val health = connection.getHealth()

        assertFalse(health.isHealthy)
    }

    @Test
    @DisplayName("Test config validation with valid Azure Serial config")
    fun testConfigValidationWithValidAzureSerialConfig() {
        val connection = AzureSerialConnectionImpl(config)
        val result = connection.validateConfig()

        assertTrue(result.isSuccess())
    }

    @Test
    @DisplayName("Test config validation fails without subscription ID")
    fun testConfigValidationFailsWithoutSubscriptionId() {
        val invalidConfig = ConnectionConfigBuilder()
            .type(ConnectionType.AZURE_SERIAL)
            .host("mail-vm")
            .port(0)
            .cloudConfig(CloudConfig(
                provider = CloudProvider.AZURE,
                resourceGroup = "mail-production-rg",
                vmName = "mail-vm"
                // Missing subscriptionId
            ))
            .build()

        val connection = AzureSerialConnectionImpl(invalidConfig)
        val result = connection.validateConfig()

        assertTrue(result.isFailed())
    }

    @Test
    @DisplayName("Test config validation fails without resource group")
    fun testConfigValidationFailsWithoutResourceGroup() {
        val invalidConfig = ConnectionConfigBuilder()
            .type(ConnectionType.AZURE_SERIAL)
            .host("mail-vm")
            .port(0)
            .cloudConfig(CloudConfig(
                provider = CloudProvider.AZURE,
                subscriptionId = "12345678-1234-1234-1234-123456789012",
                vmName = "mail-vm"
                // Missing resourceGroup
            ))
            .build()

        val connection = AzureSerialConnectionImpl(invalidConfig)
        val result = connection.validateConfig()

        assertTrue(result.isFailed())
    }

    @Test
    @DisplayName("Test config validation fails without VM name")
    fun testConfigValidationFailsWithoutVMName() {
        val invalidConfig = ConnectionConfigBuilder()
            .type(ConnectionType.AZURE_SERIAL)
            .host("mail-vm")
            .port(0)
            .cloudConfig(CloudConfig(
                provider = CloudProvider.AZURE,
                subscriptionId = "12345678-1234-1234-1234-123456789012",
                resourceGroup = "mail-production-rg"
                // Missing vmName
            ))
            .build()

        val connection = AzureSerialConnectionImpl(invalidConfig)
        val result = connection.validateConfig()

        assertTrue(result.isFailed())
    }

    @Test
    @DisplayName("Test disconnect when not connected")
    fun testDisconnectWhenNotConnected() {
        val connection = AzureSerialConnectionImpl(config)

        assertDoesNotThrow {
            connection.disconnect()
        }
    }

    @Test
    @DisplayName("Test close releases resources")
    fun testCloseReleasesResources() {
        val connection = AzureSerialConnectionImpl(config)

        assertDoesNotThrow {
            connection.close()
        }

        assertFalse(connection.isConnected())
    }

    @Test
    @DisplayName("Test metadata display name")
    fun testMetadataDisplayName() {
        val connection = AzureSerialConnectionImpl(config)
        val metadata = connection.getMetadata()

        assertEquals("mail-vm (mail-production-rg)", metadata.displayName)
    }

    @Test
    @DisplayName("Test metadata properties contain protocol")
    fun testMetadataPropertiesContainProtocol() {
        val connection = AzureSerialConnectionImpl(config)
        val metadata = connection.getMetadata()

        assertEquals("Azure Serial Console", metadata.properties["protocol"])
    }

    @Test
    @DisplayName("Test metadata to map conversion")
    fun testMetadataToMapConversion() {
        val connection = AzureSerialConnectionImpl(config)
        val metadata = connection.getMetadata()
        val map = metadata.toMap()

        assertEquals("AZURE_SERIAL", map["type"])
        assertEquals("mail-vm", map["host"])
        assertEquals("0", map["port"])
        assertEquals("mail-production-rg", map["resourceGroup"])
    }

    @Test
    @DisplayName("Test Azure Serial info extraction from metadata")
    fun testAzureSerialInfoExtractionFromMetadata() {
        val connection = AzureSerialConnectionImpl(config)
        val metadata = connection.getMetadata()

        // Should contain Azure Serial-related properties
        assertTrue(metadata.properties.containsKey("subscriptionId"))
        assertTrue(metadata.properties.containsKey("resourceGroup"))
        assertTrue(metadata.properties.containsKey("vmName"))
        assertTrue(metadata.properties.containsKey("cloudProvider"))
        assertTrue(metadata.properties.containsKey("authMethod"))
        assertTrue(metadata.properties.containsKey("protocol"))
    }
}
