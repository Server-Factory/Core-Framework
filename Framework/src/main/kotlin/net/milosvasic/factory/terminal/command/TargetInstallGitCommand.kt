package net.milosvasic.factory.terminal.command

import net.milosvasic.factory.deployment.Target
import net.milosvasic.factory.terminal.TerminalCommand

class TargetInstallGitCommand(target: Target) :

    @Throws(IllegalArgumentException::class, IllegalStateException::class)
    TerminalCommand(Commands.targetInstallGit(target))