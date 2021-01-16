package net.milosvasic.factory.proxy

import net.milosvasic.factory.component.installer.step.RemoteOperationInstallationStep
import net.milosvasic.factory.execution.flow.implementation.CommandFlow
import net.milosvasic.factory.log
import net.milosvasic.factory.remote.Connection
import net.milosvasic.factory.remote.ssh.SSH

class ProxyInstallation(private val proxy: Proxy) : RemoteOperationInstallationStep<SSH>() {

    @Throws(IllegalArgumentException::class, IllegalStateException::class)
    override fun getFlow(): CommandFlow {

        connection?.let { conn ->

            log.i(proxy.print())
            val validator = ProxyValidator()
            if (validator.validate()) {

                val cmd = ProxyInstallationCommand(proxy)
                return CommandFlow()
                    .width(conn)
                    .perform(cmd)
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