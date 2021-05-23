package net.milosvasic.factory.application.server_factory.common

import net.milosvasic.factory.application.server_factory.ServerFactory
import net.milosvasic.factory.application.server_factory.ServerFactoryBuilder
import net.milosvasic.factory.common.validation.ValidationCallback
import net.milosvasic.factory.deployment.TargetValidator
import net.milosvasic.factory.deployment.execution.DefaultTargetExecutor
import net.milosvasic.factory.log
import java.util.concurrent.CountDownLatch
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicBoolean

class CommonServerFactory(builder: ServerFactoryBuilder) : ServerFactory(builder) {

    override fun getConfigurationFactory() = CommonServerFactoryServerConfigurationFactory()

    @Throws(IllegalStateException::class, IllegalArgumentException::class, RejectedExecutionException::class)
    override fun run() {

        configuration?.let {
            it.deployment?.let { targets ->

                val executor = DefaultTargetExecutor(executor)

                targets.forEach { target ->

                    log.d("Deployment target: ${target.name}")

                    val isValid = AtomicBoolean()
                    val countdownLatch = CountDownLatch(1)

                    this.executor.execute {

                        val validationCallback = object : ValidationCallback {

                            override fun onValidated(valid: Boolean) {

                                isValid.set(valid)
                                countdownLatch.countDown()
                            }
                        }

                        val validator = TargetValidator()
                        try {

                            validator.validate(target, validationCallback)
                        } catch (e: IllegalArgumentException) {

                            log.e(e)
                            countdownLatch.countDown()
                        } catch (e: IllegalStateException) {

                            log.e(e)
                            countdownLatch.countDown()
                        }
                    }

                    countdownLatch.await()
                    if (isValid.get()) {

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