package net.milosvasic.factory.platform

import net.milosvasic.factory.EMPTY
import net.milosvasic.factory.common.validation.Validation
import net.milosvasic.factory.validation.Validator

class HostNameValidator : Validation<String> {

    @Throws(IllegalArgumentException::class)
    override fun validate(vararg what: String): Boolean {

        Validator.Arguments.validateSingle(*what)
        val hostname = what[0]
        if (hostname == String.EMPTY) {
            throw IllegalArgumentException("Empty hostname")
        }
        return true
    }
}