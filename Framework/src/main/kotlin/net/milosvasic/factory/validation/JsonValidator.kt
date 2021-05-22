package net.milosvasic.factory.validation

import com.google.gson.Gson
import com.google.gson.JsonElement
import net.milosvasic.factory.common.validation.Validation
import java.io.IOException

class JsonValidator : Validation<String> {

    @Throws(IllegalArgumentException::class)
    override fun validate(vararg what: String): Boolean {

        if (what.isEmpty()) {

            throw IllegalArgumentException("No data to validate")
        }
        what.forEach {
            try {

                Gson().getAdapter(JsonElement::class.java).fromJson(it)
            } catch (e: IllegalArgumentException) {

                invalid(it)
            } catch (e: IOException) {

                invalid(it)
            }
        }
        return true
    }

    private fun invalid(what: String): Nothing = throw IllegalArgumentException("Invalid JSON: $what")
}