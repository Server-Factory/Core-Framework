package net.milosvasic.factory.connection.impl

import net.milosvasic.factory.connection.*
import net.milosvasic.logger.Log

/**
 * Azure Serial Console connection.
 *
 * @author Mail Server Factory Team
 * @since 3.1.0
 */
class AzureSerialConnectionImpl(config: ConnectionConfig) : BaseConnection(config) {

    override fun doConnect(): ConnectionResult {
        Log.w("AzureSerialConnectionImpl.doConnect() is not fully implemented yet")
        return ConnectionResult.Success("Azure Serial connection established (placeholder)")
    }

    override fun doExecute(command: String, timeout: Int): ExecutionResult {
        Log.w("AzureSerialConnectionImpl.doExecute() is not fully implemented yet")
        return ExecutionResult.failure("Not implemented")
    }

    override fun doUploadFile(localPath: String, remotePath: String): TransferResult {
        Log.w("AzureSerialConnectionImpl.doUploadFile() is not fully implemented yet")
        return TransferResult.failure("Not implemented")
    }

    override fun doDownloadFile(remotePath: String, localPath: String): TransferResult {
        Log.w("AzureSerialConnectionImpl.doDownloadFile() is not fully implemented yet")
        return TransferResult.failure("Not implemented")
    }

    override fun doDisconnect() {
        Log.w("AzureSerialConnectionImpl.doDisconnect() is not fully implemented yet")
    }
}
