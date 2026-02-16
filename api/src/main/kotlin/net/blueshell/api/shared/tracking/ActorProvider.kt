package net.blueshell.api.shared.tracking

import net.blueshell.api.shared.enums.Role
import net.blueshell.api.shared.security.CurrentUser
import net.blueshell.api.shared.security.CurrentUserProvider
import org.springframework.stereotype.Component

@Component
class ActorProvider(
    private val currentUserProvider: CurrentUserProvider
) {
    fun currentOrSystem(): Actor {
        val user = currentUserProvider.currentUser() ?: return Actor.system()
        return Actor.user(user.id, highestRole(user))
    }

    private fun highestRole(user: CurrentUser): Role {
        return user.roles.maxByOrNull { it.ordinal } ?: Role.ANONYMOUS
    }
}
