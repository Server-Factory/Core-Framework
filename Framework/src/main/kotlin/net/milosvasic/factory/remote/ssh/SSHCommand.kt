package net.milosvasic.factory.remote.ssh

import net.milosvasic.factory.execution.flow.implementation.ObtainableTerminalCommand
import net.milosvasic.factory.operation.command.CommandConfiguration
import net.milosvasic.factory.remote.Remote
import net.milosvasic.factory.terminal.TerminalCommand
import net.milosvasic.factory.terminal.command.Commands

open class SSHCommand
@Throws(IllegalStateException::class)
constructor(
    remote: Remote,
    val remoteCommand: TerminalCommand,
    configuration: MutableMap<CommandConfiguration, Boolean> = CommandConfiguration.DEFAULT.toMutableMap(),

    sshCommand: String = Commands.ssh(

        remote.account,
        if (remoteCommand is ObtainableTerminalCommand) {

            remoteCommand.obtainable.obtain().command
        } else {
            remoteCommand.command
        },
        remote.port,
        remote.getHost(preferIpAddress = false)
    )
) : TerminalCommand(sshCommand, configuration)