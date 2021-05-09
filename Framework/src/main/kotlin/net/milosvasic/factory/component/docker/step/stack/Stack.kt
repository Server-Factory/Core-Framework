package net.milosvasic.factory.component.docker.step.stack

import net.milosvasic.factory.common.filesystem.FilePathBuilder
import net.milosvasic.factory.component.docker.DockerCommand
import net.milosvasic.factory.component.docker.DockerInstallationOperation
import net.milosvasic.factory.component.docker.command.DockerComposeUp
import net.milosvasic.factory.component.docker.step.DockerInstallationStep
import net.milosvasic.factory.execution.flow.implementation.CommandFlow
import net.milosvasic.factory.security.Permission
import net.milosvasic.factory.security.Permissions
import net.milosvasic.factory.terminal.TerminalCommand
import net.milosvasic.factory.terminal.command.Commands
import net.milosvasic.factory.terminal.command.ConcatenateCommand
import java.io.File
import java.nio.file.InvalidPathException

open class Stack(
        private val composeYmlPath: String,
        composeFileName: String = DEFAULT_COMPOSE_FILE_NAME,
        private val composeFileExtension: String = DEFAULT_COMPOSE_FILE_EXTENSION

) : DockerInstallationStep() {

    companion object {

        private const val DEFAULT_COMPOSE_FILE_NAME: String = "docker-compose"
        private const val DEFAULT_COMPOSE_FILE_EXTENSION: String = ".yml"
    }

    private val flags = "-d --remove-orphans"
    private val composeFile = "$composeFileName$composeFileExtension"


    @Throws(IllegalArgumentException::class, IllegalStateException::class)
    override fun getFlow(): CommandFlow {
        connection?.let { conn ->

            val path = getYmlPath()
            val command = DockerComposeUp(path, flags)

            return CommandFlow()
                    .width(conn)
                    .perform(command)
                    .perform(getCompletionCommand(command))
        }
        throw IllegalArgumentException("No connection provided")
    }

    override fun getOperation() = DockerInstallationOperation()

    protected open fun getScriptContent(command: String): String {
        val bashHead = "#!/bin/sh"
        return Commands.printf("$bashHead\\n$command")
    }

    @Throws(InvalidPathException::class)
    private fun getCompletionCommand(command: TerminalCommand): ConcatenateCommand {

        val stop = "stop.sh"
        val start = "start.sh"
        val restart = "restart.sh"
        val path = getYmlPath()
        val file = File(path)
        val directory = file.parentFile

        val stopShellScript = FilePathBuilder()
                .addContext(directory)
                .addContext(stop)
                .build()

        val startShellScript = FilePathBuilder()
                .addContext(directory)
                .addContext(start)
                .build()

        val restartShellScript = FilePathBuilder()
                .addContext(directory)
                .addContext(restart)
                .build()

        val restartCmd = "sh stop.sh;\\nsh start.sh;"
        val stopCmd = command.command
                .replace(DockerCommand.UP.obtain(), DockerCommand.DOWN.obtain())
                .replace(flags, "")

        val startGenerate = generate(command.command, startShellScript)
        val stopGenerate = generate(stopCmd, stopShellScript)
        val restartGenerate = generate(restartCmd, restartShellScript)

        val ownershipAndPermissionsStart = getOwnershipAndPermissions(startShellScript)
        val ownershipAndPermissionsStop = getOwnershipAndPermissions(stopShellScript)
        val ownershipAndPermissionsRestart = getOwnershipAndPermissions(restartShellScript)

        return ConcatenateCommand(
                startGenerate,
                stopGenerate,
                restartGenerate,
                ownershipAndPermissionsStart,
                ownershipAndPermissionsStop,
                ownershipAndPermissionsRestart
        )
    }

    @Throws(IllegalArgumentException::class)
    private fun getOwnershipAndPermissions(script: String): String {
        val account = connection?.getRemote()?.getAccountName()
                ?: throw IllegalArgumentException("No host for connection provided")
        val permissions = Permissions(Permission.ALL, Permission.NONE, Permission.NONE)
        return Commands.concatenate(
                Commands.chown(account, script),
                Commands.chgrp(account, script),
                Commands.chmod(script, permissions.obtain())
        )
    }

    @Throws(InvalidPathException::class)
    private fun getYmlPath(): String {

        val path = FilePathBuilder().addContext(composeYmlPath)
        if (!path.getPath().endsWith(composeFileExtension)) {
            path.addContext(composeFile)
        }
        return path.build()
    }

    private fun generate(command: String, script: String): String {
        val chmod = "chmod u+x $script"
        return "${getScriptContent(command)} > $script; $chmod"
    }
}


