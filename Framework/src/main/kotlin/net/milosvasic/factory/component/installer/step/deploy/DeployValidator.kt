package net.milosvasic.factory.component.installer.step.deploy

import net.milosvasic.factory.common.validation.Validation
import net.milosvasic.factory.configuration.definition.Definition
import net.milosvasic.factory.validation.Validator

class DeployValidator : Validation<String> {

    @Throws(IllegalArgumentException::class)
    override fun validate(vararg what: String): Boolean {

        Validator.Arguments.validateSingle(what)
        val arg = what[0]
        val split = arg.split(Deploy.SEPARATOR_FROM_TO)
        val fromToError = IllegalArgumentException("No valid parameters available: $arg")
        if (split.isEmpty()) {

            throw fromToError
        }
        if (arg.contains(Deploy.SEPARATOR_DEFINITION)) {

            if (arg.startsWith("${Definition.CURRENT_DEFINITION}${Deploy.SEPARATOR_DEFINITION}")) {

                if (split.size != 2) {

                    throw fromToError
                }
            } else {

                if (split.size != 3) {

                    throw fromToError
                }
            }
        } else if (split.size != 2) {

            throw fromToError
        }
        return true
    }
}