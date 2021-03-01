package net.milosvasic.factory.remote.ssh

import net.milosvasic.factory.EMPTY
import net.milosvasic.factory.configuration.ConfigurationManager
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

        remote.getAccountName(),
        if (remoteCommand is ObtainableTerminalCommand) {

            "${getCommandPrefix()}${remoteCommand.obtainable.obtain().command}"
        } else {
            "${getCommandPrefix()}${remoteCommand.command}"
        },
        remote.port,
        remote.getHost(preferIpAddress = false)
    )
) : TerminalCommand(sshCommand, configuration) {

    companion object {

        fun getCommandPrefix(): String {

            val proxy = ConfigurationManager.getConfiguration().getProxy()
            return try {

                proxy.getProxyHostname()
                "source /etc/profile >/dev/null 2>&1; "
            } catch (e: IllegalStateException) {

                String.EMPTY
            }
        }
    }
}