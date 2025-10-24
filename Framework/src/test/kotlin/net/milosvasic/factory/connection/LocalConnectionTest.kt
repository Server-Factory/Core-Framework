package net.milosvasic.factory.connection

import net.milosvasic.factory.connection.impl.LocalConnectionImpl
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

/**
 * Unit tests for LocalConnectionImpl.
 *
 * Tests local command execution, file operations,
 * working directory management, and health checks.
 *
 * @author Mail Server Factory Team
 * @since 3.1.0
 */
class LocalConnectionTest {

    @TempDir
    lateinit var tempDir: Path

    private lateinit var connection: LocalConnectionImpl
    private lateinit var config: ConnectionConfig

    @BeforeEach
    fun setUp() {
        config = ConnectionConfigBuilder()
            .type(ConnectionType.LOCAL)
            .host("localhost")
            .port(0)
            .options(ConnectionOptions(properties = mapOf(
                "workingDirectory" to tempDir.toString()
            )))
            .build()

        connection = LocalConnectionImpl(config)
    }

    @AfterEach
    fun tearDown() {
        connection.disconnect()
    }

    @Test
    @DisplayName("Test connection establishment")
    fun testConnectionEstablishment() {
        val result = connection.connect()

        assertTrue(result.isSuccess())
        assertTrue(connection.isConnected())
    }

    @Test
    @DisplayName("Test connection with invalid working directory")
    fun testConnectionWithInvalidWorkingDirectory() {
        val invalidConfig = ConnectionConfigBuilder()
            .type(ConnectionType.LOCAL)
            .host("localhost")
            .port(0)
            .options(ConnectionOptions(properties = mapOf(
                "workingDirectory" to "/nonexistent/directory"
            )))
            .build()

        val invalidConnection = LocalConnectionImpl(invalidConfig)
        val result = invalidConnection.connect()

        assertTrue(result.isFailed())
    }

    @Test
    @DisplayName("Test simple command execution")
    fun testSimpleCommandExecution() {
        connection.connect()

        val result = connection.execute("echo 'Hello World'")

        assertTrue(result.success)
        assertTrue(result.output.contains("Hello World"))
        assertEquals(0, result.exitCode)
    }

    @Test
    @DisplayName("Test command execution without connection")
    fun testCommandExecutionWithoutConnection() {
        val result = connection.execute("echo test")

        assertFalse(result.success)
        assertTrue(result.errorOutput.contains("Not connected"))
    }

    @Test
    @DisplayName("Test command with exit code")
    fun testCommandWithExitCode() {
        connection.connect()

        val result = connection.execute("exit 42")

        assertFalse(result.success)
        assertEquals(42, result.exitCode)
    }

    @Test
    @DisplayName("Test command timeout")
    fun testCommandTimeout() {
        connection.connect()

        val result = connection.execute("sleep 10", timeout = 1)

        assertFalse(result.success)
        assertTrue(result.errorOutput.contains("timed out"))
    }

    @Test
    @DisplayName("Test file upload (copy)")
    fun testFileUpload() {
        connection.connect()

        // Create source file
        val sourceFile = File(tempDir.toFile(), "source.txt")
        sourceFile.writeText("Test content")

        // Upload (copy) file
        val destPath = File(tempDir.toFile(), "dest.txt").absolutePath
        val result = connection.uploadFile(sourceFile.absolutePath, destPath)

        assertTrue(result.success)
        assertEquals(sourceFile.length(), result.bytesTransferred)

        // Verify file was copied
        val destFile = File(destPath)
        assertTrue(destFile.exists())
        assertEquals("Test content", destFile.readText())
    }

    @Test
    @DisplayName("Test file upload with nonexistent source")
    fun testFileUploadWithNonexistentSource() {
        connection.connect()

        val result = connection.uploadFile("/nonexistent/file.txt", tempDir.toString() + "/dest.txt")

        assertFalse(result.success)
        assertTrue(result.message.contains("does not exist"))
    }

    @Test
    @DisplayName("Test file download (copy)")
    fun testFileDownload() {
        connection.connect()

        // Create source file
        val sourceFile = File(tempDir.toFile(), "source.txt")
        sourceFile.writeText("Download test content")

        // Download (copy) file
        val destPath = File(tempDir.toFile(), "downloaded.txt").absolutePath
        val result = connection.downloadFile(sourceFile.absolutePath, destPath)

        assertTrue(result.success)
        assertEquals(sourceFile.length(), result.bytesTransferred)

        // Verify file was copied
        val destFile = File(destPath)
        assertTrue(destFile.exists())
        assertEquals("Download test content", destFile.readText())
    }

    @Test
    @DisplayName("Test file download with nonexistent source")
    fun testFileDownloadWithNonexistentSource() {
        connection.connect()

        val result = connection.downloadFile("/nonexistent/file.txt", tempDir.toString() + "/dest.txt")

        assertFalse(result.success)
        assertTrue(result.message.contains("does not exist"))
    }

    @Test
    @DisplayName("Test health check when connected")
    fun testHealthCheckWhenConnected() {
        connection.connect()

        val health = connection.getHealth()

        assertTrue(health.isHealthy)
        assertTrue(health.latencyMs >= 0)
    }

    @Test
    @DisplayName("Test health check when not connected")
    fun testHealthCheckWhenNotConnected() {
        val health = connection.getHealth()

        assertFalse(health.isHealthy)
    }

    @Test
    @DisplayName("Test metadata")
    fun testMetadata() {
        connection.connect()

        val metadata = connection.getMetadata()

        assertEquals(ConnectionType.LOCAL, metadata.type)
        assertEquals("localhost", metadata.host)
        assertEquals(0, metadata.port)
        assertTrue(metadata.properties.containsKey("workingDirectory"))
    }

    @Test
    @DisplayName("Test disconnect")
    fun testDisconnect() {
        connection.connect()
        assertTrue(connection.isConnected())

        connection.disconnect()

        assertFalse(connection.isConnected())
    }

    @Test
    @DisplayName("Test execute with environment variables")
    fun testExecuteWithEnvironmentVariables() {
        connection.connect()

        val env = mapOf("TEST_VAR" to "test_value")
        // Use echo to print environment variable
        val result = connection.executeWithEnvironment("echo \$TEST_VAR", env)

        assertTrue(result.success)
        assertTrue(result.output.contains("test_value"))
    }

    @Test
    @DisplayName("Test change working directory")
    fun testChangeWorkingDirectory() {
        connection.connect()

        val newDir = File(tempDir.toFile(), "subdir")
        newDir.mkdirs()

        val changed = connection.changeWorkingDirectory(newDir.absolutePath)

        assertTrue(changed)
        assertEquals(newDir.absolutePath, connection.getWorkingDirectory()?.absolutePath)
    }

    @Test
    @DisplayName("Test change to nonexistent directory")
    fun testChangeToNonexistentDirectory() {
        connection.connect()

        val changed = connection.changeWorkingDirectory("/nonexistent/directory")

        assertFalse(changed)
    }

    @Test
    @DisplayName("Test command execution in working directory")
    fun testCommandExecutionInWorkingDirectory() {
        connection.connect()

        // Create a file in the working directory
        val testFile = File(tempDir.toFile(), "test.txt")
        testFile.writeText("test content")

        // Execute command that lists files
        val result = connection.execute("ls test.txt")

        assertTrue(result.success)
        assertTrue(result.output.contains("test.txt"))
    }

    @Test
    @DisplayName("Test get working directory")
    fun testGetWorkingDirectory() {
        connection.connect()

        val workDir = connection.getWorkingDirectory()

        assertNotNull(workDir)
        assertEquals(tempDir.toString(), workDir?.absolutePath)
    }

    @Test
    @DisplayName("Test file copy with subdirectories")
    fun testFileCopyWithSubdirectories() {
        connection.connect()

        // Create source file
        val sourceFile = File(tempDir.toFile(), "source.txt")
        sourceFile.writeText("Subdirectory test")

        // Create destination path with subdirectories
        val destPath = File(tempDir.toFile(), "sub1/sub2/dest.txt").absolutePath

        val result = connection.uploadFile(sourceFile.absolutePath, destPath)

        assertTrue(result.success)

        // Verify subdirectories were created
        val destFile = File(destPath)
        assertTrue(destFile.exists())
        assertEquals("Subdirectory test", destFile.readText())
    }

    @Test
    @DisplayName("Test large file transfer")
    fun testLargeFileTransfer() {
        connection.connect()

        // Create large file (1MB)
        val sourceFile = File(tempDir.toFile(), "large.txt")
        val content = "x".repeat(1024 * 1024)
        sourceFile.writeText(content)

        val destPath = File(tempDir.toFile(), "large_copy.txt").absolutePath
        val result = connection.uploadFile(sourceFile.absolutePath, destPath)

        assertTrue(result.success)
        assertEquals(sourceFile.length(), result.bytesTransferred)

        val destFile = File(destPath)
        assertEquals(sourceFile.length(), destFile.length())
    }

    @Test
    @DisplayName("Test config validation")
    fun testConfigValidation() {
        val result = connection.validateConfig()

        assertTrue(result.isSuccess())
    }

    @Test
    @DisplayName("Test multiple command executions")
    fun testMultipleCommandExecutions() {
        connection.connect()

        val result1 = connection.execute("echo 'Command 1'")
        val result2 = connection.execute("echo 'Command 2'")
        val result3 = connection.execute("echo 'Command 3'")

        assertTrue(result1.success)
        assertTrue(result2.success)
        assertTrue(result3.success)

        assertTrue(result1.output.contains("Command 1"))
        assertTrue(result2.output.contains("Command 2"))
        assertTrue(result3.output.contains("Command 3"))
    }

    @Test
    @DisplayName("Test command with stderr output")
    fun testCommandWithStderrOutput() {
        connection.connect()

        val result = connection.execute("echo 'error message' >&2")

        assertTrue(result.success)
        assertTrue(result.errorOutput.contains("error message") || result.output.contains("error message"))
    }

    @Test
    @DisplayName("Test connection close and reopen")
    fun testConnectionCloseAndReopen() {
        connection.connect()
        assertTrue(connection.isConnected())

        connection.close()
        assertFalse(connection.isConnected())

        val result = connection.connect()
        assertTrue(result.isSuccess())
        assertTrue(connection.isConnected())
    }
}
