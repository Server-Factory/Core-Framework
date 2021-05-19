package net.milosvasic.factory.deployment

import net.milosvasic.factory.common.Validation

class TargetValidator : Validation<Target> {

    @Throws(IllegalArgumentException::class)
    override fun validate(vararg what: Target): Boolean {

        what.forEach {

            if (it.name.isEmpty() || it.name.isBlank()) {

                throw IllegalArgumentException("Invalid name for: $it")
            }
            if (it.source.isEmpty() || it.source.isBlank()) {

                // TODO: Source validators go here to support various source types
                throw IllegalArgumentException("Invalid source for: $it")
            }
        }
        return true
    }
}