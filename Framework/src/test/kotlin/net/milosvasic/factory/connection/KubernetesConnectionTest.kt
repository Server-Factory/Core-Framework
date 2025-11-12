package net.milosvasic.factory.connection

import net.milosvasic.factory.connection.impl.KubernetesConnectionImpl
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach

/**
 * Unit tests for KubernetesConnectionImpl.
 *
 * @author Mail Server Factory Team
 * @since 3.1.0
 */
class KubernetesConnectionTest {

    private lateinit var config: ConnectionConfig

    @BeforeEach
    fun setUp() {
        config = ConnectionConfigBuilder()
            .type(ConnectionType.KUBERNETES)
            .host("k8s-cluster.example.com")
            .port(443)
            .containerConfig(ContainerConfig(
                containerType = ContainerType.KUBERNETES,
                namespace = "mail-production",
                podSelector = "app=mail-server",
                containerInPod = "postfix",
                kubeconfig = "/home/user/.kube/config"
            ))
            .build()
    }

    @Test
    @DisplayName("Test Kubernetes connection creation")
    fun testKubernetesConnectionCreation() {
        val connection = KubernetesConnectionImpl(config)
        assertNotNull(connection)
    }

    @Test
    @DisplayName("Test connection metadata")
    fun testConnectionMetadata() {
        val connection = KubernetesConnectionImpl(config)
        val metadata = connection.getMetadata()

        assertEquals(ConnectionType.KUBERNETES, metadata.type)
        assertEquals("k8s-cluster.example.com", metadata.host)
        assertEquals(443, metadata.port)
    }

    @Test
    @DisplayName("Test metadata shows Kubernetes auth method")
    fun testMetadataShowsKubernetesAuthMethod() {
        val connection = KubernetesConnectionImpl(config)
        val metadata = connection.getMetadata()

        assertEquals("Kubernetes", metadata.properties["authMethod"])
    }

    @Test
    @DisplayName("Test metadata contains namespace")
    fun testMetadataContainsNamespace() {
        val connection = KubernetesConnectionImpl(config)
        val metadata = connection.getMetadata()

        assertEquals("mail-production", metadata.properties["namespace"])
    }

    @Test
    @DisplayName("Test metadata contains pod selector")
    fun testMetadataContainsPodSelector() {
        val connection = KubernetesConnectionImpl(config)
        val metadata = connection.getMetadata()

        assertEquals("app=mail-server", metadata.properties["podSelector"])
    }

    @Test
    @DisplayName("Test metadata contains container name")
    fun testMetadataContainsContainerName() {
        val connection = KubernetesConnectionImpl(config)
        val metadata = connection.getMetadata()

        assertEquals("postfix", metadata.properties["containerInPod"])
    }

    @Test
    @DisplayName("Test metadata contains kubeconfig path")
    fun testMetadataContainsKubeconfigPath() {
        val connection = KubernetesConnectionImpl(config)
        val metadata = connection.getMetadata()

        assertEquals("/home/user/.kube/config", metadata.properties["kubeconfig"])
    }

    @Test
    @DisplayName("Test isConnected returns false before connection")
    fun testIsConnectedBeforeConnection() {
        val connection = KubernetesConnectionImpl(config)
        assertFalse(connection.isConnected())
    }

    @Test
    @DisplayName("Test execute fails when not connected")
    fun testExecuteFailsWhenNotConnected() {
        val connection = KubernetesConnectionImpl(config)
        val result = connection.execute("postconf -d")

        assertFalse(result.success)
        assertTrue(result.errorOutput.contains("Not connected"))
    }

    @Test
    @DisplayName("Test Kubernetes connection metadata")
    fun testKubernetesConnectionMetadata() {
        val connection = KubernetesConnectionImpl(config)
        val metadata = connection.getMetadata()
        
        assertEquals(ConnectionType.KUBERNETES, metadata.type)
        assertEquals("test-pod", metadata.host)
    }

    @Test
    @DisplayName("Test upload fails when not connected")
    fun testUploadFailsWhenNotConnected() {
        val connection = KubernetesConnectionImpl(config)
        val result = connection.uploadFile("/tmp/test.txt", "/tmp/remote.txt")

        assertFalse(result.success)
    }

    @Test
    @DisplayName("Test download fails when not connected")
    fun testDownloadFailsWhenNotConnected() {
        val connection = KubernetesConnectionImpl(config)
        val result = connection.downloadFile("/tmp/remote.txt", "/tmp/local.txt")

        assertFalse(result.success)
    }

    @Test
    @DisplayName("Test health check when not connected")
    fun testHealthCheckWhenNotConnected() {
        val connection = KubernetesConnectionImpl(config)
        val health = connection.getHealth()

        assertFalse(health.isHealthy)
    }

    @Test
    @DisplayName("Test config validation with valid Kubernetes config")
    fun testConfigValidationWithValidKubernetesConfig() {
        val connection = KubernetesConnectionImpl(config)
        val result = connection.validateConfig()

        assertTrue(result.isSuccess())
    }

    @Test
    @DisplayName("Test config validation fails without namespace")
    fun testConfigValidationFailsWithoutNamespace() {
        val invalidConfig = ConnectionConfigBuilder()
            .type(ConnectionType.KUBERNETES)
            .host("k8s-cluster.example.com")
            .port(443)
            .containerConfig(ContainerConfig(
                containerType = ContainerType.KUBERNETES,
                podSelector = "app=mail-server",
                kubeconfig = "/home/user/.kube/config"
                // Missing namespace
            ))
            .build()

        val connection = KubernetesConnectionImpl(invalidConfig)
        val result = connection.validateConfig()

        assertTrue(result.isFailed())
    }

    @Test
    @DisplayName("Test config validation fails without pod selector")
    fun testConfigValidationFailsWithoutPodSelector() {
        val invalidConfig = ConnectionConfigBuilder()
            .type(ConnectionType.KUBERNETES)
            .host("k8s-cluster.example.com")
            .port(443)
            .containerConfig(ContainerConfig(
                containerType = ContainerType.KUBERNETES,
                namespace = "mail-production",
                kubeconfig = "/home/user/.kube/config"
                // Missing podSelector
            ))
            .build()

        val connection = KubernetesConnectionImpl(invalidConfig)
        val result = connection.validateConfig()

        assertTrue(result.isFailed())
    }

    @Test
    @DisplayName("Test disconnect when not connected")
    fun testDisconnectWhenNotConnected() {
        val connection = KubernetesConnectionImpl(config)

        assertDoesNotThrow {
            connection.disconnect()
        }
    }

    @Test
    @DisplayName("Test close releases resources")
    fun testCloseReleasesResources() {
        val connection = KubernetesConnectionImpl(config)

        assertDoesNotThrow {
            connection.close()
        }

        assertFalse(connection.isConnected())
    }

    @Test
    @DisplayName("Test metadata display name")
    fun testMetadataDisplayName() {
        val connection = KubernetesConnectionImpl(config)
        val metadata = connection.getMetadata()

        assertEquals("mail-production/app=mail-server/postfix", metadata.displayName)
    }

    @Test
    @DisplayName("Test metadata properties contain protocol")
    fun testMetadataPropertiesContainProtocol() {
        val connection = KubernetesConnectionImpl(config)
        val metadata = connection.getMetadata()

        assertEquals("Kubernetes", metadata.properties["protocol"])
    }

    @Test
    @DisplayName("Test metadata to map conversion")
    fun testMetadataToMapConversion() {
        val connection = KubernetesConnectionImpl(config)
        val metadata = connection.getMetadata()
        val map = metadata.toMap()

        assertEquals("KUBERNETES", map["type"])
        assertEquals("k8s-cluster.example.com", map["host"])
        assertEquals("443", map["port"])
        assertEquals("mail-production", map["namespace"])
    }

    @Test
    @DisplayName("Test Kubernetes info extraction from metadata")
    fun testKubernetesInfoExtractionFromMetadata() {
        val connection = KubernetesConnectionImpl(config)
        val metadata = connection.getMetadata()

        // Should contain Kubernetes-related properties
        assertTrue(metadata.properties.containsKey("namespace"))
        assertTrue(metadata.properties.containsKey("podSelector"))
        assertTrue(metadata.properties.containsKey("containerInPod"))
        assertTrue(metadata.properties.containsKey("kubeconfig"))
        assertTrue(metadata.properties.containsKey("authMethod"))
        assertTrue(metadata.properties.containsKey("protocol"))
    }
}
