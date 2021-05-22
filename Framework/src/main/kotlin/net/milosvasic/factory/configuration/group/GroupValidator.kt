package net.milosvasic.factory.configuration.group

import net.milosvasic.factory.common.validation.Validation

class GroupValidator : Validation<Group> {

    override fun validate(vararg what: Group): Boolean {

        what.forEach {

            val group = it.name
            val mainGroup = MainGroup()
            if (group.contains(" ") ||
                    (group != mainGroup.name && !group.contains("."))) {

                return false
            }
        }
        return true
    }
}