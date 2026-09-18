package net.blueshell.api.user.domain

import io.swagger.v3.oas.annotations.media.Schema
import net.blueshell.api.shared.enums.Role

/**
 * Which roles an admin may hand out, and where the rest come from.
 *
 * A **granted** role is written onto a person by hand and is the only kind this module lets an
 * admin change. A **derived** role is kept in step with something else the person has, so
 * granting one would be a lie: the listener that owns it takes it straight back off. See
 * api ADR-028.
 */
object GrantedRoles {
    /** The roles an admin may grant, in the order a picker shows them. */
    val ASSIGNABLE: List<Role> = listOf(Role.BOARD, Role.TREASURER, Role.ADMIN)

    /** A derived role, and the thing it follows. */
    val DERIVED: Map<Role, RoleSource> =
        mapOf(
            Role.MEMBER to RoleSource.MEMBERSHIP,
            Role.COMMITTEE to RoleSource.COMMITTEE_SEAT,
        )

    /** The role every account carries from creation, and which nothing hands out. */
    val DEFAULT: Role = Role.GUEST

    fun isAssignable(role: Role): Boolean = role in ASSIGNABLE

    fun sourceOf(role: Role): RoleSource =
        DERIVED[role] ?: when (role) {
            DEFAULT -> RoleSource.ACCOUNT
            else -> RoleSource.GRANT
        }
}

/** Where a role a person holds came from. */
@Schema(enumAsRef = true)
enum class RoleSource {
    /** Handed over by an admin, and the only source this module writes. */
    GRANT,

    /** The account itself: every account is a guest. */
    ACCOUNT,

    /** An active membership. */
    MEMBERSHIP,

    /** A seat on a committee. */
    COMMITTEE_SEAT,
}
