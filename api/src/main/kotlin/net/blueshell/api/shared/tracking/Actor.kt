package net.blueshell.api.shared.tracking

import net.blueshell.api.shared.enums.ActionActorType
import net.blueshell.api.shared.enums.Role

data class Actor(
    val userId: Long?,
    val type: ActionActorType,
    val role: Role
) {
    companion object {
        fun system(): Actor = Actor(
            userId = null,
            type = ActionActorType.SYSTEM,
            role = Role.ADMIN
        )

        fun user(userId: Long, role: Role): Actor = Actor(
            userId = userId,
            type = ActionActorType.USER,
            role = role
        )
    }
}
