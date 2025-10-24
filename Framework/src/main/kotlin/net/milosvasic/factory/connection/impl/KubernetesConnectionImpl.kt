package net.milosvasic.factory.connection.impl

import net.milosvasic.factory.connection.*
import net.milosvasic.logger.Log

/**
 * Kubernetes pod connection.
 *
 * @author Mail Server Factory Team
 * @since 3.1.0
 */
class KubernetesConnectionImpl(config: ConnectionConfig) : BaseConnection(config) {

    override fun doConnect(): ConnectionResult {
        Log.w("KubernetesConnectionImpl.doConnect() is not fully implemented yet")
        return ConnectionResult.Success("Kubernetes connection established (placeholder)")
    }

    override fun doExecute(command: String, timeout: Int): ExecutionResult {
        Log.w("KubernetesConnectionImpl.doExecute() is not fully implemented yet")
        return ExecutionResult.failure("Not implemented")
    }

    override fun doUploadFile(localPath: String, remotePath: String): TransferResult {
        Log.w("KubernetesConnectionImpl.doUploadFile() is not fully implemented yet")
        return TransferResult.failure("Not implemented")
    }

    override fun doDownloadFile(remotePath: String, localPath: String): TransferResult {
        Log.w("KubernetesConnectionImpl.doDownloadFile() is not fully implemented yet")
        return TransferResult.failure("Not implemented")
    }

    override fun doDisconnect() {
        Log.w("KubernetesConnectionImpl.doDisconnect() is not fully implemented yet")
    }
}
