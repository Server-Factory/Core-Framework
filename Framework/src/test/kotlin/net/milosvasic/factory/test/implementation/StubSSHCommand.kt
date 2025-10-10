package net.milosvasic.factory.test.implementation

import net.milosvasic.factory.execution.flow.implementation.ObtainableTerminalCommand
import net.milosvasic.factory.remote.Remote
import net.milosvasic.factory.remote.ssh.SSHCommand
import net.milosvasic.factory.terminal.TerminalCommand

class StubSSHCommand
@Throws(IllegalStateException::class)
constructor(remote: Remote, command: TerminalCommand) :
    SSHCommand(
        remote,
        command,
        sshCommand = if (command is ObtainableTerminalCommand) {

            command.obtainable.obtain().command
        } else {
            command.command
        }
    )
