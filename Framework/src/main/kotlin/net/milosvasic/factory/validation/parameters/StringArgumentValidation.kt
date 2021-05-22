package net.milosvasic.factory.validation.parameters

import net.milosvasic.factory.common.validation.Validation

class StringArgumentValidation : Validation<String> {

    @Throws(ArgumentsExpectedException::class)
    override fun validate(vararg what: String): Boolean {

        if (what.isEmpty()) {

            throw ArgumentsExpectedException()
        }
        what.forEach {
            if (it.isEmpty() || it.isBlank()) {

                return false
            }
        }
        return true
    }
}