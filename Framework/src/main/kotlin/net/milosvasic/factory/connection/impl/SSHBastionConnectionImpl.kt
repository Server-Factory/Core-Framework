package net.milosvasic.factory.connection.impl

import net.milosvasic.factory.connection.*
import net.milosvasic.logger.Log

/**
 * SSH connection through bastion host (jump server).
 *
 * @author Mail Server Factory Team
 * @since 3.1.0
 */
class SSHBastionConnectionImpl(config: ConnectionConfig) : BaseConnection(config) {

    override fun doConnect(): ConnectionResult {
        Log.w("SSHBastionConnectionImpl.doConnect() is not fully implemented yet")
        return ConnectionResult.Success("SSH Bastion connection established (placeholder)")
    }

    override fun doExecute(command: String, timeout: Int): ExecutionResult {
        Log.w("SSHBastionConnectionImpl.doExecute() is not fully implemented yet")
        return ExecutionResult.failure("Not implemented")
    }

    override fun doUploadFile(localPath: String, remotePath: String): TransferResult {
        Log.w("SSHBastionConnectionImpl.doUploadFile() is not fully implemented yet")
        return TransferResult.failure("Not implemented")
    }

    override fun doDownloadFile(remotePath: String, localPath: String): TransferResult {
        Log.w("SSHBastionConnectionImpl.doDownloadFile() is not fully implemented yet")
        return TransferResult.failure("Not implemented")
    }

    override fun doDisconnect() {
        Log.w("SSHBastionConnectionImpl.doDisconnect() is not fully implemented yet")
    }
}
