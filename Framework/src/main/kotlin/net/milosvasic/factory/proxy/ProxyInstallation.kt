package net.milosvasic.factory.proxy

import net.milosvasic.factory.component.installer.step.RemoteOperationInstallationStep
import net.milosvasic.factory.component.installer.step.deploy.Deploy
import net.milosvasic.factory.execution.flow.implementation.CommandFlow
import net.milosvasic.factory.operation.Operation
import net.milosvasic.factory.remote.Connection
import net.milosvasic.factory.remote.ssh.SSH

class ProxyInstallation(private val proxy: Proxy) : RemoteOperationInstallationStep<SSH>() {

    /*
    @Throws(IllegalArgumentException::class, IllegalStateException::class)
    private fun initializeProxy(proxy: Proxy) {

        log.i(proxy.print())
        val validator = ProxyValidator()
        if (validator.validate()) {


        } else {

            throw IllegalArgumentException("Invalid proxy: ${proxy.print()}")
        }
    }
     */

    override fun getFlow(): CommandFlow {

        TODO("Not yet implemented")
    }

    override fun getOperation(): Operation {

        TODO("Not yet implemented")
    }

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