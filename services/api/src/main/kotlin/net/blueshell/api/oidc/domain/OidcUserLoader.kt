package net.blueshell.api.oidc.domain

import net.blueshell.api.user.api.UserService
import net.blueshell.api.user.api.UserNotFoundException
import net.blueshell.api.shared.enums.Role
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

data class OidcUserData(
    val userId: Long,
    val username: String,
    val email: String,
    val firstName: String,
    val lastName: String,
    val roles: Set<Role>,
)

@Component
class OidcUserLoader(private val userService: UserService) {

    @Transactional(readOnly = true)
    fun load(username: String): OidcUserData? {
        val user = try {
            userService.findByUsername(username)
        } catch (_: UserNotFoundException) {
            return null
        }
        val id = user.id ?: return null
        val roles = user.roles.toSet()
        return OidcUserData(
            userId = id,
            username = user.username,
            email = user.email,
            firstName = user.firstName,
            lastName = user.lastName,
            roles = roles,
        )
    }
}
