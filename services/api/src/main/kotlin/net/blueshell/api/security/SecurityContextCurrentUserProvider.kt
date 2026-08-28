package net.blueshell.api.security

import net.blueshell.api.shared.security.CurrentUser
import net.blueshell.api.shared.security.CurrentUserProvider
import org.springframework.stereotype.Component

@Component
class SecurityContextCurrentUserProvider : CurrentUserProvider {
    override fun currentUser(): CurrentUser? {
        val principal = SecurityUtils.currentPrincipal() ?: return null
        return CurrentUser(
            id = principal.id,
            roles = principal.roles,
            addressId = principal.addressId
        )
    }
}
