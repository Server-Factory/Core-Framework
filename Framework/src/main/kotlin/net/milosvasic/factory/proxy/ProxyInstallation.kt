package net.milosvasic.factory.proxy

import net.milosvasic.factory.component.installer.step.RemoteOperationInstallationStep
import net.milosvasic.factory.execution.flow.implementation.CommandFlow
import net.milosvasic.factory.log
import net.milosvasic.factory.remote.Connection
import net.milosvasic.factory.remote.ssh.SSH
import net.milosvasic.factory.terminal.command.EchoCommand

class ProxyInstallation(private val proxy: Proxy) : RemoteOperationInstallationStep<SSH>() {

    @Throws(IllegalArgumentException::class, IllegalStateException::class)
    override fun getFlow(): CommandFlow {

        connection?.let { conn ->

            log.i(proxy.print())
            val validator = ProxyValidator()
            return if (validator.validate(proxy)) {

                val installProxy = ProxyInstallationCommand()
                CommandFlow()
                    .width(conn)
                    .perform(installProxy)
            } else {

                val msg = "No valid Proxy configuration provided"
                val echo = EchoCommand(msg)

                log.i(msg)

                CommandFlow()
                    .width(conn)
                    .perform(echo)
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