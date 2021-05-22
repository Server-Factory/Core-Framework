package net.milosvasic.factory.terminal.command

import net.milosvasic.factory.terminal.TerminalCommand

class GitVerifyRepository(repository: String) : TerminalCommand(Commands.gitVerifyRepository(repository))