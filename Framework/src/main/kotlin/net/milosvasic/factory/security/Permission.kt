package net.milosvasic.factory.security

/**
 * Creates a Permission set from a numeric value (0-7).
 *
 * This is a top-level function to support the syntax: Permission(6)
 *
 * @param value Numeric permission value (0-7)
 * @return Set of permissions
 */
fun Permission(value: Int): Set<Permission> {
    if (value < 0 || value > 7) {
        throw IllegalArgumentException("Permission value must be between 0 and 7, got: $value")
    }

    val permissions = mutableSetOf<Permission>()
    if (value and 4 != 0) permissions.add(Permission.READ)
    if (value and 2 != 0) permissions.add(Permission.WRITE)
    if (value and 1 != 0) permissions.add(Permission.EXECUTE)

    return if (permissions.isEmpty()) Permission.NO_PERMISSIONS else permissions
}

/**
 * File permission representation.
 *
 * @since 3.1.0
 */
enum class Permission(val code: String) {
    READ("r"),
    WRITE("w"),
    EXECUTE("x"),
    NONE("-"),
    ALL("rwx");

    companion object {
        val READ_WRITE = setOf(READ, WRITE)
        val READ_WRITE_EXECUTE = setOf(READ, WRITE, EXECUTE)
        val READ_ONLY = setOf(READ)
        val NO_PERMISSIONS = emptySet<Permission>()

        /**
         * Converts numeric permission value (0-7) to a set of permissions.
         *
         * @param value Numeric permission value (0-7)
         * @return Set of permissions
         */
        operator fun invoke(value: Int): Set<Permission> {
            if (value < 0 || value > 7) {
                throw IllegalArgumentException("Permission value must be between 0 and 7, got: $value")
            }

            val permissions = mutableSetOf<Permission>()
            if (value and 4 != 0) permissions.add(READ)
            if (value and 2 != 0) permissions.add(WRITE)
            if (value and 1 != 0) permissions.add(EXECUTE)

            return if (permissions.isEmpty()) NO_PERMISSIONS else permissions
        }
    }
}

/**
 * File permissions utility.
 *
 * Can be used as object (Permissions.get()) or instantiated (Permissions(...)).
 *
 * @since 3.1.0
 */
class Permissions {
    val owner: Set<Permission>
    val group: Set<Permission>
    val others: Set<Permission>

    /**
     * Primary constructor accepting sets of permissions.
     */
    constructor(owner: Set<Permission>, group: Set<Permission>, others: Set<Permission>) {
        this.owner = owner
        this.group = group
        this.others = others
    }

    /**
     * Constructor accepting single Permission values (converts ALL/NONE to sets).
     */
    constructor(owner: Permission, group: Permission, others: Permission) : this(
        owner = when (owner) {
            Permission.ALL -> Permission.READ_WRITE_EXECUTE
            Permission.NONE -> Permission.NO_PERMISSIONS
            else -> setOf(owner)
        },
        group = when (group) {
            Permission.ALL -> Permission.READ_WRITE_EXECUTE
            Permission.NONE -> Permission.NO_PERMISSIONS
            else -> setOf(group)
        },
        others = when (others) {
            Permission.ALL -> Permission.READ_WRITE_EXECUTE
            Permission.NONE -> Permission.NO_PERMISSIONS
            else -> setOf(others)
        }
    )

    /**
     * Obtains permission string.
     */
    fun obtain(): String {
        return "${permissionValue(owner)}${permissionValue(group)}${permissionValue(others)}"
    }

    companion object {
        val ALL = Permission.READ_WRITE_EXECUTE
        val NONE = Permission.NO_PERMISSIONS

        /**
         * Gets Unix-style permission string (e.g., "755", "644").
         */
        fun get(
            owner: Set<Permission> = setOf(Permission.READ, Permission.WRITE),
            group: Set<Permission> = setOf(Permission.READ),
            others: Set<Permission> = setOf(Permission.READ)
        ): String {
            return "${permissionValue(owner)}${permissionValue(group)}${permissionValue(others)}"
        }

        /**
         * Obtains permission string from sets of permissions.
         */
        fun obtain(
            owner: Set<Permission>,
            group: Set<Permission>,
            others: Set<Permission>
        ): String {
            return get(owner, group, others)
        }

        private fun permissionValue(perms: Set<Permission>): Int {
            var value = 0
            if (Permission.READ in perms) value += 4
            if (Permission.WRITE in perms) value += 2
            if (Permission.EXECUTE in perms) value += 1
            return value
        }
    }

    private fun permissionValue(perms: Set<Permission>): Int {
        return Companion.permissionValue(perms)
    }
}
