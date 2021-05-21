package net.milosvasic.factory.deployment.source

import net.milosvasic.factory.common.Validation
import net.milosvasic.factory.validation.Validator

class TargetSourceValidator : Validation<TargetSource> {

    @Throws(IllegalArgumentException::class)
    override fun validate(vararg what: TargetSource): Boolean {

        Validator.Arguments.validateNotEmpty(*what)
        what.forEach {

            when(it) {

                is GitTargetSource -> {

                    val repo = it.value
                    // TODO: WSF-4
                    return false
                }
            }
        }
        return true
    }
}