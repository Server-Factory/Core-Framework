package net.milosvasic.factory.configuration.definition

import net.milosvasic.factory.common.validation.Validation
import net.milosvasic.factory.validation.Validator

class DefinitionValidator : Validation<String> {

    @Throws(IllegalArgumentException::class)
    override fun validate(vararg what: String): Boolean {

        Validator.Arguments.validateSingle(what)
        val param = what[0]
        val invalidDefinitionError = IllegalArgumentException("Invalid definition: $param")
        if (param != Definition.CURRENT_DEFINITION) {

            if (!param.contains(Definition.VERSION_SEPARATOR)) {

                throw invalidDefinitionError
            }

            val separators = param.chars().filter { value ->

                value.toChar().toString() == Definition.PATH_SEPARATOR
            }.count().toInt()

            if (separators != 2) {

                throw invalidDefinitionError
            }
        }
        return true
    }
}