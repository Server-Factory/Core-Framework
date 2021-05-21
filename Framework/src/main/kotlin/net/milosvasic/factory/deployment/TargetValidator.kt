package net.milosvasic.factory.deployment

import net.milosvasic.factory.common.Validation
import net.milosvasic.factory.deployment.source.TargetSourceValidator
import net.milosvasic.factory.validation.Validator

class TargetValidator : Validation<Target> {

    @Throws(IllegalArgumentException::class)
    override fun validate(vararg what: Target): Boolean {

        Validator.Arguments.validateNotEmpty(*what)
        what.forEach {

            if (it.name.isEmpty() || it.name.isBlank()) {

                throw IllegalArgumentException("Invalid name for: $it")
            }
            val validator = TargetSourceValidator()
            val source = it.getSource()
            if (!validator.validate(source)) {

                throw IllegalArgumentException("Invalid target source type: ${source.type}")
            }

        }
        return true
    }
}