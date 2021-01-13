package net.milosvasic.factory.validation.networking

import net.milosvasic.factory.common.Validation
import net.milosvasic.factory.log
import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException

class IPV4Validator : Validation<String> {

    @Throws(IllegalArgumentException::class)
    override fun validate(vararg what: String): Boolean {

        if (what.isEmpty()) {

            throw IllegalArgumentException("No IP addresses provided")
        }
        what.forEach {
            try {
                if (!performValidation(it)) {

                    return false
                }
            } catch (e: PatternSyntaxException) {

                log.e(e)
                throw IllegalArgumentException(e.message)
            }
        }
        return true
    }

    @Throws(PatternSyntaxException::class)
    private fun performValidation(what: String): Boolean {

       /*
        * Explanation:
        *   (
        *     [0-9]         # 0-9
        *     |             # or
        *     [1-9][0-9]    # 10-99
        *     |             # or
        *     1[0-9][0-9]   # 100-199
        *     |             # or
        *     2[0-4][0-9]   # 200-249
        *     |             # or
        *     25[0-5]       # 250-255
        *   )
        *   (\.(?!$)|$))    # ensure IPv4 doesn't end with a dot
        *   {4}             # 4 times.
        *
        */
        val ipv4Pattern =
            "^(([0-9]|[1-9][0-9]|1[0-9][0-9]|2[0-4][0-9]|25[0-5])(\\.(?!\$)|\$)){4}\$"

        val pattern = Pattern.compile(ipv4Pattern)
        val matcher = pattern.matcher(what)
        return matcher.matches()
    }
}