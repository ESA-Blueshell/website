package net.blueshell.api.domain.auth.application

import net.blueshell.api.shared.enums.Role

data class AuthResult(
    val token: String,
    val userId: Long,
    val username: String,
    val expiresAtEpochMs: Long,
    val roles: Set<Role>,
    val addressId: Long?
)
