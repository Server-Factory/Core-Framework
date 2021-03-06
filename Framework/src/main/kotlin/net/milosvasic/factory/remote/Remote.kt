package net.milosvasic.factory.remote

import com.google.gson.annotations.SerializedName
import net.milosvasic.factory.EMPTY
import net.milosvasic.factory.LOCALHOST
import net.milosvasic.factory.behavior.Behavior
import net.milosvasic.factory.configuration.variable.Context
import net.milosvasic.factory.configuration.variable.Key
import net.milosvasic.factory.configuration.variable.PathBuilder
import net.milosvasic.factory.configuration.variable.Variable
import net.milosvasic.factory.validation.networking.IPV4Validator

open class Remote(

    protected var host: String?,
    protected var hostIp: String?,
    val port: Int,
    @SerializedName("user") protected val account: String?
) {

    fun getAccountName(): String {

        account?.let {

            return it
        }
        return String.EMPTY
    }

    @Throws(IllegalStateException::class)
    fun getHost(preferIpAddress: Boolean = true): String {

        getHostname()?.let {

            try {

                val validator = IPV4Validator()
                if (validator.validate(it)) {

                    return it
                }
            } catch (e: IllegalArgumentException) {

                // Ignore.
            }
        }

        val behavior = Behavior()
        val behaviorGetIp = behavior.behaviorGetIp()
        if (behaviorGetIp && preferIpAddress && hostIp == null) {

            throw IllegalStateException("No host ip address available")
        }
        hostIp?.let {
            if (behaviorGetIp && preferIpAddress && (it.isEmpty() || it.isBlank())) {

                throw EmptyHostAddressException()
            }
            if (it.isNotEmpty() && it.isNotBlank()) {

                return it
            }
        }
        getHostname()?.let {

            return it
        }
        return LOCALHOST
    }

    @Throws(IllegalArgumentException::class)
    fun setHostIpAddress(hostIp: String) {

        if (hostIp.isEmpty() || hostIp.isBlank()) {

            throw IllegalArgumentException("Empty host parameter")
        }
        this.hostIp = hostIp
    }

    protected open fun getHostname(): String? {

        host?.let { return it }

        val path = PathBuilder()
            .addContext(Context.Server)
            .setKey(Key.Hostname)
            .build()

        val hostname = Variable.get(path)
        if (hostname != String.EMPTY) {

            return hostname
        }
        return null
    }
}