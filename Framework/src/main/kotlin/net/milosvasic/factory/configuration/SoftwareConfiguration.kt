package net.milosvasic.factory.configuration

import com.google.gson.GsonBuilder
import com.google.gson.JsonParseException
import net.milosvasic.factory.EMPTY
import net.milosvasic.factory.common.filesystem.FilePathBuilder
import net.milosvasic.factory.common.obtain.ObtainParametrized
import net.milosvasic.factory.component.installer.step.InstallationStep
import net.milosvasic.factory.component.installer.step.factory.InstallationStepFactories
import net.milosvasic.factory.configuration.definition.Definition
import net.milosvasic.factory.configuration.variable.Node
import net.milosvasic.factory.log
import net.milosvasic.factory.platform.Platform
import net.milosvasic.factory.validation.Validator
import java.io.File

data class SoftwareConfiguration(

        var definition: Definition? = null,
        var uses: MutableList<String> = mutableListOf(),
        var overrides: MutableMap<String, MutableMap<String, SoftwareConfiguration>>? = mutableMapOf(),
        var configuration: String = String.EMPTY,
        var variables: Node? = null,
        var software: MutableList<SoftwareConfigurationItem>? = mutableListOf(),
        var includes: MutableSet<String>? = mutableSetOf(),
        var enabled: Boolean? = true

) : ObtainParametrized<String, Map<String, List<InstallationStep<*>>>> {

    private var platform: String? = null

    companion object : ObtainParametrized<String, SoftwareConfiguration> {

        @Throws(IllegalArgumentException::class, JsonParseException::class)
        override fun obtain(vararg param: String): SoftwareConfiguration {

            val validator = SoftwareConfigurationObtainParametersValidator()
            if (!validator.validate(*param)) {

                throw IllegalArgumentException("Expected two arguments")
            }
            val platform = param[1]
            val configurationName = param[0]
            val configurationFile = File(configurationName)
            log.d("Configuration file: ${configurationFile.absolutePath}")
            if (configurationFile.exists()) {

                val json = configurationFile.readText()
                val gsonBuilder = GsonBuilder()
                val variablesDeserializer = Node.getDeserializer()
                gsonBuilder.registerTypeAdapter(Node::class.java, variablesDeserializer)
                val gson = gsonBuilder.create()

                val instance = gson.fromJson(json, SoftwareConfiguration::class.java)
                instance.configuration = configurationName
                instance.platform = platform
                val included = mutableListOf<SoftwareConfiguration>()
                instance.includes?.forEach { include ->

                    var path = include
                    if (!include.startsWith(File.separator)) {

                        path = FilePathBuilder()
                                .addContext(configurationFile.parent)
                                .addContext(include)
                                .getPath()
                    }
                    included.add(obtain(path, platform))
                }
                included.forEach { config ->

                    config.setPlatform(platform)
                    instance.merge(config)
                }
                // Variable handling: Software configurations use fixed file paths and do not require
                // dynamic variable substitution like the main configuration. If variable support is
                // needed in the future, implement similar to Configuration.kt variable processing.
                return instance
            } else {

                val msg = "Software configuration file does not exist: ${configurationFile.absolutePath}"
                throw IllegalArgumentException(msg)
            }
        }
    }

    @Synchronized
    @Throws(IllegalArgumentException::class, IllegalStateException::class)
    override fun obtain(vararg param: String): Map<String, List<InstallationStep<*>>> {

        Validator.Arguments.validateSingle(param)
        val platformName = param[0]
        val factories = InstallationStepFactories
        val installationSteps = mutableMapOf<String, List<InstallationStep<*>>>()
        software?.forEach {
            val steps = it.getInstallationSteps(platformName)
            if (steps.platform != Platform.UNKNOWN) {

                val items = mutableListOf<InstallationStep<*>>()
                steps.items.forEach { definition ->

                    this.definition?.let { def ->
                        definition.setDefinition(def)
                    }
                    val step = factories.obtain(definition)
                    items.add(step)
                }
                installationSteps[it.name] = items
            }
        }
        if (installationSteps.isEmpty()) {

            throw IllegalArgumentException("No installation steps for '$platformName' platform")
        }
        return installationSteps
    }

    @Throws(IllegalArgumentException::class)
    fun merge(configuration: SoftwareConfiguration) {

        if (platform == null) {

            throw IllegalArgumentException("No operating system information provided for remote host")
        }
        if (platform == Platform.UNKNOWN.platformName) {

            throw IllegalArgumentException("Operating system information provided for remote host is unknown")
        }

        merge(configuration.variables)

        configuration.software?.let {

            if (software == null) {
                software = mutableListOf()
            }
            software?.addAll(it)
        }

        configuration.includes?.let {

            if (includes == null) {
                includes = mutableSetOf()
            }
            includes?.addAll(it)
        }

        configuration.overrides?.let {
            platform?.let { platformName ->
                it[SoftwareConfigurationOverride.PLATFORM.type]?.let { osOverrides ->

                    val platform = Platform.getByValue(platformName)
                    var cfg = osOverrides[platform.platformName]
                    cfg?.let {

                        merge(it)
                        return
                    }
                    platform.getFallback().forEach { fallback ->

                        cfg = osOverrides[fallback.platformName]
                        cfg?.let {

                            merge(it)
                            return
                        }
                    }
                }
            }
        }
    }

    fun isEnabled(): Boolean {

        enabled?.let {
            return it
        }
        return true
    }

    fun getPlatform() = platform

    fun setPlatform(platform: String) {

        this.platform = platform
    }

    private fun merge(toMerge: Node?) {

        toMerge?.let { toAppend ->
            if (variables == null) {
                variables = toAppend
            } else {
                variables?.append(toAppend)
            }
        }
    }
}