package net.milosvasic.factory.proxy

import net.milosvasic.factory.remote.Remote

abstract class Proxy(

    host: String?,
    hostIp: String?,
    port: Int,
    account: String
) : Remote(

    host,
    hostIp,
    port,
    account
)