package net.milosvasic.factory.remote

import com.google.gson.annotations.SerializedName
import net.milosvasic.factory.EMPTY
import net.milosvasic.factory.LOCALHOST
import net.milosvasic.factory.configuration.variable.Context
import net.milosvasic.factory.configuration.variable.Key
import net.milosvasic.factory.configuration.variable.PathBuilder
import net.milosvasic.factory.configuration.variable.Variable

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

        val behaviorGetIp = behaviorGetIp()
        if (behaviorGetIp && preferIpAddress && hostIp == null) {

            throw IllegalStateException("No host ip address available")
        }
        hostIp?.let {
            if (behaviorGetIp && preferIpAddress && (it.isEmpty() || it.isBlank())) {

                throw IllegalStateException("Host ip address is empty")
            }
            return it
        }
        getHostname()?.let { return it }
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

    @Throws(IllegalArgumentException::class, IllegalStateException::class)
    private fun behaviorGetIp(): Boolean {

        val behaviorPath = PathBuilder()
            .addContext(Context.Behavior)
            .setKey(Key.GetIp)
            .build()

        var behaviorGetIp = false
        try {

            behaviorGetIp = Variable.get(behaviorPath).toBoolean()
        } catch (e: IllegalStateException) {

            // Ignore
        }
        return behaviorGetIp
    }
}