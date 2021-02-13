package net.milosvasic.factory.proxy

import net.milosvasic.factory.common.filesystem.FilePathBuilder
import net.milosvasic.factory.component.installer.step.RemoteOperationInstallationStep
import net.milosvasic.factory.configuration.variable.Context
import net.milosvasic.factory.configuration.variable.Key
import net.milosvasic.factory.configuration.variable.PathBuilder
import net.milosvasic.factory.configuration.variable.Variable
import net.milosvasic.factory.execution.flow.implementation.CommandFlow
import net.milosvasic.factory.log
import net.milosvasic.factory.remote.Connection
import net.milosvasic.factory.remote.ssh.SSH
import net.milosvasic.factory.terminal.command.Commands
import net.milosvasic.factory.terminal.command.MkdirCommand

class ProxyInstallation(private val proxy: Proxy) : RemoteOperationInstallationStep<SSH>() {

    @Throws(IllegalArgumentException::class, IllegalStateException::class)
    override fun getFlow(): CommandFlow {

        connection?.let { conn ->

            log.i(proxy.print())
            val validator = ProxyValidator()
            if (validator.validate(proxy)) {

                val rootPath = PathBuilder()
                    .addContext(Context.Server)
                    .setKey(Key.ServerHome)
                    .build()

                val root = Variable.get(rootPath)

                val proxyRoot = FilePathBuilder()
                    .addContext(root)
                    .addContext(Commands.DIRECTORY_SERVER)
                    .addContext(Commands.DIRECTORY_PROXY)
                    .build()

                val mkdir = MkdirCommand(proxyRoot)
                val installProxy = ProxyInstallationCommand(proxy)

                return CommandFlow()
                    .width(conn)
                    .perform(mkdir)
                    .perform(installProxy)

            } else {

                throw IllegalArgumentException("Invalid proxy: ${proxy.print()}")
            }
        }
        throw IllegalArgumentException("No proper connection provided")
    }

    override fun getOperation() = ProxyInstallationOperation()

    @Throws(IllegalArgumentException::class)
    fun setConnection(conn: Connection): ProxyInstallation {

        if (conn is SSH) {

            connection = conn
            return this
        }
        val msg = "${conn::class.simpleName} is not supported, only ${SSH::class.simpleName}"
        throw IllegalArgumentException(msg)
    }
}