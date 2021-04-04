package net.milosvasic.factory.application.server_factory

import net.milosvasic.factory.configuration.recipe.ConfigurationRecipe
import net.milosvasic.factory.filesystem.Directories
import net.milosvasic.logger.Logger

class ServerFactoryBuilder {

    private var logger: Logger? = null
    private var featureDatabase = true
    private var recipe: ConfigurationRecipe<*>? = null
    private var installationLocation = Directories.DEFAULT_INSTALLATION_LOCATION

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

    fun setLogger(logger: Logger): ServerFactoryBuilder {

        this.logger = logger
        return this
    }

    fun getFeatureDatabase() = featureDatabase

    fun setFeatureDatabase(featureDatabase: Boolean): ServerFactoryBuilder {

        this.featureDatabase = featureDatabase
        return this
    }

    fun getInstallationLocation() = installationLocation

    fun setInstallationLocation(location: String): ServerFactoryBuilder {

        installationLocation = location
        return this
    }
}