package net.blueshell.api.user.web

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Size
import net.blueshell.api.shared.enums.Role

/**
 * The full set of granted roles the person should end up with, rather than one to flip.
 *
 * Stating the end state is what makes the write idempotent and lets the record carry a clean
 * before and after; two admins saving the same person cannot toggle past each other.
 */
@Schema(name = "UpdateUserRolesRequest")
data class UpdateUserRolesRequest(
    val roles: Set<Role> = emptySet(),
    @field:Size(max = 1023)
    val note: String? = null,
)
