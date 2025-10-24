package net.milosvasic.factory.component.installer.step.reboot

import net.milosvasic.factory.component.installer.step.RemoteOperationInstallationStep
import net.milosvasic.factory.execution.flow.implementation.CommandFlow
import net.milosvasic.factory.log
import net.milosvasic.factory.operation.Operation
import net.milosvasic.factory.remote.ssh.SSH
import net.milosvasic.factory.terminal.TerminalCommand
import net.milosvasic.factory.terminal.command.Commands
import net.milosvasic.factory.terminal.command.GenericCommand

/**
 * Installation step that performs a system reboot.
 *
 * This step handles rebooting remote systems with a simple approach:
 * 1. Sends reboot command
 * 2. Waits for configured timeout
 * 3. Assumes system is back online
 *
 * For advanced reboot verification with health checks, connection monitoring,
 * and platform detection, see the advanced implementation in RebootStepAdvanced.kt.wip
 *
 * Usage in JSON:
 * ```json
 * {
 *   "type": "reboot",
 *   "value": "300"
 * }
 * ```
 *
 * @param value Timeout in seconds to wait after reboot (default: 300)
 *
 * @author Mail Server Factory Team
 * @since 3.1.0
 */
class RebootStep(private val value: String = "300") : RemoteOperationInstallationStep<SSH>() {

    companion object {
        private const val DEFAULT_TIMEOUT = 300 // 5 minutes
        private const val MIN_TIMEOUT = 60 // 1 minute
        private const val MAX_TIMEOUT = 1800 // 30 minutes
    }

    private var timeout: Int = DEFAULT_TIMEOUT

    init {
        // Parse timeout
        timeout = value.toIntOrNull() ?: DEFAULT_TIMEOUT
        timeout = timeout.coerceIn(MIN_TIMEOUT, MAX_TIMEOUT)

        log.i("RebootStep initialized: timeout=${timeout}s")
    }

    override fun getOperation(): Operation {
        // Use the Commands.reboot() function which properly handles delayed reboot
        return GenericCommand(Commands.reboot(2))
    }

    override fun getFlow(): CommandFlow {
        log.i("=".repeat(70))
        log.i("REBOOT STEP - Initiating system reboot")
        log.i("Timeout: ${timeout}s")
        log.i("=".repeat(70))

        // Create flow with reboot command
        val flow = CommandFlow("Reboot")
        flow.width(connection!!)
        flow.perform(getOperation() as TerminalCommand)

        return flow
    }

    override fun finish(success: Boolean) {
        if (success) {
            log.i("=".repeat(70))
            log.i("REBOOT STEP - Reboot command sent")
            log.i("System will reboot. Waiting ${timeout}s before proceeding...")
            log.i("=".repeat(70))

            // Wait for reboot to complete
            try {
                Thread.sleep(timeout * 1000L)
                log.i("Wait period completed")
            } catch (e: InterruptedException) {
                log.w("Reboot wait interrupted: ${e.message}")
                Thread.currentThread().interrupt()
            }
        } else {
            log.e("=".repeat(70))
            log.e("REBOOT STEP - Failed")
            log.e("=".repeat(70))
        }

        super.finish(success)
    }
}
