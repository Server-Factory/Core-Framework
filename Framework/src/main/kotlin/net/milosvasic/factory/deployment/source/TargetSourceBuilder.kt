package net.milosvasic.factory.deployment.source

import net.milosvasic.factory.common.Build

class TargetSourceBuilder(private val from: RawTargetSource) : Build<TargetSource> {

    @Throws(IllegalArgumentException::class)
    override fun build(): TargetSource {

        when (from.type) {

            TargetSourceType.GIT.type -> {

                return GitTargetSource(from.value)
            }
        }
        throw IllegalArgumentException("Unsupported target type: ${from.type}")
    }
}