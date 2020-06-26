package net.milosvasic.factory.component.installer.step.certificate

import net.milosvasic.factory.terminal.TerminalCommand
import net.milosvasic.factory.terminal.command.Commands

class SignRequestKeyCommand

@Throws(IllegalArgumentException::class, IllegalStateException::class)
constructor(name: String) : TerminalCommand(

        Commands.signRequestKey(name)
)