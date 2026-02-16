package net.blueshell.api.shared.security

import net.blueshell.api.domain.user.persistence.User

object UserPrincipalMapper {
    fun fromUser(user: User): UserPrincipal {
        return UserPrincipal(
            id = user.id!!,
            usernameValue = user.username,
            passwordValue = user.password,
            enabledValue = user.enabled,
            roles = user.roles.toSet(),
            addressId = user.addressId,
            personDetailsId = user.personDetailsId,
        )
    }
}
