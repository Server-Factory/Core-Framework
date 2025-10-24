package net.milosvasic.factory.connection.impl

import net.milosvasic.factory.connection.*
import net.milosvasic.logger.Log

/**
 * SSH connection with SSH agent authentication.
 *
 * @author Mail Server Factory Team
 * @since 3.1.0
 */
class SSHAgentConnectionImpl(config: ConnectionConfig) : BaseConnection(config) {

    override fun doConnect(): ConnectionResult {
        Log.w("SSHAgentConnectionImpl.doConnect() is not fully implemented yet")
        return ConnectionResult.Success("SSH Agent connection established (placeholder)")
    }

    override fun doExecute(command: String, timeout: Int): ExecutionResult {
        Log.w("SSHAgentConnectionImpl.doExecute() is not fully implemented yet")
        return ExecutionResult.failure("Not implemented")
    }

    override fun doUploadFile(localPath: String, remotePath: String): TransferResult {
        Log.w("SSHAgentConnectionImpl.doUploadFile() is not fully implemented yet")
        return TransferResult.failure("Not implemented")
    }

    override fun doDownloadFile(remotePath: String, localPath: String): TransferResult {
        Log.w("SSHAgentConnectionImpl.doDownloadFile() is not fully implemented yet")
        return TransferResult.failure("Not implemented")
    }

    override fun doDisconnect() {
        Log.w("SSHAgentConnectionImpl.doDisconnect() is not fully implemented yet")
    }
}
