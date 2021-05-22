package net.milosvasic.factory.deployment.source

import net.milosvasic.factory.common.validation.ValidationAsync
import net.milosvasic.factory.common.validation.ValidationCallback
import net.milosvasic.factory.execution.flow.callback.FlowCallback
import net.milosvasic.factory.execution.flow.implementation.CommandFlow
import net.milosvasic.factory.log
import net.milosvasic.factory.terminal.Terminal
import net.milosvasic.factory.terminal.command.ApplicationInfoCommand
import net.milosvasic.factory.terminal.command.GitVerifyRepository

class TargetSourceValidator : ValidationAsync<TargetSource> {

    @Throws(IllegalArgumentException::class, IllegalStateException::class)
    override fun validate(what: TargetSource, callback: ValidationCallback) {

        when (what) {
            is GitTargetSource -> {

                val flowCallback = object : FlowCallback {

                    override fun onFinish(success: Boolean) {

                        if (!success) {

                            log.e("Invalid target source: ${what.value}")
                        }
                        callback.onValidated(success)
                    }
                }

                val flow = CommandFlow()
                val terminal = Terminal()

                flow.width(terminal)
                    .perform(ApplicationInfoCommand("git"))
                    .perform(GitVerifyRepository(what.value))
                    .onFinish(flowCallback)
                    .run()
            }
            else -> {

                throw IllegalArgumentException("Unsupported target source: ${what.type}")
            }
        }
    }
}