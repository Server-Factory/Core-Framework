package net.milosvasic.factory.component.docker.proxy

import net.milosvasic.factory.EMPTY
import net.milosvasic.factory.common.obtain.ObtainParametrized
import net.milosvasic.factory.proxy.Proxy
import net.milosvasic.factory.proxy.ProxyValidator
import net.milosvasic.factory.validation.Validator
import java.lang.StringBuilder

class ProxyEnvironmentFactory : ObtainParametrized<Proxy, String> {

    @Throws(IllegalArgumentException::class, IllegalStateException::class)
    override fun obtain(vararg param: Proxy): String {

        Validator.Arguments.validateSingle(param)
        val proxy = param[0]
        val validator = ProxyValidator()
        if (validator.validate(proxy)) {

            var credentials = String.EMPTY
            if (proxy.getProxyAccount() != String.EMPTY && proxy.password != String.EMPTY) {

                credentials = "${proxy.getProxyAccount()}:${proxy.getProxyPassword()}@"
            }

            val url = "http://$credentials${proxy.getHost()}:${proxy.port}"

            return StringBuilder()
                .append("HTTP_PROXY=$url\n")
                .append("HTTPS_PROXY=$url\n")
                .append("FTP_PROXY=$url")
                .toString()
        }
        throw IllegalArgumentException("Invalid Proxy")
    }
}