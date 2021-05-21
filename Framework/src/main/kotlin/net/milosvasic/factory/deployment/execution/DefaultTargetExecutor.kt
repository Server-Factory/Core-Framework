package net.milosvasic.factory.deployment.execution

import net.milosvasic.factory.deployment.Target
import net.milosvasic.factory.log

class DefaultTargetExecutor : TargetExecutor() {

    @Throws(IllegalArgumentException::class)
    override fun execute(what: Target) {

        log.v("Processing the target: ${what.name}")
        val source = what.getSource()
        log.v("${what.name} target source: $source")

    }
}