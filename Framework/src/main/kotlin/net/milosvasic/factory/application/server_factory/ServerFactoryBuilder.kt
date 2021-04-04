package net.milosvasic.factory.application.server_factory

import net.milosvasic.factory.configuration.recipe.ConfigurationRecipe
import net.milosvasic.factory.fail
import net.milosvasic.logger.Logger
import java.lang.NullPointerException

class ServerFactoryBuilder {

    private var logger: Logger? = null
    private var featureDatabase = true
    private var installationLocation = ""
    private var recipe: ConfigurationRecipe<*>? = null

    init {

        try {

            installationLocation = System.getProperty("user.home")
        } catch (e: NullPointerException) {

            fail(e)
        } catch (e: IllegalArgumentException) {

            fail(e)
        } catch (e: SecurityException) {

            fail(e)
        }
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

    fun getInstallationLocation(): String = installationLocation

    fun setInstallationLocation(location: String): ServerFactoryBuilder {

        installationLocation = location
        return this
    }
}