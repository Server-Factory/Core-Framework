package net.milosvasic.factory.common.filesystem

import net.milosvasic.factory.EMPTY
import net.milosvasic.factory.common.validation.Validation
import net.milosvasic.factory.validation.Validator
import net.milosvasic.factory.validation.parameters.SingleParameterExpectedException
import java.nio.file.InvalidPathException
import java.nio.file.Paths

class FilePathValidator : Validation<FilePathBuilder> {

    private val forbidden = listOf(" ", "\n", "\t")
    private val forbiddenAsWholePath = listOf(".", "..")

    @Throws(InvalidPathException::class, SingleParameterExpectedException::class)
    override fun validate(vararg what: FilePathBuilder): Boolean {

        Validator.Arguments.validateSingle(what)
        val builder = what[0]
        if (!builder.hasContexts()) {

            throw InvalidPathException(String.EMPTY, "No path contexts provided")
        }
        builder.getElements().forEach {

            if (it == String.EMPTY) {

                return false
            }
            forbidden.forEach { forbid ->
                if (it.contains(forbid)) {
                    return false
                }
            }
            Paths.get(it)
        }

        forbiddenAsWholePath.forEach { forbid ->
            if (builder.getPath() == forbid) {

                return false
            }
        }
        return true
    }
}