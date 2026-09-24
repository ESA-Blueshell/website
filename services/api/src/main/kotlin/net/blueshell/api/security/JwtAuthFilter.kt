package net.blueshell.api.security

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import net.blueshell.api.user.api.UserNotFoundException
import net.blueshell.api.user.api.UserService
import org.springframework.http.HttpHeaders
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.context.SecurityContextRepository
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

/**
 * Signs a request in from the auth cookie, and only from the cookie: a bearer header is not a
 * browser's credential (api ADR-030). The cookie is honoured while the sign-in it names agrees
 * with it, and is rotated here when it is due.
 */
@Component
class JwtAuthFilter(
    private val signIns: SignIns,
    private val userService: UserService,
    private val authTokenCookieService: AuthTokenCookieService,
    private val securityContextRepository: SecurityContextRepository,
) : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val token = authTokenCookieService.resolveToken(request)
        if (token == null) {
            filterChain.doFilter(request, response)
            return
        }
        val resolution =
            signIns.resolve(
                token,
                Browser.of(request.getHeader(HttpHeaders.USER_AGENT)),
                mayRotate = request.requestURI !in UNROTATED_PATHS && !response.isCommitted,
            )
        if (resolution is SignIns.Resolution.Honoured) {
            signIn(request, response, resolution)
        }
        filterChain.doFilter(request, response)
    }

    private fun signIn(
        request: HttpServletRequest,
        response: HttpServletResponse,
        resolution: SignIns.Resolution.Honoured,
    ) {
        val signIn = resolution.signIn
        val principal =
            try {
                userService.loadUserPrincipalById(signIn.userId)
            } catch (_: UserNotFoundException) {
                return
            }
        val auth = UsernamePasswordAuthenticationToken(principal, null, principal.authorities)
        auth.details = SignInDetails(signIn.id, signIn.methods)
        val context = SecurityContextHolder.createEmptyContext()
        context.authentication = auth
        SecurityContextHolder.setContext(context)
        // saveContext is required under requireExplicitSave=true so deferred resolvers
        // (the authorize endpoint, for one) see the principal.
        securityContextRepository.saveContext(context, request, response)
        request.setAttribute(SignInContext.ATTRIBUTE, signIn)
        resolution.rotated?.let { authTokenCookieService.writeAuthCookie(response, it.token, it.cookieTtl.toMillis()) }
    }

    private companion object {
        // Logout clears the cookie itself. Forward-auth answers Traefik, which does not hand a
        // Set-Cookie back to the browser, so a rotation there would strand the browser on an old id.
        val UNROTATED_PATHS = setOf("/auth", "/auth/logout", "/oauth2/forward-auth")
    }
}
