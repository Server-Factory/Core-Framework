package net.milosvasic.factory.application.server_factory.common

import net.milosvasic.factory.application.server_factory.ServerFactory
import net.milosvasic.factory.application.server_factory.ServerFactoryBuilder
import net.milosvasic.factory.deployment.TargetValidator
import net.milosvasic.factory.deployment.execution.DefaultTargetExecutor
import net.milosvasic.factory.log

class CommonServerFactory(builder: ServerFactoryBuilder) : ServerFactory(builder) {

    override fun getConfigurationFactory() = CommonServerFactoryServerConfigurationFactory()

    @Throws(IllegalStateException::class, IllegalArgumentException::class)
    override fun run() {

        configuration?.let {
            it.deployment?.let { targets ->

                val validator = TargetValidator()
                val executor = DefaultTargetExecutor()

                targets.forEach { target ->

                    log.d("Deployment target: ${target.name}")
                    if (validator.validate(target)) {

                        executor.execute(target)
                    } else {

                        throw IllegalArgumentException("Invalid target: ${target.name}")
                    }
                }
            }
        }
        super.run()
    }
}