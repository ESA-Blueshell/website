package net.blueshell.api.user.web

import io.swagger.v3.oas.annotations.media.Schema
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.user.domain.RoleSource
import java.time.Instant

/**
 * What one person may reach, split by where each role comes from.
 *
 * [assignable] rides along so the picker is drawn from what the api will accept rather than from
 * a list repeated on the other side of the wire.
 */
@Schema(name = "UserRolesResponse")
data class UserRolesResponse(
    val userId: Long,
    val roles: List<Role>,
    val granted: List<Role>,
    val derived: List<DerivedRoleResponse>,
    val implied: List<Role>,
    val assignable: List<Role>,
)

/** A role the person holds because of something else, and the something else. */
@Schema(name = "DerivedRoleResponse")
data class DerivedRoleResponse(
    val role: Role,
    val source: RoleSource,
)

@Schema(name = "RoleChangeResponse")
data class RoleChangeResponse(
    val id: Long,
    val actorId: Long,
    val actorName: String,
    val changedAt: Instant,
    val before: List<Role>,
    val after: List<Role>,
    val note: String?,
)
