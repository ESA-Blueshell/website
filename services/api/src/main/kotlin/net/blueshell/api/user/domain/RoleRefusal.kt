package net.blueshell.api.user.domain

import net.blueshell.api.shared.enums.Role
import org.springframework.http.HttpStatus

// A code and the facts, never the sentence: `domains/user/refusals.ts` writes that. See ADR-026.
sealed class RoleRefusal(
    val status: HttpStatus,
    val code: String,
    val summary: String,
    val facts: Map<String, Any>,
) : RuntimeException(summary)

class RoleNotAssignable(role: Role) : RoleRefusal(
    HttpStatus.BAD_REQUEST,
    "RoleNotAssignable",
    "That role is not one an admin hands out.",
    mapOf("role" to role.name),
)

class LastAdministrator : RoleRefusal(
    HttpStatus.CONFLICT,
    "LastAdministrator",
    "That would leave the association with no administrator.",
    emptyMap(),
)
