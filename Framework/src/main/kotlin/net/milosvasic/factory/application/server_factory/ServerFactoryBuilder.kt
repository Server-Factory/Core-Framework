package net.milosvasic.factory.application.server_factory

import net.milosvasic.factory.EMPTY
import net.milosvasic.factory.common.filesystem.FilePathBuilder
import net.milosvasic.factory.configuration.recipe.ConfigurationRecipe
import net.milosvasic.logger.Logger

class ServerFactoryBuilder {

    private var logger: Logger? = null
    private var featureDatabase = true
    private var installationHome: String? = null
    private var recipe: ConfigurationRecipe<*>? = null

    @Throws(SecurityException::class)
    fun getInstallationLocation(): String {

        var installationHomeLocation = String.EMPTY
        installationHome?.let { home ->

            installationHomeLocation = home
        }
        if (installationHomeLocation.isEmpty() || installationHomeLocation.isBlank()) {

            installationHomeLocation = System.getProperty("user.home")
        }

        return FilePathBuilder()
            .addContext(installationHomeLocation)
            .getPath()
    }

    @Throws(IllegalArgumentException::class)
    fun getRecipe(): ConfigurationRecipe<*> {

        recipe?.let {

            return it
        }
        throw IllegalArgumentException("Configuration recipe is not provided")
    }

    fun setRecipe(recipe: ConfigurationRecipe<*>): ServerFactoryBuilder {

        this.recipe = recipe
        return this
    }

    fun getLogger() = logger

    fun getFeatureDatabase() = featureDatabase

    fun setFeatureDatabase(database: Boolean): ServerFactoryBuilder {

        featureDatabase = database
        return this
    }

    fun setInstallationHome(home: String): ServerFactoryBuilder {

        installationHome = home
        return this
    }
}