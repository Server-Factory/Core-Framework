package net.milosvasic.factory.terminal.command

import net.milosvasic.factory.terminal.TerminalCommand
import java.nio.file.InvalidPathException

class IpAddressObtainCommand
@Throws(InvalidPathException::class) constructor(host: String) :
    @Throws(InvalidPathException::class) TerminalCommand(Commands.getIpAddress(host))