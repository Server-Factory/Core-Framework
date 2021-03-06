package net.milosvasic.factory.test.implementation

import net.milosvasic.factory.EMPTY
import net.milosvasic.factory.component.installer.step.deploy.Deploy
import net.milosvasic.factory.remote.Remote
import net.milosvasic.factory.security.Permission
import net.milosvasic.factory.security.Permissions
import net.milosvasic.factory.terminal.TerminalCommand
import net.milosvasic.factory.terminal.command.ChmodCommand
import net.milosvasic.factory.terminal.command.Commands
import net.milosvasic.factory.terminal.command.CpCommand
import net.milosvasic.factory.terminal.command.RawTerminalCommand
import java.nio.file.InvalidPathException

class StubDeploy(
        what: String,
        private val where: String,
        private val protoStubs: List<String>
) : Deploy(what, where) {

    @Throws(InvalidPathException::class)
    override fun getScp(remote: Remote) = CpCommand(getLocalTar(), where)

    override fun getScpCommand() = Commands.CP

    @Throws(IllegalArgumentException::class)
    override fun getProtoCleanup(): TerminalCommand {

        if (protoStubs.isEmpty()) {
            throw IllegalArgumentException("No proto stubs available")
        }
        var command = String.EMPTY
        protoStubs.forEachIndexed { index, it ->
            if (index > 0) {
                command += " && "
            }
            command += Commands.rm("$where/$it")
        }
        return RawTerminalCommand(command)
    }

    override fun getSecurityChanges(remote: Remote): TerminalCommand {

        val permissions = Permissions(Permission.ALL, Permission.NONE, Permission.NONE)
        return ChmodCommand(where, permissions.obtain())
    }
}