package net.milosvasic.factory.proxy

import net.milosvasic.factory.EMPTY
import net.milosvasic.factory.remote.Remote

class Proxy(

    host: String,
    port: Int = 3128,
    hostIp: String? = "",
    account: String? = "",
    val password: String? = "",
    private val selfSignedCA: Boolean? = false
) : Remote(

    host,
    hostIp,
    port,
    account
) {

    fun getProxyAccount(): String {

        account?.let {

            return it
        }
        return String.EMPTY
    }

    fun getProxyPassword(): String {

        password?.let {

            return password
        }
        return String.EMPTY
    }

    fun isSelfSignedCA(): Boolean {

        selfSignedCA?.let {

            return selfSignedCA
        }
        return false
    }

    override fun getHost(preferIpAddress: Boolean): String {

        host?.let {
            return it
        }
        throw IllegalStateException("Host information unavailable")
    }

    fun print() = "Proxy(host=$host, port=$port, account=$account, password=$password)"
}