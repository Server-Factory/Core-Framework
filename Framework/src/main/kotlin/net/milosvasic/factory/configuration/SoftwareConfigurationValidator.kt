package net.milosvasic.factory.configuration

import net.milosvasic.factory.common.validation.Validation
import net.milosvasic.factory.log
import net.milosvasic.factory.platform.Platform
import net.milosvasic.factory.platform.PlatformValidator
import net.milosvasic.factory.validation.Validator
import net.milosvasic.factory.validation.parameters.StringArgumentValidation

class SoftwareConfigurationValidator : Validation<SoftwareConfiguration> {

    @Throws(IllegalArgumentException::class)
    override fun validate(vararg what: SoftwareConfiguration): Boolean {

        Validator.Arguments.validateNotEmpty(*what)
        val stringValidator = StringArgumentValidation()
        val platformValidator = PlatformValidator()
        what.forEach {

            if (!stringValidator.validate(it.configuration)) {

                return false
            }
            val platformValue = it.getPlatform()
            if (platformValue == null) {

                log.e("No platform information specified")
                return false
            }
            val platform = Platform.getByValue(platformValue)
            if (!platformValidator.validate(platform)) {

                return false
            }
        }
        return true
    }
}