package net.milosvasic.factory.proxy

import net.milosvasic.factory.terminal.TerminalCommand
import net.milosvasic.factory.terminal.command.Commands
import java.nio.file.InvalidPathException

class ProxyInstallationCommand
@Throws(InvalidPathException::class, IllegalStateException::class)
constructor() :
    @Throws(InvalidPathException::class, IllegalStateException::class)
    TerminalCommand(Commands.installProxy())