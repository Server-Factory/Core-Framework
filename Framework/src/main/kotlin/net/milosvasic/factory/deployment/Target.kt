package net.milosvasic.factory.deployment

import net.milosvasic.factory.deployment.source.RawTargetSource
import net.milosvasic.factory.deployment.source.TargetSource
import net.milosvasic.factory.deployment.source.TargetSourceBuilder

data class Target(

    val name: String,
    private val type: String,
    private val source: RawTargetSource
) {

    companion object {

        const val DIRECTORY_HOME = "Targets"
    }

    fun getType(): TargetType {

        return TargetType.getByValue(type)
    }

    @Throws(IllegalArgumentException::class)
    fun getSource(): TargetSource {

        val builder = TargetSourceBuilder(source)
        return builder.build()
    }
}