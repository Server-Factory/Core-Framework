package net.milosvasic.factory.remote

import com.google.gson.annotations.SerializedName
import net.milosvasic.factory.EMPTY
import net.milosvasic.factory.LOCALHOST
import net.milosvasic.factory.configuration.variable.Context
import net.milosvasic.factory.configuration.variable.Key
import net.milosvasic.factory.configuration.variable.PathBuilder
import net.milosvasic.factory.configuration.variable.Variable

class Remote(

        private var host: String?,
        private var hostIp: String?,
        val port: Int,
        @SerializedName("user") val account: String
) {

    @Throws(IllegalStateException::class)
    fun getHost(): String {

        hostIp?.let { return it }
        getHostname()?.let { return it }
        return LOCALHOST
    }

    @Throws(IllegalArgumentException::class)
    fun setHostIp(hostIp: String) {

        if (hostIp.isEmpty() || hostIp.isBlank()) {

            throw IllegalArgumentException("Empty host parameter")
        }
        this.hostIp = hostIp
    }

    private fun getHostname(): String? {

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