package net.milosvasic.factory.connection

import net.milosvasic.factory.connection.impl.AnsibleConnectionImpl
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach

/**
 * Unit tests for AnsibleConnectionImpl.
 *
 * @author Mail Server Factory Team
 * @since 3.1.0
 */
class AnsibleConnectionTest {

    private lateinit var config: ConnectionConfig

    @BeforeEach
    fun setUp() {
        config = ConnectionConfigBuilder()
            .type(ConnectionType.ANSIBLE)
            .host("mail-servers")  // Inventory group
            .port(22)
            .credentials(Credentials(
                username = "ansible",
                keyPath = "/path/to/ansible_key"
            ))
            .options(ConnectionOptions(properties = mapOf(
                "inventoryPath" to "/etc/ansible/hosts",
                "playbookDir" to "/opt/ansible/playbooks",
                "extraVars" to "env=production mail_domain=example.com"
            )))
            .build()
    }

    @Test
    @DisplayName("Test Ansible connection creation")
    fun testAnsibleConnectionCreation() {
        val connection = AnsibleConnectionImpl(config)
        assertNotNull(connection)
    }

    @Test
    @DisplayName("Test connection metadata")
    fun testConnectionMetadata() {
        val connection = AnsibleConnectionImpl(config)
        val metadata = connection.getMetadata()

        assertEquals(ConnectionType.ANSIBLE, metadata.type)
        assertEquals("mail-servers", metadata.host)
        assertEquals(22, metadata.port)
    }

    @Test
    @DisplayName("Test metadata shows Ansible auth method")
    fun testMetadataShowsAnsibleAuthMethod() {
        val connection = AnsibleConnectionImpl(config)
        val metadata = connection.getMetadata()

        assertEquals("Ansible", metadata.properties["authMethod"])
    }

    @Test
    @DisplayName("Test metadata contains inventory path")
    fun testMetadataContainsInventoryPath() {
        val connection = AnsibleConnectionImpl(config)
        val metadata = connection.getMetadata()

        assertEquals("/etc/ansible/hosts", metadata.properties["inventoryPath"])
    }

    @Test
    @DisplayName("Test metadata contains playbook directory")
    fun testMetadataContainsPlaybookDirectory() {
        val connection = AnsibleConnectionImpl(config)
        val metadata = connection.getMetadata()

        assertEquals("/opt/ansible/playbooks", metadata.properties["playbookDir"])
    }

    @Test
    @DisplayName("Test metadata contains extra vars")
    fun testMetadataContainsExtraVars() {
        val connection = AnsibleConnectionImpl(config)
        val metadata = connection.getMetadata()

        assertEquals("env=production mail_domain=example.com", metadata.properties["extraVars"])
    }

    @Test
    @DisplayName("Test isConnected returns false before connection")
    fun testIsConnectedBeforeConnection() {
        val connection = AnsibleConnectionImpl(config)
        assertFalse(connection.isConnected())
    }

    @Test
    @DisplayName("Test execute fails when not connected")
    fun testExecuteFailsWhenNotConnected() {
        val connection = AnsibleConnectionImpl(config)
        val result = connection.execute("ansible all -m ping")

        assertFalse(result.success)
        assertTrue(result.errorOutput.contains("Not connected"))
    }

    @Test
    @DisplayName("Test executePlaybook method exists")
    fun testExecutePlaybookMethodExists() {
        val connection = AnsibleConnectionImpl(config)

        assertDoesNotThrow {
            val result = connection.executePlaybook(
                playbookPath = "/opt/ansible/playbooks/deploy.yml",
                extraVars = mapOf("mail_domain" to "example.com")
            )
            assertNotNull(result)
        }
    }

    @Test
    @DisplayName("Test executePlaybook fails when not connected")
    fun testExecutePlaybookFailsWhenNotConnected() {
        val connection = AnsibleConnectionImpl(config)
        val result = connection.executePlaybook(
            playbookPath = "/opt/ansible/playbooks/deploy.yml"
        )

        assertFalse(result.success)
    }

    @Test
    @DisplayName("Test upload fails when not connected")
    fun testUploadFailsWhenNotConnected() {
        val connection = AnsibleConnectionImpl(config)
        val result = connection.uploadFile("/tmp/test.txt", "/tmp/remote.txt")

        assertFalse(result.success)
    }

    @Test
    @DisplayName("Test download fails when not connected")
    fun testDownloadFailsWhenNotConnected() {
        val connection = AnsibleConnectionImpl(config)
        val result = connection.downloadFile("/tmp/remote.txt", "/tmp/local.txt")

        assertFalse(result.success)
    }

    @Test
    @DisplayName("Test health check when not connected")
    fun testHealthCheckWhenNotConnected() {
        val connection = AnsibleConnectionImpl(config)
        val health = connection.getHealth()

        assertFalse(health.isHealthy)
    }

    @Test
    @DisplayName("Test config validation with valid Ansible config")
    fun testConfigValidationWithValidAnsibleConfig() {
        val connection = AnsibleConnectionImpl(config)
        val result = connection.validateConfig()

        assertTrue(result.isSuccess())
    }

    @Test
    @DisplayName("Test config validation fails without inventory path")
    fun testConfigValidationFailsWithoutInventoryPath() {
        val invalidConfig = ConnectionConfigBuilder()
            .type(ConnectionType.ANSIBLE)
            .host("mail-servers")
            .port(22)
            .credentials(Credentials(
                username = "ansible",
                keyPath = "/path/to/ansible_key"
            ))
            .options(ConnectionOptions(properties = mapOf(
                "playbookDir" to "/opt/ansible/playbooks"
                // Missing inventoryPath
            )))
            .build()

        val connection = AnsibleConnectionImpl(invalidConfig)
        val result = connection.validateConfig()

        assertTrue(result.isFailed())
    }

    @Test
    @DisplayName("Test disconnect when not connected")
    fun testDisconnectWhenNotConnected() {
        val connection = AnsibleConnectionImpl(config)

        assertDoesNotThrow {
            connection.disconnect()
        }
    }

    @Test
    @DisplayName("Test close releases resources")
    fun testCloseReleasesResources() {
        val connection = AnsibleConnectionImpl(config)

        assertDoesNotThrow {
            connection.close()
        }

        assertFalse(connection.isConnected())
    }

    @Test
    @DisplayName("Test metadata display name")
    fun testMetadataDisplayName() {
        val connection = AnsibleConnectionImpl(config)
        val metadata = connection.getMetadata()

        assertEquals("ansible@mail-servers", metadata.displayName)
    }

    @Test
    @DisplayName("Test metadata properties contain protocol")
    fun testMetadataPropertiesContainProtocol() {
        val connection = AnsibleConnectionImpl(config)
        val metadata = connection.getMetadata()

        assertEquals("Ansible", metadata.properties["protocol"])
    }

    @Test
    @DisplayName("Test metadata to map conversion")
    fun testMetadataToMapConversion() {
        val connection = AnsibleConnectionImpl(config)
        val metadata = connection.getMetadata()
        val map = metadata.toMap()

        assertEquals("ANSIBLE", map["type"])
        assertEquals("mail-servers", map["host"])
        assertEquals("22", map["port"])
        assertEquals("/etc/ansible/hosts", map["inventoryPath"])
    }

    @Test
    @DisplayName("Test Ansible info extraction from metadata")
    fun testAnsibleInfoExtractionFromMetadata() {
        val connection = AnsibleConnectionImpl(config)
        val metadata = connection.getMetadata()

        // Should contain Ansible-related properties
        assertTrue(metadata.properties.containsKey("inventoryPath"))
        assertTrue(metadata.properties.containsKey("playbookDir"))
        assertTrue(metadata.properties.containsKey("extraVars"))
        assertTrue(metadata.properties.containsKey("authMethod"))
        assertTrue(metadata.properties.containsKey("protocol"))
    }
}
