package net.milosvasic.factory.terminal.command

import net.milosvasic.factory.deployment.Target
import net.milosvasic.factory.terminal.TerminalCommand

class TargetInstallGitCommand
@Throws(IllegalArgumentException::class, IllegalStateException::class)
constructor(target: Target) :
    TerminalCommand(Commands.targetInstallGit(target))