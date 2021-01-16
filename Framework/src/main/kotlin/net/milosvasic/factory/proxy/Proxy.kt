package net.milosvasic.factory.proxy

import net.milosvasic.factory.remote.Remote

class Proxy(

    host: String,
    port: Int = 3128,
    hostIp: String? = "",
    account: String = "",
    val password: String? = ""

) : Remote(

    host,
    hostIp,
    port,
    account
) {

    override fun getHost(preferIpAddress: Boolean): String {

        host?.let {
            return it
        }
        throw IllegalStateException("Host information unavailable")
    }

    fun print() = "Proxy(host=$host, port=$port, account=$account, password=$password)"
}