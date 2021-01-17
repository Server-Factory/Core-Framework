package net.milosvasic.factory.component.installer.step.deploy

import net.milosvasic.factory.common.DataHandler
import net.milosvasic.factory.common.filesystem.FilePathBuilder
import net.milosvasic.factory.common.obtain.Obtain
import net.milosvasic.factory.component.installer.step.RemoteOperationInstallationStep
import net.milosvasic.factory.configuration.variable.Variable
import net.milosvasic.factory.execution.flow.implementation.CommandFlow
import net.milosvasic.factory.log
import net.milosvasic.factory.operation.OperationResult
import net.milosvasic.factory.remote.Connection
import net.milosvasic.factory.remote.Remote
import net.milosvasic.factory.remote.ssh.SSH
import net.milosvasic.factory.security.Permission
import net.milosvasic.factory.security.Permissions
import net.milosvasic.factory.terminal.Terminal
import net.milosvasic.factory.terminal.TerminalCommand
import net.milosvasic.factory.terminal.command.*
import java.io.File
import java.nio.file.InvalidPathException

open class Deploy(what: String, private val where: String) : RemoteOperationInstallationStep<SSH>() {

    companion object {

        const val SEPARATOR_FROM_TO = ":"
        const val SEPARATOR_DEFINITION = "@"
        const val PROTOTYPE_PREFIX = "proto."
        const val SOFTWARE_CONFIGURATION_NAME = "Deployment dependencies"
    }

    private val whatFile = File(what)
    private var remote: Remote? = null
    private var terminal: Terminal? = null
    private val tmpPath = tmpDirectory().absolutePath
    private val excludes = listOf("$PROTOTYPE_PREFIX*")

    private val onDirectoryCreated = object : DataHandler<OperationResult> {
        override fun onData(data: OperationResult?) {

            if (data == null || !data.success) {

                finish(false)
                return
            }
            if (whatFile.exists()) {
                if (whatFile.isDirectory) {
                    try {

                        processFiles(whatFile)
                    } catch (e: IllegalStateException) {

                        log.e(e)
                        finish(false)
                    } catch (e: IllegalArgumentException) {

                        log.e(e)
                        finish(false)
                    }
                } else {

                    log.e("${whatFile.absolutePath} is not directory")
                    finish(false)
                }
            } else {
                log.e("File does not exist: ${whatFile.absolutePath}")
                finish(false)
            }
        }
    }

    @Throws(IllegalArgumentException::class, IllegalStateException::class)
    override fun getFlow(): CommandFlow {

        connection?.let { conn ->
            terminal = conn.getTerminal()
            remote = connection?.getRemote()

            terminal?.let { term ->
                remote?.let { rmt ->

                    val scpObtain = object : Obtain<TerminalCommand> {

                        @Throws(IllegalArgumentException::class, IllegalStateException::class)
                        override fun obtain() = getScp(rmt)
                    }

                    val flow = CommandFlow()
                        .width(conn)
                        .perform(MkdirCommand(where), onDirectoryCreated)
                        .width(term)
                        .perform(TarCommand(whatFile.absolutePath, getLocalTar()))
                        .perform(scpObtain)
                        .width(conn)
                        .perform(UnTarCommand(getRemoteTar(), where))
                        .perform(RmCommand(getRemoteTar()))

                    try {
                        val protoCleanup = getProtoCleanup()
                        flow.perform(protoCleanup)
                    } catch (e: IllegalArgumentException) {

                        log.w(e)
                    }

                    return flow
                        .width(term)
                        .perform(RmCommand(getLocalTar()))
                        .width(conn)
                        .perform(getSecurityChanges(rmt))
                }
            }
        }
        throw IllegalArgumentException("No proper connection provided")
    }

    override fun finish(success: Boolean) {
        try {

            cleanupFiles(whatFile)
            super.finish(success)
        } catch (e: IllegalStateException) {

            log.e(e)
            super.finish(false)
        }
    }

    override fun getOperation() = DeployOperation()

    @Throws(IllegalArgumentException::class)
    fun setConnection(conn: Connection): Deploy {

        if (conn is SSH) {

            connection = conn
            return this
        }
        val msg = "${conn::class.simpleName} is not supported, only ${SSH::class.simpleName}"
        throw IllegalArgumentException(msg)
    }

    protected open fun getScpCommand() = Commands.SCP

    @Throws(InvalidPathException::class, IllegalStateException::class)
    protected open fun getScp(remote: Remote): TerminalCommand = ScpCommand(getLocalTar(), where, remote)

    @Throws(IllegalArgumentException::class)
    protected open fun getProtoCleanup(): TerminalCommand {

        if (excludes.isEmpty()) {
            throw IllegalArgumentException("No excludes available")
        }
        val excluded = mutableListOf<String>()
        excludes.forEach {
            val exclude = FindAndRemoveCommand(it, Commands.HERE)
            excluded.add(exclude.command)
        }
        return ConcatenateCommand(*excluded.toTypedArray())
    }

    protected open fun getSecurityChanges(remote: Remote): TerminalCommand {

        val chown = Commands.chown(remote.getAccountName(), where)
        val chgrp = Commands.chgrp(remote.getAccountName(), where)
        val permissions = Permissions(Permission.ALL, Permission.NONE, Permission.NONE)
        val chmod = Commands.chmod(where, permissions.obtain())
        return ConcatenateCommand(chown, chgrp, chmod)
    }

    @Throws(IllegalStateException::class)
    private fun processFiles(directory: File) {
        val fileList = directory.listFiles()
        fileList?.let { files ->
            files.forEach { file ->
                if (file.isDirectory) {
                    processFiles(file)
                } else if (file.name.toLowerCase().startsWith(PROTOTYPE_PREFIX)) {
                    processFile(directory, file)
                }
            }
        }
    }

    @Throws(IllegalStateException::class)
    private fun cleanupFiles(directory: File) {
        val fileList = directory.listFiles()
        fileList?.let { files ->
            files.forEach { file ->
                if (file.isDirectory) {
                    cleanupFiles(file)
                } else if (file.name.toLowerCase().startsWith(PROTOTYPE_PREFIX)) {
                    val toRemove = File(directory, getName(file))
                    cleanupFile(toRemove)
                }
            }
        }
    }

    private fun cleanupFile(file: File) {
        if (file.exists()) {
            if (file.delete()) {
                log.v("File is removed: ${file.absolutePath}")
            } else {
                log.w("File could not be removed: ${file.absolutePath}")
            }
        } else {
            log.w("File does not exist: ${file.absolutePath}")
        }
    }

    @Throws(IllegalStateException::class)
    private fun processFile(directory: File, file: File) {
        if (!file.exists()) {
            throw IllegalStateException("File does not exist: ${file.absolutePath}")
        }
        log.v("Processing prototype file: ${file.absolutePath}")
        val content = file.readText()
        if (content.isNotEmpty() && !content.isBlank()) {
            val parsedContent = Variable.parse(content)
            val destination = File(directory, getName(file))
            if (destination.exists()) {
                throw IllegalStateException("Destination file already exist: ${destination.absolutePath}")
            } else {
                if (destination.createNewFile()) {
                    destination.writeText(parsedContent)
                } else {
                    throw IllegalStateException("Can't create destination file: ${destination.absolutePath}")
                }
            }
        }
    }

    private fun getName(file: File) = file.name.toLowerCase().replace(PROTOTYPE_PREFIX, "")

    private fun tmpDirectory(): File {

        val root = File(File.separator)
        val path = FilePathBuilder()
            .addContext(root)
            .addContext("tmp")
            .build()

        return File(path)
    }

    @Throws(InvalidPathException::class)
    private fun getRemoteTar(): String {

        return FilePathBuilder()
            .addContext(where)
            .addContext("${whatFile.name}${Commands.TAR_EXTENSION}")
            .build()
    }

    @Throws(InvalidPathException::class)
    protected fun getLocalTar(): String {

        return FilePathBuilder()
            .addContext(tmpPath)
            .addContext("${whatFile.name}${Commands.TAR_EXTENSION}")
            .build()
    }
}