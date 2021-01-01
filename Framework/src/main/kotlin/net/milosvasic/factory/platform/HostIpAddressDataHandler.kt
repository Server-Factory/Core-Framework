package net.milosvasic.factory.platform

import net.milosvasic.factory.common.DataHandler
import net.milosvasic.factory.error.ERROR
import net.milosvasic.factory.fail
import net.milosvasic.factory.log
import net.milosvasic.factory.operation.OperationResult
import net.milosvasic.factory.remote.Remote
import net.milosvasic.factory.validation.networking.IPV4Validator

class HostIpAddressDataHandler(private val remote: Remote) : DataHandler<OperationResult> {

    private val validator = IPV4Validator()

    @Throws(IllegalArgumentException::class)
    override fun onData(data: OperationResult?) {

        data?.let {
            val ip = it.data
            try {
                if (!validator.validate(ip)) {

                    val exception = IllegalArgumentException("Invalid IP address: $ip")
                    die(exception)
                }
            } catch (e: IllegalArgumentException) {

                die(e)
            }
            remote.setHostIp(ip)
        }
    }

    private fun die(e: Exception? = null) {

        e?.let {
            log.e(it)
        }
        fail(ERROR.RUNTIME_ERROR)
    }
}