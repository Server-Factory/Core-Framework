package net.milosvasic.factory.connection

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Assertions.*
import net.milosvasic.factory.validation.ValidationResult

/**
 * Unit tests for ConnectionConfig and related classes.
 *
 * Tests configuration validation, credentials handling,
 * cloud config, container config, and builder pattern.
 *
 * @author Mail Server Factory Team
 * @since 3.1.0
 */
class ConnectionConfigTest {

    @Test
    @DisplayName("Test valid SSH configuration")
    fun testValidSSHConfiguration() {
        val config = ConnectionConfigBuilder()
            .type(ConnectionType.SSH)
            .host("test.example.com")
            .port(22)
            .credentials(Credentials("testuser", password = "testpass"))
            .build()

        val result = config.validate()
        assertTrue(result.isSuccess())
    }

    @Test
    @DisplayName("Test invalid host")
    fun testInvalidHost() {
        val config = ConnectionConfigBuilder()
            .type(ConnectionType.SSH)
            .host("") // Invalid empty host
            .port(22)
            .credentials(Credentials("testuser", password = "testpass"))
            .build()

        val result = config.validate()
        assertTrue(result.isFailed())
    }

    @Test
    @DisplayName("Test invalid port")
    fun testInvalidPort() {
        val config = ConnectionConfigBuilder()
            .type(ConnectionType.SSH)
            .host("test.example.com")
            .port(70000) // Invalid port > 65535
            .credentials(Credentials("testuser", password = "testpass"))
            .build()

        val result = config.validate()
        assertTrue(result.isFailed())
    }

    @Test
    @DisplayName("Test Local connection doesn't require host validation")
    fun testLocalConnectionNoHostValidation() {
        val config = ConnectionConfigBuilder()
            .type(ConnectionType.LOCAL)
            .host("localhost")
            .port(0)
            .build()

        val result = config.validate()
        assertTrue(result.isSuccess())
    }

    @Test
    @DisplayName("Test bastion config validation - missing bastion")
    fun testBastionConfigValidationMissingBastion() {
        val config = ConnectionConfigBuilder()
            .type(ConnectionType.SSH_BASTION)
            .host("target.internal")
            .port(22)
            .credentials(Credentials("testuser", password = "testpass"))
            // Missing bastionConfig
            .build()

        val result = config.validate()
        assertTrue(result.isFailed())
        assertTrue((result as ValidationResult.Invalid).reason.contains("Bastion"))
    }

    @Test
    @DisplayName("Test valid bastion configuration")
    fun testValidBastionConfiguration() {
        val bastionConfig = ConnectionConfigBuilder()
            .type(ConnectionType.SSH)
            .host("bastion.example.com")
            .port(22)
            .credentials(Credentials("bastionuser", password = "bastionpass"))
            .build()

        val config = ConnectionConfigBuilder()
            .type(ConnectionType.SSH_BASTION)
            .host("target.internal")
            .port(22)
            .credentials(Credentials("targetuser", password = "targetpass"))
            .bastionConfig(bastionConfig)
            .build()

        val result = config.validate()
        assertTrue(result.isSuccess())
    }

    @Test
    @DisplayName("Test container config validation - missing container config")
    fun testContainerConfigValidationMissing() {
        val config = ConnectionConfigBuilder()
            .type(ConnectionType.DOCKER)
            .host("unix:///var/run/docker.sock")
            .port(0)
            // Missing containerConfig
            .build()

        val result = config.validate()
        assertTrue(result.isFailed())
    }

    @Test
    @DisplayName("Test Docker config validation - missing container name")
    fun testDockerConfigValidationMissingName() {
        val config = ConnectionConfigBuilder()
            .type(ConnectionType.DOCKER)
            .host("unix:///var/run/docker.sock")
            .port(0)
            .containerConfig(ContainerConfig(
                containerType = ContainerType.DOCKER
                // Missing containerName
            ))
            .build()

        val result = config.validate()
        assertTrue(result.isFailed())
    }

    @Test
    @DisplayName("Test valid Docker configuration")
    fun testValidDockerConfiguration() {
        val config = ConnectionConfigBuilder()
            .type(ConnectionType.DOCKER)
            .host("unix:///var/run/docker.sock")
            .port(0)
            .containerConfig(ContainerConfig(
                containerType = ContainerType.DOCKER,
                containerName = "test-container"
            ))
            .build()

        val result = config.validate()
        assertTrue(result.isSuccess())
    }

    @Test
    @DisplayName("Test Kubernetes config validation - missing namespace")
    fun testKubernetesConfigValidationMissingNamespace() {
        val config = ConnectionConfigBuilder()
            .type(ConnectionType.KUBERNETES)
            .host("k8s-cluster.example.com")
            .port(443)
            .containerConfig(ContainerConfig(
                containerType = ContainerType.KUBERNETES,
                podSelector = "app=test"
                // Missing namespace
            ))
            .build()

        val result = config.validate()
        assertTrue(result.isFailed())
    }

    @Test
    @DisplayName("Test valid Kubernetes configuration")
    fun testValidKubernetesConfiguration() {
        val config = ConnectionConfigBuilder()
            .type(ConnectionType.KUBERNETES)
            .host("k8s-cluster.example.com")
            .port(443)
            .containerConfig(ContainerConfig(
                containerType = ContainerType.KUBERNETES,
                namespace = "default",
                podSelector = "app=test"
            ))
            .build()

        val result = config.validate()
        assertTrue(result.isSuccess())
    }

    @Test
    @DisplayName("Test cloud config validation - missing cloud config")
    fun testCloudConfigValidationMissing() {
        val config = ConnectionConfigBuilder()
            .type(ConnectionType.AWS_SSM)
            .host("i-1234567890")
            .port(0)
            // Missing cloudConfig
            .build()

        val result = config.validate()
        assertTrue(result.isFailed())
    }

    @Test
    @DisplayName("Test AWS config validation - missing required fields")
    fun testAWSConfigValidationMissingFields() {
        val config = ConnectionConfigBuilder()
            .type(ConnectionType.AWS_SSM)
            .host("i-1234567890")
            .port(0)
            .cloudConfig(CloudConfig(
                provider = CloudProvider.AWS
                // Missing region and instanceId
            ))
            .build()

        val result = config.validate()
        assertTrue(result.isFailed())
    }

    @Test
    @DisplayName("Test valid AWS configuration")
    fun testValidAWSConfiguration() {
        val config = ConnectionConfigBuilder()
            .type(ConnectionType.AWS_SSM)
            .host("i-1234567890")
            .port(0)
            .cloudConfig(CloudConfig(
                provider = CloudProvider.AWS,
                region = "us-east-1",
                instanceId = "i-1234567890"
            ))
            .build()

        val result = config.validate()
        assertTrue(result.isSuccess())
    }

    @Test
    @DisplayName("Test Azure config validation")
    fun testAzureConfigValidation() {
        val config = ConnectionConfigBuilder()
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

        val result = config.validate()
        assertTrue(result.isSuccess())
    }

    @Test
    @DisplayName("Test GCP config validation")
    fun testGCPConfigValidation() {
        val config = ConnectionConfigBuilder()
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

        val result = config.validate()
        assertTrue(result.isSuccess())
    }

    @Test
    @DisplayName("Test credentials validation - valid password")
    fun testCredentialsValidationValidPassword() {
        val credentials = Credentials("testuser", password = "testpass")

        val result = credentials.validate()
        assertTrue(result.isSuccess())
    }

    @Test
    @DisplayName("Test credentials validation - valid key")
    fun testCredentialsValidationValidKey() {
        val credentials = Credentials("testuser", keyPath = "/path/to/key")

        val result = credentials.validate()
        assertTrue(result.isSuccess())
    }

    @Test
    @DisplayName("Test credentials validation - no auth method")
    fun testCredentialsValidationNoAuthMethod() {
        val credentials = Credentials("testuser")

        val result = credentials.validate()
        assertTrue(result.isFailed())
    }

    @Test
    @DisplayName("Test credentials validation - invalid username")
    fun testCredentialsValidationInvalidUsername() {
        val credentials = Credentials("ab", password = "testpass") // Too short

        val result = credentials.validate()
        assertTrue(result.isFailed())
    }

    @Test
    @DisplayName("Test getDisplayName for SSH")
    fun testGetDisplayNameSSH() {
        val config = ConnectionConfigBuilder()
            .type(ConnectionType.SSH)
            .host("test.example.com")
            .port(22)
            .credentials(Credentials("testuser", password = "testpass"))
            .build()

        val displayName = config.getDisplayName()
        assertEquals("testuser@test.example.com:22", displayName)
    }

    @Test
    @DisplayName("Test getDisplayName for Local")
    fun testGetDisplayNameLocal() {
        val config = ConnectionConfigBuilder()
            .type(ConnectionType.LOCAL)
            .host("localhost")
            .port(0)
            .build()

        val displayName = config.getDisplayName()
        assertEquals("localhost", displayName)
    }

    @Test
    @DisplayName("Test getDisplayName for Docker")
    fun testGetDisplayNameDocker() {
        val config = ConnectionConfigBuilder()
            .type(ConnectionType.DOCKER)
            .host("unix:///var/run/docker.sock")
            .port(0)
            .containerConfig(ContainerConfig(
                containerType = ContainerType.DOCKER,
                containerName = "test-container"
            ))
            .build()

        val displayName = config.getDisplayName()
        assertEquals("docker://test-container", displayName)
    }

    @Test
    @DisplayName("Test getDisplayName for Kubernetes")
    fun testGetDisplayNameKubernetes() {
        val config = ConnectionConfigBuilder()
            .type(ConnectionType.KUBERNETES)
            .host("k8s-cluster.example.com")
            .port(443)
            .containerConfig(ContainerConfig(
                containerType = ContainerType.KUBERNETES,
                namespace = "production",
                podSelector = "app=web"
            ))
            .build()

        val displayName = config.getDisplayName()
        assertEquals("k8s://production/app=web", displayName)
    }

    @Test
    @DisplayName("Test ConnectionOptions properties")
    fun testConnectionOptionsProperties() {
        val options = ConnectionOptions(
            timeout = 60,
            retries = 5,
            properties = mapOf("key1" to "value1", "key2" to "value2")
        )

        assertEquals("value1", options.getProperty("key1"))
        assertEquals("value2", options.getProperty("key2"))
        assertEquals("default", options.getProperty("key3", "default"))
        assertTrue(options.hasProperty("key1"))
        assertFalse(options.hasProperty("key3"))
    }

    @Test
    @DisplayName("Test ConnectionType helper methods")
    fun testConnectionTypeHelperMethods() {
        assertTrue(ConnectionType.SSH.isSSH())
        assertTrue(ConnectionType.SSH_AGENT.isSSH())
        assertTrue(ConnectionType.SSH_CERTIFICATE.isSSH())
        assertTrue(ConnectionType.SSH_BASTION.isSSH())
        assertFalse(ConnectionType.DOCKER.isSSH())

        assertTrue(ConnectionType.DOCKER.isContainer())
        assertTrue(ConnectionType.KUBERNETES.isContainer())
        assertFalse(ConnectionType.SSH.isContainer())

        assertTrue(ConnectionType.AWS_SSM.isCloud())
        assertTrue(ConnectionType.AZURE_SERIAL.isCloud())
        assertTrue(ConnectionType.GCP_OS_LOGIN.isCloud())
        assertFalse(ConnectionType.SSH.isCloud())

        assertTrue(ConnectionType.SSH.isRemote())
        assertTrue(ConnectionType.DOCKER.isRemote())
        assertFalse(ConnectionType.LOCAL.isRemote())
    }

    @Test
    @DisplayName("Test encrypted password decryption")
    fun testEncryptedPasswordDecryption() {
        val credentials = Credentials("testuser", password = "plaintext")

        val decrypted = credentials.getDecryptedPassword()
        assertEquals("plaintext", decrypted)
    }

    @Test
    @DisplayName("Test builder pattern")
    fun testBuilderPattern() {
        val config = ConnectionConfigBuilder()
            .type(ConnectionType.SSH)
            .host("test.example.com")
            .port(2222)
            .credentials(Credentials("testuser", password = "testpass"))
            .options(ConnectionOptions(timeout = 60, retries = 5))
            .build()

        assertEquals(ConnectionType.SSH, config.type)
        assertEquals("test.example.com", config.host)
        assertEquals(2222, config.port)
        assertEquals("testuser", config.credentials?.username)
        assertEquals(60, config.options.timeout)
        assertEquals(5, config.options.retries)
    }
}
