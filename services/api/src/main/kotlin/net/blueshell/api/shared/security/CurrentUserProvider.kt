package net.blueshell.api.shared.security

import net.blueshell.api.shared.enums.Role

data class CurrentUser(
    val id: Long,
    val roles: Set<Role>,
    val addressId: Long?
)

interface CurrentUserProvider {
    fun currentUser(): CurrentUser?
}
