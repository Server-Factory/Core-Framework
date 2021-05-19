package net.milosvasic.factory.configuration

import net.milosvasic.factory.EMPTY
import net.milosvasic.factory.common.filesystem.FilePathBuilder
import net.milosvasic.factory.configuration.definition.Definition
import net.milosvasic.factory.configuration.variable.Node
import net.milosvasic.factory.deployment.Target
import net.milosvasic.factory.merge
import net.milosvasic.factory.proxy.Proxy
import net.milosvasic.factory.remote.Remote
import java.io.File
import java.nio.file.InvalidPathException
import java.util.concurrent.LinkedBlockingQueue

abstract class Configuration(

    definition: Definition? = null,
    val name: String? = String.EMPTY,
    val remote: Remote,
    uses: LinkedBlockingQueue<String>?,
    includes: LinkedBlockingQueue<String>?,
    software: LinkedBlockingQueue<String>?,
    containers: LinkedBlockingQueue<String>?,
    variables: Node? = null,
    overrides: MutableMap<String, MutableMap<String, SoftwareConfiguration>>?,
    enabled: Boolean? = null,
    deployment: MutableList<Target>?

) : ConfigurationInclude(

    definition,
    uses,
    includes,
    software,
    containers,
    variables,
    overrides,
    enabled,
    deployment
) {

    private var proxy: Proxy? = null

    companion object {

        const val DEFAULT_CONFIGURATION_FILE = "Definition.json"

        @Throws(InvalidPathException::class)
        fun getConfigurationFilePath(path: String): String {

            var fullPath = path
            if (!path.endsWith(".json")) {

                val param = FilePathBuilder()
                    .addContext(File.separator)
                    .addContext(DEFAULT_CONFIGURATION_FILE)
                    .build()

                fullPath += param
            }
            return fullPath
        }
    }

    @Throws(IllegalArgumentException::class)
    open fun merge(configuration: Configuration) {

        configuration.enabled?.let { enabled ->
            if (enabled) {

                configuration.includes?.let {
                    includes?.addAll(it)
                }
                configuration.uses?.let {
                    uses?.addAll(it)
                }
                configuration.variables?.let {
                    variables?.append(it)
                }
                configuration.software?.let {
                    software?.addAll(it)
                }
                configuration.stacks?.let {
                    stacks?.addAll(it)
                }
                configuration.overrides?.let {
                    overrides?.let { ods ->
                        it.merge(ods)
                    }
                }
                configuration.deployment?.let {
                    deployment?.addAll(it)
                }
                configuration.proxy?.let {
                    proxy?.let { p ->

                        throw IllegalArgumentException("Proxy conflict: ${p.print()} vs ${it.print()}")
                    }
                    proxy = it
                }
            }
        }
    }

    fun mergeVariables(variables: Node?) {
        variables?.let { toAppend ->
            if (this.variables == null) {
                this.variables = toAppend
            } else {
                toAppend.children.forEach { child ->
                    this.variables?.append(child)
                }
            }
        }
    }

    fun getProxy(): Proxy {

        proxy?.let {
            return it
        }
        return Proxy()
    }

    override fun toString(): String {

        return "Configuration(\nname='$name', \nremote=$remote\n)\n${super.toString()}"
    }
}