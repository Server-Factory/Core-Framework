package net.milosvasic.factory.deployment.execution

import net.milosvasic.factory.common.filesystem.FilePathBuilder
import net.milosvasic.factory.configuration.variable.Context
import net.milosvasic.factory.configuration.variable.Key
import net.milosvasic.factory.configuration.variable.PathBuilder
import net.milosvasic.factory.configuration.variable.Variable
import net.milosvasic.factory.deployment.Target
import net.milosvasic.factory.execution.TaskExecutor
import net.milosvasic.factory.log
import net.milosvasic.factory.terminal.Terminal
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.RejectedExecutionException

class DefaultTargetExecutor(private val executor: Executor = TaskExecutor.instantiate(5)) : TargetExecutor() {

    @Throws(IllegalArgumentException::class, RejectedExecutionException::class, IllegalStateException::class)
    override fun execute(what: Target) {

        log.i("Processing the target: ${what.name}")
        val source = what.getSource()
        log.i("${what.name} target source: ${source.value}")

        val terminal = Terminal()

        val keyHome = Key.Home
        val ctxSystem = Context.System
        val ctxInstallation = Context.Installation

        val pathSystemInstallationHome = PathBuilder()
            .addContext(ctxSystem)
            .addContext(ctxInstallation)
            .setKey(keyHome)
            .build()

        val systemInstallationHome = Variable.get(pathSystemInstallationHome)

        val targetHomePath = FilePathBuilder()
            .addContext(systemInstallationHome)
            .addContext(Target.DIRECTORY_HOME)
            .build()

        log.d("Targets home: $targetHomePath")

        val countdownLatch = CountDownLatch(1)

        executor.execute {

            countdownLatch.countDown()
        }

        countdownLatch.await()
    }
}