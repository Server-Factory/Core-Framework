package net.milosvasic.factory.proxy

import com.google.gson.annotations.SerializedName
import net.milosvasic.factory.EMPTY
import net.milosvasic.factory.remote.Remote

class Proxy(

    host: String,
    port: Int = 3128,
    hostIp: String? = "",
    account: String? = "",
    val password: String? = "",

    @SerializedName("self_signed_ca")
    private val selfSignedCA: Boolean? = false,

    @SerializedName("certificate_endpoint")
    private val certificateEndpoint: String? = ""
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

    fun getCertificateEndpoint(): String {

        certificateEndpoint?.let {

            return certificateEndpoint
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