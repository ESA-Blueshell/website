package net.blueshell.api.platform.oidc

import net.blueshell.api.shared.enums.Role
import net.blueshell.api.domain.user.persistence.repository.UserRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

data class OidcUserData(
    val username: String,
    val email: String,
    val firstName: String,
    val lastName: String,
    val roles: Set<Role>,
)

@Component
class OidcUserLoader(private val userRepository: UserRepository) {

    @Transactional(readOnly = true)
    fun load(username: String): OidcUserData? {
        val user = userRepository.findByUsername(username).orElse(null) ?: return null
        // Access roles within the transaction to initialize the lazy collection.
        val roles = user.roles.toSet()
        return OidcUserData(
            username = user.username,
            email = user.email,
            firstName = user.firstName,
            lastName = user.lastName,
            roles = roles,
        )
    }
}
