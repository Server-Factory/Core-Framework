package net.milosvasic.factory.application.server_factory.common

import net.milosvasic.factory.application.server_factory.ServerFactory
import net.milosvasic.factory.application.server_factory.ServerFactoryBuilder
import net.milosvasic.factory.log

class CommonServerFactory(builder: ServerFactoryBuilder) : ServerFactory(builder) {

    override fun getConfigurationFactory() = CommonServerFactoryServerConfigurationFactory()

    override fun run() {

        configuration?.let {
            it.deployment?.let { targets ->

                targets.forEach { target ->

                    log.d("Deployment target: ${target.name}")
                }
            }
        }
        super.run()
    }
}