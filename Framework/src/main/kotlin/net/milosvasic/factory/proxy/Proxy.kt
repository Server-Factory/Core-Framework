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
    private val certificateEndpoint: String? = "",

    private val refreshFrequency: Int? = DEFAULT_REFRESH_FREQUENCY
) : Remote(

    host,
    hostIp,
    port,
    account
) {

    companion object {

        const val DEFAULT_REFRESH_FREQUENCY = 900
    }

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

    fun getRefreshFrequency(): Int {

        refreshFrequency?.let {

            return it
        }
        return DEFAULT_REFRESH_FREQUENCY
    }

    override fun getHostname(): String? {

        host?.let {

            return it
        }
        return null
    }

    fun print() = "Proxy(host=$host, hostIp=$hostIp, port=$port, account=$account, password=$password)"
}