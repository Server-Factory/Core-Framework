package net.milosvasic.factory.security

import net.milosvasic.factory.common.validation.Validation

/**
 * Validator for file permissions.
 *
 * Validates that Permission enum values are valid.
 *
 * @author Mail Server Factory Team
 * @since 3.1.0
 */
class PermissionsValidator : Validation<Permission> {

    @Throws(IllegalArgumentException::class)
    override fun validate(vararg what: Permission): Boolean {
        // Permission enum values are always valid by definition
        // This validator exists for consistency with the Validation interface
        if (what.isEmpty()) {
            throw IllegalArgumentException("Expected at least one permission")
        }
        return true
    }
}