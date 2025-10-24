package net.milosvasic.factory.security

import net.milosvasic.logger.Log
import net.milosvasic.factory.connection.Connection

/**
 * SELinux security checker with comprehensive warnings and recommendations.
 *
 * Features:
 * - Detects SELinux status (enforcing, permissive, disabled)
 * - Provides detailed warnings about security implications
 * - Offers remediation steps
 * - Tracks SELinux mode changes
 * - Logs all SELinux-related events
 *
 * This addresses P1 Issue #7: SELinux disabled without warning
 *
 * @author Mail Server Factory Team
 * @since 3.1.0
 */
object SELinuxChecker {

    /**
     * Checks SELinux status on remote system.
     *
     * @param connection Connection to remote system
     * @return SELinuxStatus with detailed information
     */
    fun checkStatus(connection: Connection): SELinuxStatus {
        try {
            Log.i("Checking SELinux status...")

            // Check if SELinux is available
            val selinuxAvailable = checkSELinuxAvailable(connection)
            if (!selinuxAvailable) {
                Log.i("SELinux not available on this system")
                return SELinuxStatus(
                    mode = SELinuxMode.NOT_AVAILABLE,
                    enabled = false,
                    configured = false,
                    message = "SELinux is not installed or not available on this system"
                )
            }

            // Get current mode
            val currentMode = getCurrentMode(connection)

            // Get config mode (what it's set to in config file)
            val configMode = getConfigMode(connection)

            // Check if enabled
            val enabled = currentMode != SELinuxMode.DISABLED

            // Generate status
            val status = SELinuxStatus(
                mode = currentMode,
                enabled = enabled,
                configured = true,
                configMode = configMode,
                message = generateStatusMessage(currentMode, configMode)
            )

            // Log status
            Log.i("SELinux status: ${status.mode}, enabled=${status.enabled}, config=${status.configMode}")

            // Issue warnings if needed
            issueWarningsIfNeeded(status)

            // Log to audit
            AuditLogger.log(
                event = AuditEvent.SYSTEM,
                action = AuditAction.READ,
                details = "SELinux status checked: ${status.mode}",
                result = AuditResult.SUCCESS,
                metadata = mapOf(
                    "mode" to status.mode.name,
                    "enabled" to status.enabled.toString(),
                    "configMode" to (status.configMode?.name ?: "UNKNOWN")
                )
            )

            return status

        } catch (e: Exception) {
            Log.e("Failed to check SELinux status: ${e.message}", e)
            return SELinuxStatus(
                mode = SELinuxMode.UNKNOWN,
                enabled = false,
                configured = false,
                message = "Failed to determine SELinux status: ${e.message}"
            )
        }
    }

    /**
     * Sets SELinux mode with comprehensive warnings.
     *
     * @param connection Connection to remote system
     * @param targetMode Target SELinux mode
     * @param force Force mode change without confirmation
     * @return true if successful
     */
    fun setMode(connection: Connection, targetMode: SELinuxMode, force: Boolean = false): Boolean {
        try {
            val currentStatus = checkStatus(connection)

            // Warn if disabling or setting to permissive
            if (targetMode == SELinuxMode.DISABLED || targetMode == SELinuxMode.PERMISSIVE) {
                Log.w("=".repeat(70))
                Log.w("WARNING: Setting SELinux to ${targetMode.name}")
                Log.w("=".repeat(70))

                when (targetMode) {
                    SELinuxMode.DISABLED -> {
                        Log.w("SECURITY IMPACT: Disabling SELinux removes a critical security layer")
                        Log.w("  • Mandatory Access Control (MAC) will be disabled")
                        Log.w("  • System vulnerable to privilege escalation")
                        Log.w("  • No process confinement or isolation")
                        Log.w("  • Compliance requirements may not be met (PCI-DSS, HIPAA, etc.)")
                        Log.w("")
                        Log.w("RECOMMENDED ALTERNATIVE:")
                        Log.w("  1. Use SELinux in enforcing mode")
                        Log.w("  2. Configure appropriate policies for mail services")
                        Log.w("  3. Use audit logs to troubleshoot policy violations")
                        Log.w("")
                        Log.w("If you must disable SELinux:")
                        Log.w("  • Document the business justification")
                        Log.w("  • Implement compensating controls")
                        Log.w("  • Review and approve by security team")
                    }

                    SELinuxMode.PERMISSIVE -> {
                        Log.w("SECURITY IMPACT: SELinux will log violations but not enforce policies")
                        Log.w("  • Access violations will be permitted")
                        Log.w("  • System protection is limited")
                        Log.w("  • Useful for troubleshooting, not for production")
                        Log.w("")
                        Log.w("RECOMMENDED USAGE:")
                        Log.w("  • Use permissive mode temporarily for debugging")
                        Log.w("  • Review audit logs: ausearch -m avc")
                        Log.w("  • Create/adjust policies as needed")
                        Log.w("  • Return to enforcing mode after resolving issues")
                    }

                    else -> {}
                }

                Log.w("=".repeat(70))

                if (!force) {
                    Log.w("Use force=true to proceed with this change")
                    return false
                }
            }

            // Execute mode change
            val success = when (targetMode) {
                SELinuxMode.ENFORCING -> setEnforcing(connection)
                SELinuxMode.PERMISSIVE -> setPermissive(connection)
                SELinuxMode.DISABLED -> setDisabled(connection)
                else -> false
            }

            if (success) {
                Log.i("SELinux mode changed to ${targetMode.name}")

                // Log to audit
                AuditLogger.logPrivilegedOperation(
                    action = AuditAction.MODIFY,
                    details = "SELinux mode changed from ${currentStatus.mode} to $targetMode",
                    user = "system",
                    success = true
                )
            } else {
                Log.e("Failed to change SELinux mode to ${targetMode.name}")

                AuditLogger.logPrivilegedOperation(
                    action = AuditAction.MODIFY,
                    details = "Failed to change SELinux mode to $targetMode",
                    user = "system",
                    success = false
                )
            }

            return success

        } catch (e: Exception) {
            Log.e("Failed to set SELinux mode: ${e.message}", e)
            return false
        }
    }

    /**
     * Checks if SELinux is available on the system.
     */
    private fun checkSELinuxAvailable(connection: Connection): Boolean {
        return try {
            val result = connection.execute("which getenforce")
            result.success
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Gets the current SELinux mode.
     */
    private fun getCurrentMode(connection: Connection): SELinuxMode {
        return try {
            val result = connection.execute("getenforce")
            val output = result.output.trim().uppercase()

            when (output) {
                "ENFORCING" -> SELinuxMode.ENFORCING
                "PERMISSIVE" -> SELinuxMode.PERMISSIVE
                "DISABLED" -> SELinuxMode.DISABLED
                else -> SELinuxMode.UNKNOWN
            }
        } catch (e: Exception) {
            SELinuxMode.UNKNOWN
        }
    }

    /**
     * Gets the configured SELinux mode from config file.
     */
    private fun getConfigMode(connection: Connection): SELinuxMode? {
        return try {
            val result = connection.execute("grep '^SELINUX=' /etc/selinux/config | cut -d= -f2")
            val output = result.output.trim().uppercase()

            when (output) {
                "ENFORCING" -> SELinuxMode.ENFORCING
                "PERMISSIVE" -> SELinuxMode.PERMISSIVE
                "DISABLED" -> SELinuxMode.DISABLED
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Sets SELinux to enforcing mode.
     */
    private fun setEnforcing(connection: Connection): Boolean {
        return try {
            connection.execute("setenforce 1")
            updateConfigFile(connection, SELinuxMode.ENFORCING)
            true
        } catch (e: Exception) {
            Log.e("Failed to set enforcing mode: ${e.message}")
            false
        }
    }

    /**
     * Sets SELinux to permissive mode.
     */
    private fun setPermissive(connection: Connection): Boolean {
        return try {
            connection.execute("setenforce 0")
            updateConfigFile(connection, SELinuxMode.PERMISSIVE)
            true
        } catch (e: Exception) {
            Log.e("Failed to set permissive mode: ${e.message}")
            false
        }
    }

    /**
     * Disables SELinux (requires reboot).
     */
    private fun setDisabled(connection: Connection): Boolean {
        return try {
            updateConfigFile(connection, SELinuxMode.DISABLED)
            Log.w("SELinux will be disabled after reboot")
            true
        } catch (e: Exception) {
            Log.e("Failed to disable SELinux: ${e.message}")
            false
        }
    }

    /**
     * Updates SELinux config file.
     */
    private fun updateConfigFile(connection: Connection, mode: SELinuxMode): Boolean {
        return try {
            val configValue = when (mode) {
                SELinuxMode.ENFORCING -> "enforcing"
                SELinuxMode.PERMISSIVE -> "permissive"
                SELinuxMode.DISABLED -> "disabled"
                else -> return false
            }

            connection.execute("sed -i 's/^SELINUX=.*/SELINUX=$configValue/' /etc/selinux/config")
            true
        } catch (e: Exception) {
            Log.e("Failed to update SELinux config: ${e.message}")
            false
        }
    }

    /**
     * Issues warnings based on SELinux status.
     */
    private fun issueWarningsIfNeeded(status: SELinuxStatus) {
        when (status.mode) {
            SELinuxMode.DISABLED -> {
                Log.w("━".repeat(70))
                Log.w("⚠️  SECURITY WARNING: SELinux is DISABLED")
                Log.w("━".repeat(70))
                Log.w("Your system is running without SELinux protection!")
                Log.w("")
                Log.w("Security Implications:")
                Log.w("  • No Mandatory Access Control (MAC)")
                Log.w("  • Processes can access any resource")
                Log.w("  • Increased risk of privilege escalation")
                Log.w("  • Non-compliant with security standards (PCI-DSS, HIPAA, DISA-STIG)")
                Log.w("")
                Log.w("Recommendation: Enable SELinux in enforcing mode")
                Log.w("  sudo setenforce 1")
                Log.w("  Edit /etc/selinux/config: SELINUX=enforcing")
                Log.w("━".repeat(70))
            }

            SELinuxMode.PERMISSIVE -> {
                Log.w("━".repeat(70))
                Log.w("⚠️  SECURITY WARNING: SELinux is in PERMISSIVE mode")
                Log.w("━".repeat(70))
                Log.w("Policy violations are logged but NOT enforced!")
                Log.w("")
                Log.w("Security Implications:")
                Log.w("  • Access violations are permitted")
                Log.w("  • Limited system protection")
                Log.w("  • Not suitable for production environments")
                Log.w("")
                Log.w("Recommendation: Switch to enforcing mode after debugging")
                Log.w("  1. Review audit logs: ausearch -m avc")
                Log.w("  2. Fix policy violations")
                Log.w("  3. Switch to enforcing: sudo setenforce 1")
                Log.w("━".repeat(70))
            }

            SELinuxMode.NOT_AVAILABLE -> {
                Log.w("━".repeat(70))
                Log.w("ℹ️  INFO: SELinux is not available on this system")
                Log.w("━".repeat(70))
                Log.w("Consider using alternative security frameworks:")
                Log.w("  • AppArmor (Debian/Ubuntu)")
                Log.w("  • grsecurity (custom kernels)")
                Log.w("  • Docker security profiles")
                Log.w("━".repeat(70))
            }

            SELinuxMode.ENFORCING -> {
                Log.i("✓ SELinux is in ENFORCING mode (recommended)")
            }

            SELinuxMode.UNKNOWN -> {
                Log.w("WARNING: Could not determine SELinux status")
            }
        }
    }

    /**
     * Generates status message.
     */
    private fun generateStatusMessage(currentMode: SELinuxMode, configMode: SELinuxMode?): String {
        return buildString {
            append("SELinux is ${currentMode.name}")

            if (configMode != null && configMode != currentMode) {
                append(" (configured as ${configMode.name}, will change after reboot)")
            }
        }
    }

    /**
     * Provides detailed SELinux recommendations for mail server.
     */
    fun getMailServerRecommendations(): List<String> {
        return listOf(
            "SELinux Recommendations for Mail Server:",
            "",
            "1. Keep SELinux in ENFORCING mode",
            "   • Provides mandatory access control",
            "   • Limits damage from compromised services",
            "",
            "2. Use mail-specific SELinux policies:",
            "   • postfix_selinux - Postfix policy module",
            "   • dovecot_selinux - Dovecot policy module",
            "   • Install: yum install postfix-selinux dovecot-selinux",
            "",
            "3. Configure SELinux booleans for mail services:",
            "   • allow_postfix_local_write_mail_spool (if needed)",
            "   • httpd_can_sendmail (if web app sends email)",
            "   • Check: getsebool -a | grep mail",
            "",
            "4. Handle SELinux denials:",
            "   • Monitor: ausearch -m avc -ts recent",
            "   • Generate policy: audit2allow -M mypolicy < /var/log/audit/audit.log",
            "   • Load policy: semodule -i mypolicy.pp",
            "",
            "5. File contexts for mail directories:",
            "   • Set context: semanage fcontext -a -t mail_spool_t '/var/mail(/.*)?'",
            "   • Restore: restorecon -Rv /var/mail",
            "",
            "6. Troubleshooting:",
            "   • If services fail, check: journalctl -xe",
            "   • View denials: sealert -a /var/log/audit/audit.log",
            "   • Temporary permissive for service: semanage permissive -a postfix_t",
            "",
            "7. Best Practices:",
            "   • Never disable SELinux in production",
            "   • Use permissive mode only for debugging",
            "   • Document all policy customizations",
            "   • Test policies before enforcing",
            "",
            "For more information: https://access.redhat.com/documentation/en-us/red_hat_enterprise_linux/9/html/using_selinux"
        )
    }
}

/**
 * SELinux modes.
 */
enum class SELinuxMode {
    ENFORCING,     // SELinux policies are enforced
    PERMISSIVE,    // SELinux policies are not enforced, only logged
    DISABLED,      // SELinux is completely disabled
    NOT_AVAILABLE, // SELinux is not installed/available
    UNKNOWN        // Could not determine status
}

/**
 * SELinux status information.
 */
data class SELinuxStatus(
    val mode: SELinuxMode,
    val enabled: Boolean,
    val configured: Boolean,
    val configMode: SELinuxMode? = null,
    val message: String
) {
    fun isSecure(): Boolean {
        return mode == SELinuxMode.ENFORCING
    }

    fun requiresReboot(): Boolean {
        return configMode != null && configMode != mode
    }

    fun hasWarnings(): Boolean {
        return mode != SELinuxMode.ENFORCING && mode != SELinuxMode.NOT_AVAILABLE
    }
}
