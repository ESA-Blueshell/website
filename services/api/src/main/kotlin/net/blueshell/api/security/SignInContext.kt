package net.blueshell.api.security

import org.springframework.web.context.request.RequestAttributes
import org.springframework.web.context.request.RequestContextHolder
import java.io.Serializable

/** The sign-in the current request was honoured under, as [JwtAuthFilter] found it. */
object SignInContext {
    internal const val ATTRIBUTE = "net.blueshell.api.security.SignIn"

    fun current(): SignIn? =
        RequestContextHolder.getRequestAttributes()?.getAttribute(ATTRIBUTE, RequestAttributes.SCOPE_REQUEST) as? SignIn
}

/** What an authentication made from a sign-in carries, so OIDC can tell whether it still lives. */
data class SignInDetails(
    val signInId: String,
    val methods: Set<String>,
) : Serializable {
    private companion object {
        private const val serialVersionUID: Long = 1L
    }
}
