package net.milosvasic.factory.proxy

import net.milosvasic.factory.remote.Remote

class Proxy(

    host: String?,
    hostIp: String?,
    port: Int = 3128,
    account: String = "",
    val password: String? = ""

) : Remote(

    host,
    hostIp,
    port,
    account
) {

    fun print() = "Proxy(host=$host, hostIp=$hostIp, port=$port, account=$account, password=$password)"
}