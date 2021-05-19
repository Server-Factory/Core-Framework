package net.milosvasic.factory.test.implementation

import net.milosvasic.factory.EMPTY
import net.milosvasic.factory.configuration.Configuration
import net.milosvasic.factory.configuration.SoftwareConfiguration
import net.milosvasic.factory.configuration.definition.Definition
import net.milosvasic.factory.configuration.variable.Node
import net.milosvasic.factory.deployment.Target
import net.milosvasic.factory.remote.Remote
import java.util.concurrent.LinkedBlockingQueue

class StubConfiguration(

        definition: Definition? = null,
        name: String = String.EMPTY,
        remote: Remote,
        uses: LinkedBlockingQueue<String>?,
        includes: LinkedBlockingQueue<String>?,
        software: LinkedBlockingQueue<String>,
        containers: LinkedBlockingQueue<String>?,
        variables: Node? = null,
        overrides: MutableMap<String, MutableMap<String, SoftwareConfiguration>>?,
        deployment: MutableList<Target>?

) : Configuration(

        definition, name, remote, uses, includes, software, containers, variables, overrides, null, deployment
)