package net.milosvasic.factory.platform

import net.milosvasic.factory.common.validation.Validation
import net.milosvasic.factory.validation.parameters.ArgumentsExpectedException

class PlatformValidator(private val notAllowed: Set<Platform> = setOf(Platform.UNKNOWN)) : Validation<Platform> {

    @Throws(ArgumentsExpectedException::class)
    override fun validate(vararg what: Platform): Boolean {

        if (what.isEmpty()) {

            throw ArgumentsExpectedException()
        }
        what.forEach {
            if (notAllowed.contains(it)) {

                return false
            }
        }
        return true
    }
}