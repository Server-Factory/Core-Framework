package net.milosvasic.factory.component.docker.step.stack

import net.milosvasic.factory.common.filesystem.FilePathBuilder
import net.milosvasic.factory.configuration.variable.Context
import net.milosvasic.factory.configuration.variable.Key
import net.milosvasic.factory.configuration.variable.PathBuilder
import net.milosvasic.factory.configuration.variable.Variable
import net.milosvasic.factory.terminal.TerminalCommand
import net.milosvasic.factory.terminal.command.Commands
import java.nio.file.InvalidPathException

class CheckCommand(containerName: String, timeout: Int) :
        TerminalCommand("${getCommand()} $containerName $timeout")

@Throws(InvalidPathException::class, IllegalStateException::class)
private fun getCommand(): String {

    val utilsHomePath = PathBuilder()
        .addContext(Context.Server)
        .setKey(Key.UtilsHome)
        .build()

    val utilsHome = Variable.get(utilsHomePath)

    return FilePathBuilder()
            .addContext(utilsHome)
            .addContext(Commands.SCRIPT_CHECK)
            .build()
}