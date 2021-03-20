package net.milosvasic.factory.firewall

import net.milosvasic.factory.terminal.TerminalCommand
import net.milosvasic.factory.terminal.command.Commands
import java.nio.file.InvalidPathException

class DisableIptablesForMdns
@Throws(InvalidPathException::class, IllegalStateException::class)
constructor() :
    @Throws(InvalidPathException::class, IllegalStateException::class)
    TerminalCommand(Commands.disableIptablesForMdns())