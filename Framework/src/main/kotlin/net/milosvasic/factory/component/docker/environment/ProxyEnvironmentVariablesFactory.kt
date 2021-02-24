package net.milosvasic.factory.component.docker.environment

import net.milosvasic.factory.EMPTY
import net.milosvasic.factory.LOCALHOST
import net.milosvasic.factory.LOCALHOST_NAME
import net.milosvasic.factory.common.obtain.ObtainParametrized
import net.milosvasic.factory.proxy.Proxy
import net.milosvasic.factory.proxy.ProxyValidator
import net.milosvasic.factory.validation.Validator
import java.lang.StringBuilder

class ProxyEnvironmentVariablesFactory : ObtainParametrized<Proxy, String> {

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
            return getVariables(url)
        }
        throw IllegalArgumentException("Invalid Proxy")
    }

    fun obtainEmpty() = getVariables("")

    private fun getVariables(url: String) = StringBuilder()
        .append("${EnvironmentVariables.HttpProxy.variableName}=\"$url\"\n")
        .append("${EnvironmentVariables.HttpsProxy.variableName}=\"$url\"\n")
        .append("${EnvironmentVariables.FtpProxy.variableName}=\"$url\"\n")
        .append("${EnvironmentVariables.NoProxy.variableName}=\"$LOCALHOST,$LOCALHOST_NAME\"\n")
        .append("${EnvironmentVariables.HttpProxy.variableName.toUpperCase()}=\"$url\"\n")
        .append("${EnvironmentVariables.HttpsProxy.variableName.toUpperCase()}=\"$url\"\n")
        .append("${EnvironmentVariables.FtpProxy.variableName.toUpperCase()}=\"$url\"\n")
        .append("${EnvironmentVariables.NoProxy.variableName.toUpperCase()}=\"$LOCALHOST,$LOCALHOST_NAME\"")
        .toString()
}