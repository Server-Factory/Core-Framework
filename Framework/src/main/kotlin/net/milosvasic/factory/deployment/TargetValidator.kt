package net.milosvasic.factory.deployment

import net.milosvasic.factory.common.validation.ValidationAsync
import net.milosvasic.factory.common.validation.ValidationCallback
import net.milosvasic.factory.deployment.source.TargetSourceValidator

class TargetValidator : ValidationAsync<Target> {

    @Throws(IllegalArgumentException::class, IllegalStateException::class)
    override fun validate(what: Target, callback: ValidationCallback) {

        if (what.name.isEmpty() || what.name.isBlank()) {

            throw IllegalArgumentException("Invalid name for: $what")
        }

        val source = what.getSource()
        val validator = TargetSourceValidator()

        val targetSourceValidationCallback = object : ValidationCallback {

            override fun onValidated(valid: Boolean) {

                callback.onValidated(valid)
            }
        }

        validator.validate(source, targetSourceValidationCallback)
    }
}