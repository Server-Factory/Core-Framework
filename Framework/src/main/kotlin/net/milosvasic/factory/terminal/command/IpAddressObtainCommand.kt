package net.milosvasic.factory.terminal.command

import net.milosvasic.factory.terminal.TerminalCommand
import java.nio.file.InvalidPathException

class IpAddressObtainCommand
@Throws(InvalidPathException::class) constructor(host: String) :
    TerminalCommand(Commands.getIpAddress(host))