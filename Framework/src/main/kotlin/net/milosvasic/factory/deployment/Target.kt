package net.milosvasic.factory.deployment

import net.milosvasic.factory.application.server_factory.common.CommonServerConfigurationFactory
import net.milosvasic.factory.common.filesystem.FilePathBuilder
import net.milosvasic.factory.configuration.Configuration
import net.milosvasic.factory.configuration.recipe.FileConfigurationRecipe
import net.milosvasic.factory.configuration.variable.Context
import net.milosvasic.factory.configuration.variable.Key
import net.milosvasic.factory.configuration.variable.PathBuilder
import net.milosvasic.factory.configuration.variable.Variable
import net.milosvasic.factory.deployment.source.RawTargetSource
import net.milosvasic.factory.deployment.source.TargetSource
import net.milosvasic.factory.deployment.source.TargetSourceBuilder
import java.io.File

data class Target(

    val name: String,
    private val type: String,
    private val source: RawTargetSource
) {

    companion object {

        const val DIRECTORY_HOME = "Targets"
        const val CONFIGURATION_FILE = "Configuration.json"
    }

    fun getType(): TargetType {

        return TargetType.getByValue(type)
    }

    @Throws(IllegalArgumentException::class)
    fun getSource(): TargetSource {

        val builder = TargetSourceBuilder(source)
        return builder.build()
    }

    @Throws(IllegalArgumentException::class, IllegalStateException::class)
    fun getConfiguration() : Configuration {

        val targetHomePath = getHomePath()

        val configurationPath = FilePathBuilder()
            .addContext(targetHomePath)
            .addContext(CONFIGURATION_FILE)
            .build()

        val configurationFile = File(configurationPath)
        if (!configurationFile.exists()) {

            val msg = "$name target configuration file does not exist: ${configurationFile.absolutePath}"
            throw IllegalArgumentException(msg)
        }

        val recipe = FileConfigurationRecipe(configurationFile)
        val factory = CommonServerConfigurationFactory()
        return factory.obtain(recipe)
    }

    @Throws(IllegalArgumentException::class, IllegalStateException::class)
    fun getHomePath(): String {

        val keyHome = Key.Home
        val ctxSystem = Context.System
        val ctxInstallation = Context.Installation

        val pathSystemInstallationHome = PathBuilder()
            .addContext(ctxSystem)
            .addContext(ctxInstallation)
            .setKey(keyHome)
            .build()

        val systemInstallationHome = Variable.get(pathSystemInstallationHome)

        return FilePathBuilder()
            .addContext(systemInstallationHome)
            .addContext(DIRECTORY_HOME)
            .addContext(name)
            .build()
    }
}