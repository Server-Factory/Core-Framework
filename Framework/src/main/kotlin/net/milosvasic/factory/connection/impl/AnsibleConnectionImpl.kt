package net.milosvasic.factory.connection.impl

import net.milosvasic.factory.connection.*
import net.milosvasic.logger.Log

/**
 * Ansible connection for remote execution.
 *
 * @author Mail Server Factory Team
 * @since 3.1.0
 */
class AnsibleConnectionImpl(config: ConnectionConfig) : BaseConnection(config) {

    override fun doConnect(): ConnectionResult {
        Log.w("AnsibleConnectionImpl.doConnect() is not fully implemented yet")
        return ConnectionResult.Success("Ansible connection established (placeholder)")
    }

    override fun doExecute(command: String, timeout: Int): ExecutionResult {
        Log.w("AnsibleConnectionImpl.doExecute() is not fully implemented yet")
        return ExecutionResult.failure("Not implemented")
    }

    override fun doUploadFile(localPath: String, remotePath: String): TransferResult {
        Log.w("AnsibleConnectionImpl.doUploadFile() is not fully implemented yet")
        return TransferResult.failure("Not implemented")
    }

    override fun doDownloadFile(remotePath: String, localPath: String): TransferResult {
        Log.w("AnsibleConnectionImpl.doDownloadFile() is not fully implemented yet")
        return TransferResult.failure("Not implemented")
    }

    override fun doDisconnect() {
        Log.w("AnsibleConnectionImpl.doDisconnect() is not fully implemented yet")
    }
}
