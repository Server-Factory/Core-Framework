package net.milosvasic.factory.application

enum class Argument(private val arg: String) {

    INSTALLATION_HOME("installationHome");

    companion object {

        const val ARGUMENT_PREFIX = "--"
    }

    fun get() = "$ARGUMENT_PREFIX$arg="
}