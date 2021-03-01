package net.milosvasic.factory.component.docker.step.stack

import net.milosvasic.factory.common.filesystem.FilePathBuilder
import net.milosvasic.factory.configuration.variable.Context
import net.milosvasic.factory.configuration.variable.Key
import net.milosvasic.factory.configuration.variable.PathBuilder
import net.milosvasic.factory.configuration.variable.Variable
import net.milosvasic.factory.terminal.TerminalCommand

class CheckCommand(containerName: String, timeout: Int) :
        TerminalCommand("${getCommand()} $containerName $timeout")

private fun getCommand(): String {

    val serverHomePath = PathBuilder()
            .addContext(Context.Server)
            .setKey(Key.Home)
            .build()

    val serverHome = Variable.get(serverHomePath)

    return FilePathBuilder()
            .addContext(serverHome)
            .addContext("Utils")
            .addContext("check.sh")
            .build()
}