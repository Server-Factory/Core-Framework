package net.milosvasic.factory.proxy

import com.google.gson.annotations.SerializedName
import net.milosvasic.factory.EMPTY
import net.milosvasic.factory.remote.Remote

class Proxy(

    host: String = "",
    port: Int = 0,
    hostIp: String? = "",
    account: String? = "",
    val password: String? = "",

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

    @Throws(IllegalStateException::class)
    fun getProxyHostname(): String {

        host?.let {

            if (it == String.EMPTY) {

                throw IllegalStateException("No host name available (1)")
            }
            return it
        }
        throw IllegalStateException("No host name available (2)")
    }

    fun print() = "Proxy(hostname=$host, hostIp=$hostIp, port=$port, account=$account, password=$password)"
}