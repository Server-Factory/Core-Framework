package net.milosvasic.factory.component.docker.step.network

import net.milosvasic.factory.common.validation.Validation
import net.milosvasic.factory.validation.Validator

class NetworkValidator(val separator: String) : Validation<String> {

    override fun validate(vararg what: String): Boolean {

        Validator.Arguments.validateSingle(what)
        val value = what[0]
        if (!value.contains(separator)) {

            throw IllegalArgumentException("No subnet parameter provided for the network: $value")
        }
        val arguments = value.split(separator)
        if (arguments.size < 2) {

            throw IllegalArgumentException("Invalid network parameters provided")
        }
        return true
    }
}