package net.milosvasic.factory.connection.impl

import net.milosvasic.factory.connection.*
import net.milosvasic.logger.Log

/**
 * Google Cloud Platform OS Login connection.
 *
 * @author Mail Server Factory Team
 * @since 3.1.0
 */
class GCPOSLoginConnectionImpl(config: ConnectionConfig) : BaseConnection(config) {

    override fun doConnect(): ConnectionResult {
        Log.w("GCPOSLoginConnectionImpl.doConnect() is not fully implemented yet")
        return ConnectionResult.Success("GCP OS Login connection established (placeholder)")
    }

    override fun doExecute(command: String, timeout: Int): ExecutionResult {
        Log.w("GCPOSLoginConnectionImpl.doExecute() is not fully implemented yet")
        return ExecutionResult.failure("Not implemented")
    }

    override fun doUploadFile(localPath: String, remotePath: String): TransferResult {
        Log.w("GCPOSLoginConnectionImpl.doUploadFile() is not fully implemented yet")
        return TransferResult.failure("Not implemented")
    }

    override fun doDownloadFile(remotePath: String, localPath: String): TransferResult {
        Log.w("GCPOSLoginConnectionImpl.doDownloadFile() is not fully implemented yet")
        return TransferResult.failure("Not implemented")
    }

    override fun doDisconnect() {
        Log.w("GCPOSLoginConnectionImpl.doDisconnect() is not fully implemented yet")
    }
}
