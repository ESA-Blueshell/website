package net.blueshell.api.auth.domain

import net.blueshell.api.security.Browser
import net.blueshell.api.security.SignIns
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.user.api.UserService
import net.blueshell.api.user.persistence.User
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.stereotype.Service

/** Who is signed in, as the login answer states them. */
data class Signer(
    val userId: Long,
    val username: String,
    val roles: List<Role>,
    val addressId: Long?,
) {
    companion object {
        fun of(user: User) = Signer(requireNotNull(user.id), user.username, user.inheritedRoles.sortedBy { it.ordinal }, user.addressId)
    }
}

/** A password that opened a sign-in, and the token that carries it. */
data class SignedIn(
    val signer: Signer,
    val issued: SignIns.Issued,
)

@Service
class AuthenticationService(
    private val authenticationManager: AuthenticationManager,
    private val users: UserService,
    private val signIns: SignIns,
) {
    fun signIn(
        username: String,
        password: String,
        browser: Browser,
    ): SignedIn {
        authenticationManager.authenticate(UsernamePasswordAuthenticationToken(username, password))
        val user = users.findByUsername(username)
        return SignedIn(Signer.of(user), signIns.start(requireNotNull(user.id), browser))
    }
}
