package net.milosvasic.factory.application.server_factory.common

import com.google.gson.reflect.TypeToken
import net.milosvasic.factory.configuration.ConfigurationFactory
import net.milosvasic.factory.log
import java.lang.reflect.Type

class CommonServerConfigurationFactory : ConfigurationFactory<CommonServerFactoryConfiguration>() {

    override fun getType(): Type {

        return object : TypeToken<CommonServerFactoryConfiguration>() {}.type
    }

    override fun validateConfiguration(configuration: CommonServerFactoryConfiguration) = true

    override fun onInstantiated(configuration: CommonServerFactoryConfiguration) {

        val name = configuration.name
        name?.let {
            if (it.isNotEmpty() && it.isNotBlank()) {

                log.v("Configuration: $name")
            }
        }
    }
}