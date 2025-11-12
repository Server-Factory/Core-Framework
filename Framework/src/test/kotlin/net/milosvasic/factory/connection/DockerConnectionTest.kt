package net.milosvasic.factory.connection

import net.milosvasic.factory.connection.impl.DockerConnectionImpl
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach

/**
 * Unit tests for DockerConnectionImpl.
 *
 * @author Mail Server Factory Team
 * @since 3.1.0
 */
class DockerConnectionTest {

    private lateinit var config: ConnectionConfig

    @BeforeEach
    fun setUp() {
        config = ConnectionConfigBuilder()
            .type(ConnectionType.DOCKER)
            .host("unix:///var/run/docker.sock")
            .port(0)
            .containerConfig(ContainerConfig(
                containerType = ContainerType.DOCKER,
                containerName = "test-container",
                dockerHost = "unix:///var/run/docker.sock"
            ))
            .build()
    }

    @Test
    @DisplayName("Test Docker connection creation")
    fun testDockerConnectionCreation() {
        val connection = DockerConnectionImpl(config)
        assertNotNull(connection)
    }

    @Test
    @DisplayName("Test connection metadata")
    fun testConnectionMetadata() {
        val connection = DockerConnectionImpl(config)
        val metadata = connection.getMetadata()

        assertEquals(ConnectionType.DOCKER, metadata.type)
        assertEquals("unix:///var/run/docker.sock", metadata.host)
        assertEquals(0, metadata.port)
    }

    @Test
    @DisplayName("Test metadata contains container name")
    fun testMetadataContainsContainerName() {
        val connection = DockerConnectionImpl(config)
        val metadata = connection.getMetadata()

        assertEquals("test-container", metadata.properties["containerName"])
    }

    @Test
    @DisplayName("Test metadata shows Docker protocol")
    fun testMetadataShowsDockerProtocol() {
        val connection = DockerConnectionImpl(config)
        val metadata = connection.getMetadata()

        assertEquals("Docker", metadata.properties["protocol"])
    }

    @Test
    @DisplayName("Test metadata shows container type")
    fun testMetadataShowsContainerType() {
        val connection = DockerConnectionImpl(config)
        val metadata = connection.getMetadata()

        assertEquals("DOCKER", metadata.properties["containerType"])
    }

    @Test
    @DisplayName("Test isConnected returns false before connection")
    fun testIsConnectedBeforeConnection() {
        val connection = DockerConnectionImpl(config)
        assertFalse(connection.isConnected())
    }

    @Test
    @DisplayName("Test execute fails when not connected")
    fun testExecuteFailsWhenNotConnected() {
        val connection = DockerConnectionImpl(config)
        val result = connection.execute("echo test")

        assertFalse(result.success)
        assertTrue(result.errorOutput.contains("Not connected"))
    }

    @Test
    @DisplayName("Test upload fails when not connected")
    fun testUploadFailsWhenNotConnected() {
        val connection = DockerConnectionImpl(config)
        val result = connection.uploadFile("/tmp/test.txt", "/tmp/remote.txt")

        assertFalse(result.success)
    }

    @Test
    @DisplayName("Test download fails when not connected")
    fun testDownloadFailsWhenNotConnected() {
        val connection = DockerConnectionImpl(config)
        val result = connection.downloadFile("/tmp/remote.txt", "/tmp/local.txt")

        assertFalse(result.success)
    }

    @Test
    @DisplayName("Test health check when not connected")
    fun testHealthCheckWhenNotConnected() {
        val connection = DockerConnectionImpl(config)
        val health = connection.getHealth()

        assertFalse(health.isHealthy)
    }

    @Test
    @DisplayName("Test config validation")
    fun testConfigValidation() {
        val connection = DockerConnectionImpl(config)
        val result = connection.validateConfig()

        assertTrue(result.isSuccess())
    }

    @Test
    @DisplayName("Test disconnect when not connected")
    fun testDisconnectWhenNotConnected() {
        val connection = DockerConnectionImpl(config)

        assertDoesNotThrow {
            connection.disconnect()
        }
    }

    @Test
    @DisplayName("Test close releases resources")
    fun testCloseReleasesResources() {
        val connection = DockerConnectionImpl(config)

        assertDoesNotThrow {
            connection.close()
        }

        assertFalse(connection.isConnected())
    }

    @Test
    @DisplayName("Test metadata display name format")
    fun testMetadataDisplayNameFormat() {
        val connection = DockerConnectionImpl(config)
        val metadata = connection.getMetadata()

        assertEquals("docker://test-container", metadata.displayName)
    }

    @Test
    @DisplayName("Test connection with remote Docker host")
    fun testConnectionWithRemoteDockerHost() {
        val remoteConfig = ConnectionConfigBuilder()
            .type(ConnectionType.DOCKER)
            .host("tcp://remote-docker:2375")
            .port(2375)
            .containerConfig(ContainerConfig(
                containerType = ContainerType.DOCKER,
                containerName = "remote-container",
                dockerHost = "tcp://remote-docker:2375"
            ))
            .build()

        val connection = DockerConnectionImpl(remoteConfig)
        val metadata = connection.getMetadata()

        assertEquals("tcp://remote-docker:2375", metadata.properties["dockerHost"])
    }

    @Test
    @DisplayName("Test connection with container image")
    fun testConnectionWithContainerImage() {
        val imageConfig = ConnectionConfigBuilder()
            .type(ConnectionType.DOCKER)
            .host("unix:///var/run/docker.sock")
            .port(0)
            .containerConfig(ContainerConfig(
                containerType = ContainerType.DOCKER,
                containerName = "test-container",
                image = "nginx:latest"
            ))
            .build()

        val connection = DockerConnectionImpl(imageConfig)
        val metadata = connection.getMetadata()

        assertEquals("nginx:latest", metadata.properties["image"])
    }

    @Test
    @DisplayName("Test connection with network")
    fun testConnectionWithNetwork() {
        val networkConfig = ConnectionConfigBuilder()
            .type(ConnectionType.DOCKER)
            .host("unix:///var/run/docker.sock")
            .port(0)
            .containerConfig(ContainerConfig(
                containerType = ContainerType.DOCKER,
                containerName = "test-container",
                network = "custom-network"
            ))
            .build()

        val connection = DockerConnectionImpl(networkConfig)
        val metadata = connection.getMetadata()

        assertEquals("custom-network", metadata.properties["network"])
    }

    @Test
    @DisplayName("Test Docker connection metadata")
    fun testDockerConnectionMetadata() {
        val connection = DockerConnectionImpl(config)
        val metadata = connection.getMetadata()
        
        assertEquals(ConnectionType.DOCKER, metadata.type)
        assertEquals("test-container", metadata.host)
    }

    @Test
    @DisplayName("Test Docker connection builder")
    fun testDockerConnectionBuilder() {
        val connection = ConnectionFactory.build {
            type(ConnectionType.DOCKER)
            host("test-container")
        }
        
        val metadata = connection.getMetadata()
        assertEquals(ConnectionType.DOCKER, metadata.type)
        assertEquals("test-container", metadata.host)
    }

    @Test
    @DisplayName("Test Docker connection configuration")
    fun testDockerConnectionConfiguration() {
        val config = ConnectionConfigBuilder()
            .type(ConnectionType.DOCKER)
            .host("test-container")
            .port(0)
            .build()
            
        assertTrue(config.validate().isSuccess())
        assertEquals(ConnectionType.DOCKER, config.type)
        assertEquals("test-container", config.host)
    }

    @Test
    @DisplayName("Test Docker connection validation")
    fun testDockerConnectionValidation() {
        val validConfig = ConnectionConfigBuilder()
            .type(ConnectionType.DOCKER)
            .host("test-container")
            .build()
            
        val result = validConfig.validate()
        assertTrue(result.isSuccess())
    }
}
