package net.blueshell.api.shared.security

import net.blueshell.api.user.persistence.User

object UserPrincipalMapper {
    fun fromUser(user: User): UserPrincipal =
        UserPrincipal(
            id = user.id!!,
            usernameValue = user.username,
            passwordValue = user.password,
            enabledValue = user.enabled,
            roles = user.rolesInForce,
            locked = user.lockedAt != null,
            hasTwoFactor = user.hasTwoFactor,
            addressId = user.addressId,
            personDetailsId = user.personDetailsId,
        )
}
