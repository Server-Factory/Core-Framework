package net.milosvasic.factory.component.docker.proxy

import net.milosvasic.factory.EMPTY
import net.milosvasic.factory.common.obtain.ObtainParametrized
import net.milosvasic.factory.proxy.Proxy
import net.milosvasic.factory.proxy.ProxyValidator
import net.milosvasic.factory.validation.Validator

class ProxyEnvironmentFactory : ObtainParametrized<Proxy, String> {

    @Throws(IllegalArgumentException::class)
    override fun obtain(vararg param: Proxy): String {

        Validator.Arguments.validateSingle(param)
        val proxy = param[0]
        val validator = ProxyValidator()
        if (validator.validate(proxy)) {

            // TODO:
            return String.EMPTY
        }
        throw IllegalArgumentException("Invalid Proxy")
    }
}