package net.milosvasic.factory.deployment.execution

import net.milosvasic.factory.deployment.Target
import net.milosvasic.factory.deployment.source.TargetSourceType
import net.milosvasic.factory.execution.TaskExecutor
import net.milosvasic.factory.execution.flow.callback.FlowCallback
import net.milosvasic.factory.execution.flow.implementation.CommandFlow
import net.milosvasic.factory.fail
import net.milosvasic.factory.log
import net.milosvasic.factory.terminal.Terminal
import net.milosvasic.factory.terminal.command.TargetInstallGitCommand
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.RejectedExecutionException

class DefaultTargetExecutor(private val executor: Executor = TaskExecutor.instantiate(5)) : TargetExecutor() {

    @Throws(IllegalArgumentException::class, RejectedExecutionException::class, IllegalStateException::class)
    override fun execute(what: Target) {

        when (what.getSource().type) {

            TargetSourceType.GIT.type -> {

                log.i("Installing the target: ${what.name}")
                val source = what.getSource()
                log.i("${what.name} target source: ${source.value}")

                val countdownLatch = CountDownLatch(1)

                executor.execute {

                    val terminal = Terminal()

                    val callback = object : FlowCallback {

                        override fun onFinish(success: Boolean) {

                            if (!success) {

                                val e = IllegalStateException("${what.name} target installation failed")
                                fail(e)
                            }
                            countdownLatch.countDown()
                        }
                    }

                    try {

                        val command = TargetInstallGitCommand(what)

                        CommandFlow()
                            .width(terminal)
                            .perform(command)
                            .onFinish(callback)
                            .run()

                    } catch (e: IllegalArgumentException) {

                        fail(e)
                    } catch (e: IllegalStateException) {

                        fail(e)
                    }
                }

                countdownLatch.await()
            }
            else -> {

                throw IllegalArgumentException("Unsupported target source: ${what.getSource().type}")
            }
        }
    }
}