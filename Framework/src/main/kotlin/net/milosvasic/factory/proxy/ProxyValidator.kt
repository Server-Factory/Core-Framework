package net.milosvasic.factory.proxy

import net.milosvasic.factory.common.Validation
import net.milosvasic.factory.validation.Validator
import net.milosvasic.factory.validation.parameters.SingleParameterExpectedException

class ProxyValidator : Validation<Proxy> {

    @Throws(SingleParameterExpectedException::class, IllegalStateException::class)
    override fun validate(vararg what: Proxy): Boolean {

        Validator.Arguments.validateSingle(what)
        val proxy = what[0]
        if (proxy.port <= 0) {

            return false
        }
        val host = proxy.getHost(preferIpAddress = false)
        if (host.isBlank() || host.isEmpty()) {

            return false
        }
        return true
    }
}