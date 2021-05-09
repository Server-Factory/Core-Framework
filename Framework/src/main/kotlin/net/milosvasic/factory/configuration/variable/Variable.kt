package net.milosvasic.factory.configuration.variable

import net.milosvasic.factory.EMPTY
import net.milosvasic.factory.configuration.ConfigurationManager
import java.util.regex.Pattern

object Variable {

    private const val OPEN = "{{"
    private const val CLOSE = "}}"

    const val EMPTY_VARIABLE = "__empty__"

    private fun getRegex() = "${Pattern.quote(OPEN)}(.*?)${Pattern.quote(CLOSE)}"

    private fun getPattern() = Pattern.compile(getRegex())

    @Throws(IllegalStateException::class)
    fun get(path: Path): String {

        return parse("$OPEN${path.getPath()}$CLOSE")
    }

    @Throws(IllegalStateException::class)
    fun parse(value: Any): String {

        when (value) {
            is String -> {
                val pattern = getPattern()
                var result: String = value
                val matcher = pattern.matcher(result)
                while (matcher.find()) {
                    val match = matcher.group(1)
                    if (match.isNotEmpty()) {

                        @Throws(IllegalStateException::class)
                        fun noVariable(match: String) {
                            throw IllegalStateException("No variable defined in the configuration for: '$match'")
                        }

                        val variables = ConfigurationManager.getConfiguration().variables
                        if (variables == null) {
                            noVariable(match)
                        } else {
                            val rawVariable = variables.get(match) ?: noVariable(match)
                            val variable = parse(rawVariable)
                            if (variable == String.EMPTY) {
                                noVariable(match)
                            }
                            result = if (variable == EMPTY_VARIABLE) {

                                result.replace("$OPEN$match$CLOSE", "")
                            } else {
                                result.replace("$OPEN$match$CLOSE", variable)
                            }
                        }
                    }
                }
                return result
            }
            else -> {

                return "$value"
            }
        }
    }
}