package net.blueshell.api.security

import net.blueshell.api.shared.enums.Role
import net.blueshell.api.shared.security.UserPrincipal
import org.springframework.security.authentication.TestingAuthenticationToken
import org.springframework.security.core.Authentication

internal fun authWithRoles(
    id: Long = 1L,
    roles: Set<Role> = setOf(Role.GUEST),
    addressId: Long? = null,
    personDetailsId: Long? = null
): Authentication {
    val principal = UserPrincipal(
        id = id,
        usernameValue = "user-$id",
        passwordValue = "password",
        enabledValue = true,
        roles = roles,
        addressId = addressId,
        personDetailsId = personDetailsId,
    )

    return TestingAuthenticationToken(principal, "password", principal.authorities)
}

internal fun guestAuth(id: Long = 1L, addressId: Long? = null): Authentication =
    authWithRoles(id = id, roles = setOf(Role.GUEST), addressId = addressId)

internal fun memberAuth(id: Long = 1L, addressId: Long? = null): Authentication =
    authWithRoles(id = id, roles = setOf(Role.MEMBER), addressId = addressId)

internal fun committeeAuth(id: Long = 1L, addressId: Long? = null): Authentication =
    authWithRoles(id = id, roles = setOf(Role.COMMITTEE), addressId = addressId)

internal fun boardAuth(id: Long = 1L, addressId: Long? = null): Authentication =
    authWithRoles(id = id, roles = setOf(Role.BOARD), addressId = addressId)

internal fun adminAuth(id: Long = 1L, addressId: Long? = null): Authentication =
    authWithRoles(id = id, roles = setOf(Role.ADMIN), addressId = addressId)
