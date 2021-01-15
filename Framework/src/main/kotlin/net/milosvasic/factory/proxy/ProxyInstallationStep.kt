package net.milosvasic.factory.proxy

import net.milosvasic.factory.component.installer.step.RemoteOperationInstallationStep
import net.milosvasic.factory.execution.flow.implementation.CommandFlow
import net.milosvasic.factory.operation.Operation
import net.milosvasic.factory.remote.ssh.SSH

class ProxyInstallationStep(proxy: Proxy) : RemoteOperationInstallationStep<SSH>() {

    override fun getFlow(): CommandFlow {

        TODO("Not yet implemented")
    }

    override fun getOperation(): Operation {

        TODO("Not yet implemented")
    }
}