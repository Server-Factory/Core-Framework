package net.milosvasic.factory.application.server_factory.common

import net.milosvasic.factory.application.server_factory.ServerFactory
import net.milosvasic.factory.application.server_factory.ServerFactoryBuilder
import net.milosvasic.factory.deployment.TargetValidator
import net.milosvasic.factory.log

class CommonServerFactory(builder: ServerFactoryBuilder) : ServerFactory(builder) {

    override fun getConfigurationFactory() = CommonServerFactoryServerConfigurationFactory()

    @Throws(IllegalStateException::class, IllegalArgumentException::class)
    override fun run() {

        configuration?.let {
            it.deployment?.let { targets ->

                val validator = TargetValidator()
                targets.forEach { target ->

                    log.d("Deployment target: ${target.name}")
                    if (validator.validate(target)) {

                        log.v("Processing the target: ${target.name}")
                        // TODO:
                    } else {

                        throw IllegalArgumentException("Invalid target: ${target.name}")
                    }
                }
            }
        }
        super.run()
    }
}