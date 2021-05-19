package net.milosvasic.factory.application.server_factory.common

import net.milosvasic.factory.application.server_factory.ServerFactory
import net.milosvasic.factory.application.server_factory.ServerFactoryBuilder

class CommonServerFactory(builder: ServerFactoryBuilder) : ServerFactory(builder) {

    override fun getConfigurationFactory() = CommonServerFactoryServerConfigurationFactory()

    override fun run() {

        super.run()
    }
}