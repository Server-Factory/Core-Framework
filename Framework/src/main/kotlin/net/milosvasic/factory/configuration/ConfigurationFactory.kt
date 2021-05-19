package net.milosvasic.factory.configuration

import com.google.gson.GsonBuilder
import com.google.gson.JsonParseException
import com.google.gson.JsonSyntaxException
import net.milosvasic.factory.common.filesystem.FilePathBuilder
import net.milosvasic.factory.common.obtain.ObtainParametrized
import net.milosvasic.factory.configuration.recipe.ConfigurationRecipe
import net.milosvasic.factory.configuration.recipe.FileConfigurationRecipe
import net.milosvasic.factory.configuration.recipe.RawJsonConfigurationRecipe
import net.milosvasic.factory.configuration.variable.Node
import net.milosvasic.factory.log
import net.milosvasic.factory.validation.Validator
import java.io.File
import java.lang.reflect.Type
import java.util.concurrent.LinkedBlockingQueue

abstract class ConfigurationFactory<T : Configuration> : ObtainParametrized<ConfigurationRecipe<*>, T> {

    abstract fun getType(): Type

    abstract fun onInstantiated(configuration: T)

    abstract fun validateConfiguration(configuration: T): Boolean

    @Throws(IllegalArgumentException::class)
    override fun obtain(vararg param: ConfigurationRecipe<*>): T {

        Validator.Arguments.validateSingle(param)
        return when (val recipe = param[0]) {
            is FileConfigurationRecipe -> {

                obtain(recipe.data)
            }
            is RawJsonConfigurationRecipe -> {

                obtain(recipe.data)
            }
            else -> {

                throw IllegalArgumentException("Unsupported configuration recipe: $recipe")
            }
        }
    }

    @Throws(IllegalArgumentException::class)
    private fun obtain(configurationJson: String): T {

        val variablesDeserializer = Node.getDeserializer()
        val gsonBuilder = GsonBuilder()
        gsonBuilder.registerTypeAdapter(Node::class.java, variablesDeserializer)
        val gson = gsonBuilder.create()
        try {

            val configuration: T = gson.fromJson(configurationJson, getType())
            postInstantiate(configuration)

            if (validateConfiguration(configuration)) {
                return configuration
            }
        } catch (e: JsonParseException) {

            log.e(e)
            throw IllegalArgumentException("Unable to parse JSON: ${e.message}")
        } catch (e: JsonSyntaxException) {

            log.e(e)
            throw IllegalArgumentException("Unable to parse JSON: ${e.message}")
        }
        throw IllegalArgumentException("Could not obtain configuration from raw string")
    }

    @Throws(IllegalArgumentException::class)
    private fun obtain(configurationFile: File): T {

        if (configurationFile.exists()) {

            val configurationJson = configurationFile.readText()
            val variablesDeserializer = Node.getDeserializer()
            val gsonBuilder = GsonBuilder()
            gsonBuilder.registerTypeAdapter(Node::class.java, variablesDeserializer)
            val gson = gsonBuilder.create()
            try {

                val configuration: T = gson.fromJson(configurationJson, getType())
                postInstantiate(configuration)
                configuration.enabled?.let { enabled ->
                    if (enabled) {
                        log.d("Configuration file: ${configurationFile.absolutePath}")
                    } else {
                        log.w("Configuration file is disabled: ${configurationFile.absolutePath}")
                    }
                }

                val iterator = configuration.includes?.iterator()
                iterator?.let {
                    while (it.hasNext()) {
                        val include = it.next()
                        var includeFile = File(include)
                        if (!include.startsWith(File.separator)) {

                            val path = FilePathBuilder()
                                    .addContext(configurationFile.parent)
                                    .addContext(include)
                                    .getPath()

                            includeFile = File(path)
                        }

                        val includedConfiguration = obtain(includeFile)
                        configuration.merge(includedConfiguration)
                    }
                }

                if (validateConfiguration(configuration)) {
                    return configuration
                }
            } catch (e: JsonParseException) {

                log.e(e)
                throw IllegalArgumentException("Unable to parse JSON: ${e.message}")
            } catch (e: JsonSyntaxException) {

                log.e(e)
                throw IllegalArgumentException("Unable to parse JSON: ${e.message}")
            }
        } else {

            throw IllegalArgumentException("File does not exist: ${configurationFile.absoluteFile}")
        }
        throw IllegalArgumentException("Could not obtain configuration from file")
    }

    private fun postInstantiate(configuration: T) {

        if (configuration.enabled == null) {
            configuration.enabled = true
        }
        if (configuration.uses == null) {
            configuration.uses = LinkedBlockingQueue()
        }
        if (configuration.includes == null) {
            configuration.includes = LinkedBlockingQueue()
        }
        if (configuration.software == null) {
            configuration.software = LinkedBlockingQueue()
        }
        if (configuration.stacks == null) {
            configuration.stacks = LinkedBlockingQueue()
        }
        if (configuration.variables == null) {
            configuration.variables = Node()
        }
        if (configuration.overrides == null) {
            configuration.overrides = mutableMapOf()
        }
        if (configuration.deployment == null) {
            configuration.deployment = mutableListOf()
        }
        onInstantiated(configuration)
    }
}