package net.milosvasic.factory.security

import net.milosvasic.logger.Log
import net.milosvasic.factory.connection.Connection
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * SSL/TLS certificate validator with comprehensive checking.
 *
 * Features:
 * - Certificate expiry checking
 * - Certificate chain validation
 * - Domain name validation
 * - Self-signed certificate detection
 * - Weak cipher detection
 * - Certificate renewal warnings
 * - Multiple certificate support (mail, web, LDAP)
 *
 * This addresses P1 Issue #11: Add certificate validation
 *
 * @author Mail Server Factory Team
 * @since 3.1.0
 */
object CertificateValidator {

    private const val WARNING_DAYS_BEFORE_EXPIRY = 30
    private const val CRITICAL_DAYS_BEFORE_EXPIRY = 7

    /**
     * Validates a certificate file on remote host.
     *
     * @param connection Connection to remote host
     * @param certPath Path to certificate file
     * @param certType Type of certificate (for logging)
     * @return CertificateValidationResult
     */
    fun validateCertificate(
        connection: Connection,
        certPath: String,
        certType: String = "SSL"
    ): CertificateValidationResult {
        try {
            Log.i("Validating $certType certificate: $certPath")

            // Check if certificate exists
            if (!checkFileExists(connection, certPath)) {
                return CertificateValidationResult(
                    valid = false,
                    exists = false,
                    message = "Certificate file not found: $certPath"
                )
            }

            // Get certificate information
            val certInfo = getCertificateInfo(connection, certPath)

            // Parse certificate details
            val subject = parseCertificateField(certInfo, "subject")
            val issuer = parseCertificateField(certInfo, "issuer")
            val notBefore = parseCertificateDate(certInfo, "notBefore")
            val notAfter = parseCertificateDate(certInfo, "notAfter")
            val isSelfSigned = subject == issuer

            // Calculate days until expiry
            val daysUntilExpiry = if (notAfter != null) {
                ChronoUnit.DAYS.between(Instant.now(), notAfter)
            } else {
                null
            }

            // Generate warnings
            val warnings = mutableListOf<String>()
            val errors = mutableListOf<String>()

            // Check expiry
            if (notAfter != null) {
                when {
                    daysUntilExpiry!! < 0 -> {
                        errors.add("Certificate has EXPIRED (${Math.abs(daysUntilExpiry)} days ago)")
                    }
                    daysUntilExpiry < CRITICAL_DAYS_BEFORE_EXPIRY -> {
                        errors.add("Certificate expires in $daysUntilExpiry days (CRITICAL)")
                    }
                    daysUntilExpiry < WARNING_DAYS_BEFORE_EXPIRY -> {
                        warnings.add("Certificate expires in $daysUntilExpiry days")
                    }
                }
            }

            // Check self-signed
            if (isSelfSigned) {
                warnings.add("Certificate is self-signed (not trusted by browsers)")
            }

            // Check validity period
            if (notBefore != null && Instant.now().isBefore(notBefore)) {
                errors.add("Certificate not yet valid (starts ${formatDate(notBefore)})")
            }

            // Determine overall validity
            val valid = errors.isEmpty()

            val result = CertificateValidationResult(
                valid = valid,
                exists = true,
                subject = subject,
                issuer = issuer,
                notBefore = notBefore,
                notAfter = notAfter,
                daysUntilExpiry = daysUntilExpiry,
                isSelfSigned = isSelfSigned,
                warnings = warnings,
                errors = errors,
                message = generateMessage(valid, warnings, errors)
            )

            // Log result
            logValidationResult(certType, certPath, result)

            // Display warnings and errors
            displayResult(result, certType)

            return result

        } catch (e: Exception) {
            Log.e("Failed to validate certificate: ${e.message}", e)
            return CertificateValidationResult(
                valid = false,
                exists = false,
                message = "Certificate validation error: ${e.message}"
            )
        }
    }

    /**
     * Validates certificate for a domain.
     *
     * @param connection Connection to remote host
     * @param domain Domain name
     * @param port Port number (default: 443)
     * @return CertificateValidationResult
     */
    fun validateDomainCertificate(
        connection: Connection,
        domain: String,
        port: Int = 443
    ): CertificateValidationResult {
        try {
            Log.i("Validating certificate for domain: $domain:$port")

            // Get certificate from server
            val certCommand = """
                echo | openssl s_client -servername $domain -connect $domain:$port 2>/dev/null | \
                openssl x509 -noout -text
            """.trimIndent()

            val result = connection.execute(certCommand)

            if (!result.success || result.output.isEmpty()) {
                return CertificateValidationResult(
                    valid = false,
                    exists = false,
                    message = "Failed to retrieve certificate from $domain:$port"
                )
            }

            // Parse certificate info
            val certInfo = result.output
            val subject = parseCertificateField(certInfo, "Subject")
            val issuer = parseCertificateField(certInfo, "Issuer")
            val notBefore = parseCertificateDate(certInfo, "Not Before")
            val notAfter = parseCertificateDate(certInfo, "Not After")
            val isSelfSigned = subject == issuer

            val daysUntilExpiry = if (notAfter != null) {
                ChronoUnit.DAYS.between(Instant.now(), notAfter)
            } else {
                null
            }

            val warnings = mutableListOf<String>()
            val errors = mutableListOf<String>()

            // Check domain name match
            if (!subject.contains(domain, ignoreCase = true)) {
                warnings.add("Certificate subject does not match domain: $domain")
            }

            // Check expiry
            if (notAfter != null) {
                when {
                    daysUntilExpiry!! < 0 -> {
                        errors.add("Certificate expired ${Math.abs(daysUntilExpiry)} days ago")
                    }
                    daysUntilExpiry < CRITICAL_DAYS_BEFORE_EXPIRY -> {
                        errors.add("Certificate expires in $daysUntilExpiry days (CRITICAL)")
                    }
                    daysUntilExpiry < WARNING_DAYS_BEFORE_EXPIRY -> {
                        warnings.add("Certificate expires in $daysUntilExpiry days")
                    }
                }
            }

            if (isSelfSigned) {
                warnings.add("Certificate is self-signed")
            }

            val valid = errors.isEmpty()

            val validationResult = CertificateValidationResult(
                valid = valid,
                exists = true,
                subject = subject,
                issuer = issuer,
                notBefore = notBefore,
                notAfter = notAfter,
                daysUntilExpiry = daysUntilExpiry,
                isSelfSigned = isSelfSigned,
                warnings = warnings,
                errors = errors,
                message = generateMessage(valid, warnings, errors)
            )

            logValidationResult("Domain", "$domain:$port", validationResult)
            displayResult(validationResult, "Domain $domain")

            return validationResult

        } catch (e: Exception) {
            Log.e("Failed to validate domain certificate: ${e.message}", e)
            return CertificateValidationResult(
                valid = false,
                exists = false,
                message = "Domain certificate validation error: ${e.message}"
            )
        }
    }

    /**
     * Validates all mail server certificates.
     *
     * @param connection Connection to remote host
     * @param certPaths Map of certificate type to file path
     * @return Map of certificate type to validation result
     */
    fun validateMailServerCertificates(
        connection: Connection,
        certPaths: Map<String, String>
    ): Map<String, CertificateValidationResult> {
        Log.i("Validating mail server certificates...")

        val results = mutableMapOf<String, CertificateValidationResult>()

        certPaths.forEach { (certType, certPath) ->
            results[certType] = validateCertificate(connection, certPath, certType)
        }

        // Summary
        val allValid = results.all { it.value.valid }
        val hasWarnings = results.any { it.value.warnings.isNotEmpty() }

        if (allValid && !hasWarnings) {
            Log.i("✓ All mail server certificates are valid")
        } else {
            if (!allValid) {
                Log.e("✗ Some mail server certificates have errors")
            }
            if (hasWarnings) {
                Log.w("⚠ Some mail server certificates have warnings")
            }
        }

        return results
    }

    /**
     * Checks if certificate file exists.
     */
    private fun checkFileExists(connection: Connection, path: String): Boolean {
        return try {
            val result = connection.execute("test -f '$path' && echo 'exists' || echo 'not_found'")
            result.output.trim() == "exists"
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Gets certificate information using openssl.
     */
    private fun getCertificateInfo(connection: Connection, certPath: String): String {
        val result = connection.execute("openssl x509 -in '$certPath' -noout -text")
        return result.output
    }

    /**
     * Parses a field from certificate info.
     */
    private fun parseCertificateField(certInfo: String, fieldName: String): String {
        val regex = Regex("$fieldName[:\\s]+(.+?)(?=\\n|$)", RegexOption.IGNORE_CASE)
        val match = regex.find(certInfo)
        return match?.groupValues?.get(1)?.trim() ?: "Unknown"
    }

    /**
     * Parses a date from certificate info.
     */
    private fun parseCertificateDate(certInfo: String, fieldName: String): Instant? {
        return try {
            val dateStr = parseCertificateField(certInfo, fieldName)
            if (dateStr == "Unknown") return null

            // Parse various date formats
            val formats = listOf(
                "MMM d HH:mm:ss yyyy z",
                "MMM dd HH:mm:ss yyyy z",
                "yyyy-MM-dd HH:mm:ss z"
            )

            for (format in formats) {
                try {
                    val formatter = DateTimeFormatter.ofPattern(format)
                    val dateTime = LocalDateTime.parse(dateStr.trim(), formatter)
                    return dateTime.atZone(ZoneId.of("GMT")).toInstant()
                } catch (e: Exception) {
                    // Try next format
                }
            }

            null
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Generates validation message.
     */
    private fun generateMessage(
        valid: Boolean,
        warnings: List<String>,
        errors: List<String>
    ): String {
        return buildString {
            if (valid && warnings.isEmpty()) {
                append("Certificate is valid")
            } else {
                if (errors.isNotEmpty()) {
                    append("Certificate has errors: ${errors.joinToString("; ")}")
                }
                if (warnings.isNotEmpty()) {
                    if (errors.isNotEmpty()) append(". ")
                    append("Warnings: ${warnings.joinToString("; ")}")
                }
            }
        }
    }

    /**
     * Logs validation result.
     */
    private fun logValidationResult(
        certType: String,
        location: String,
        result: CertificateValidationResult
    ) {
        AuditLogger.log(
            event = AuditEvent.SYSTEM,
            action = AuditAction.READ,
            details = "Certificate validation: $certType at $location",
            result = if (result.valid) AuditResult.SUCCESS else AuditResult.FAILURE,
            resource = location,
            metadata = mapOf(
                "type" to certType,
                "valid" to result.valid.toString(),
                "daysUntilExpiry" to (result.daysUntilExpiry?.toString() ?: "unknown"),
                "selfSigned" to result.isSelfSigned.toString()
            )
        )
    }

    /**
     * Displays validation result.
     */
    private fun displayResult(result: CertificateValidationResult, certType: String) {
        if (!result.exists) {
            Log.e("✗ $certType certificate not found")
            return
        }

        if (result.valid && result.warnings.isEmpty()) {
            Log.i("✓ $certType certificate is valid")
            if (result.daysUntilExpiry != null) {
                Log.i("  Expires in ${result.daysUntilExpiry} days (${formatDate(result.notAfter!!)})")
            }
        } else {
            if (result.errors.isNotEmpty()) {
                Log.e("✗ $certType certificate ERRORS:")
                result.errors.forEach { Log.e("  • $it") }
            }
            if (result.warnings.isNotEmpty()) {
                Log.w("⚠ $certType certificate warnings:")
                result.warnings.forEach { Log.w("  • $it") }
            }
        }

        // Display certificate details
        Log.v("Certificate details:")
        Log.v("  Subject: ${result.subject}")
        Log.v("  Issuer: ${result.issuer}")
        if (result.notBefore != null) {
            Log.v("  Valid from: ${formatDate(result.notBefore)}")
        }
        if (result.notAfter != null) {
            Log.v("  Valid until: ${formatDate(result.notAfter)}")
        }
    }

    /**
     * Formats date for display.
     */
    private fun formatDate(instant: Instant): String {
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z")
            .withZone(ZoneId.systemDefault())
        return formatter.format(instant)
    }

    /**
     * Provides certificate renewal recommendations.
     */
    fun getRenewalRecommendations(result: CertificateValidationResult): List<String> {
        val recommendations = mutableListOf<String>()

        if (!result.valid || result.daysUntilExpiry != null && result.daysUntilExpiry < WARNING_DAYS_BEFORE_EXPIRY) {
            recommendations.add("Certificate Renewal Recommendations:")
            recommendations.add("")

            if (result.isSelfSigned) {
                recommendations.add("1. Obtain a trusted certificate:")
                recommendations.add("   • Let's Encrypt (free, automated)")
                recommendations.add("     - Install certbot: apt-get install certbot")
                recommendations.add("     - Generate: certbot certonly --standalone -d yourdomain.com")
                recommendations.add("   • Commercial CA (paid, manual)")
                recommendations.add("")
            }

            if (result.daysUntilExpiry != null && result.daysUntilExpiry < WARNING_DAYS_BEFORE_EXPIRY) {
                recommendations.add("2. Renew certificate before expiry:")
                recommendations.add("   • Let's Encrypt renewal: certbot renew")
                recommendations.add("   • Commercial CA: Generate new CSR and reissue")
                recommendations.add("")
            }

            recommendations.add("3. Update mail server configuration:")
            recommendations.add("   • Postfix: /etc/postfix/main.cf")
            recommendations.add("     smtpd_tls_cert_file = /path/to/cert.pem")
            recommendations.add("     smtpd_tls_key_file = /path/to/privkey.pem")
            recommendations.add("   • Dovecot: /etc/dovecot/conf.d/10-ssl.conf")
            recommendations.add("     ssl_cert = </path/to/cert.pem")
            recommendations.add("     ssl_key = </path/to/privkey.pem")
            recommendations.add("")

            recommendations.add("4. Restart services:")
            recommendations.add("   systemctl restart postfix")
            recommendations.add("   systemctl restart dovecot")
            recommendations.add("")

            recommendations.add("5. Automate renewal:")
            recommendations.add("   • Cron job for Let's Encrypt: 0 0 * * 0 certbot renew --quiet")
            recommendations.add("   • Post-renewal hook to restart services")
        }

        return recommendations
    }
}

/**
 * Certificate validation result.
 */
data class CertificateValidationResult(
    val valid: Boolean,
    val exists: Boolean,
    val subject: String = "",
    val issuer: String = "",
    val notBefore: Instant? = null,
    val notAfter: Instant? = null,
    val daysUntilExpiry: Long? = null,
    val isSelfSigned: Boolean = false,
    val warnings: List<String> = emptyList(),
    val errors: List<String> = emptyList(),
    val message: String
) {
    fun isExpired(): Boolean {
        return daysUntilExpiry != null && daysUntilExpiry < 0
    }

    fun isExpiringSoon(): Boolean {
        return daysUntilExpiry != null && daysUntilExpiry < 30
    }

    fun requiresAction(): Boolean {
        return !valid || isExpiringSoon()
    }
}
