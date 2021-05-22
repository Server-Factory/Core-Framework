package net.milosvasic.factory.configuration.variable

import net.milosvasic.factory.common.validation.Validation
import net.milosvasic.factory.log
import net.milosvasic.factory.validation.Validator

class PathBuilderValidator : Validation<PathBuilder> {

    @Throws(IllegalArgumentException::class)
    override fun validate(vararg what: PathBuilder): Boolean {

        Validator.Arguments.validateSingle(what)
        val builder = what[0]
        if (!builder.hasContexts()) {

            log.w("No contexts provided for the builder: $builder")
            return false
        }
        if (!builder.hasKey()) {

            log.w("No key provided for the builder: $builder")
            return false
        }
        return true
    }
}