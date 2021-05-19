package net.milosvasic.factory.configuration

import net.milosvasic.factory.configuration.definition.Definition
import net.milosvasic.factory.configuration.variable.Node
import net.milosvasic.factory.deployment.Target
import java.util.concurrent.LinkedBlockingQueue

open class ConfigurationInclude(

        var definition: Definition? = null,
        var uses: LinkedBlockingQueue<String>?,
        var includes: LinkedBlockingQueue<String>?,
        var software: LinkedBlockingQueue<String>?,
        var stacks: LinkedBlockingQueue<String>?,
        var variables: Node? = null,
        var overrides: MutableMap<String, MutableMap<String, SoftwareConfiguration>>?,
        var enabled: Boolean? = null,
        var deployment: MutableList<Target>?
) {

    override fun toString(): String {

        return "ConfigurationInclude(definition=$definition, uses=$uses, includes=$includes, " +
                "software=$software, containers=$stacks, variables=$variables, overrides=$overrides, " +
                "enabled=$enabled, deployment=$deployment)"
    }
}