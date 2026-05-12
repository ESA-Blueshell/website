package net.blueshell.api.infrastructure.security

import jakarta.servlet.FilterChain
import jakarta.servlet.ServletException
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import net.blueshell.api.domain.user.application.UserService
import net.blueshell.api.domain.user.application.exception.UserNotFoundException
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource
import org.springframework.security.web.context.SecurityContextRepository
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.io.IOException

@Component
class JwtAuthFilter(
    private val jwtTokenUtil: JwtTokenUtil,
    private val jwtRevocationService: JwtRevocationService,
    private val userService: UserService,
    private val authTokenCookieService: AuthTokenCookieService,
    private val securityContextRepository: SecurityContextRepository,
) :
    OncePerRequestFilter() {
    @Throws(ServletException::class, IOException::class)
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        // Try every candidate credential in order until one validates.
        // The previous code short-circuited on `Authorization: Bearer ...`,
        // which let an opaque third-party token (Stalwart webadmin's own
        // OAuth, in particular) override a valid BSH_AUTH cookie and leave
        // the request anonymous. Authorization still wins when both are
        // valid — only an *invalid* Authorization yields to the cookie.
        val validation = resolveTokenCandidates(request)
            .asSequence()
            .map { jwtTokenUtil.parseAndValidate(it) }
            .firstOrNull { it.isValid }
            ?: run {
                filterChain.doFilter(request, response)
                return
            }

        if (jwtRevocationService.isRevoked(validation.jti)) {
            filterChain.doFilter(request, response)
            return
        }

        val username = validation.username ?: run {
            filterChain.doFilter(request, response)
            return
        }

        val principal = try {
            userService.loadUserPrincipalByUsername(username)
        } catch (ex: UserNotFoundException) {
            filterChain.doFilter(request, response)
            return
        }
        if (username == principal.username) {
            val auth = UsernamePasswordAuthenticationToken(
                principal, null, principal.authorities
            )
            auth.details = WebAuthenticationDetailsSource().buildDetails(request)
            // saveContext is required under requireExplicitSave=true so
            // deferred resolvers (e.g. SAS authorize) see the principal.
            val context = SecurityContextHolder.createEmptyContext()
            context.authentication = auth
            SecurityContextHolder.setContext(context)
            securityContextRepository.saveContext(context, request, response)
        }

        filterChain.doFilter(request, response)
    }

    private fun resolveTokenCandidates(request: HttpServletRequest): List<String> =
        buildList {
            request.getHeader("Authorization")
                ?.takeIf { it.startsWith("Bearer ") }
                ?.substring(7)?.trim()?.takeIf { it.isNotBlank() }
                ?.let { add(it) }
            authTokenCookieService.resolveToken(request)?.let { add(it) }
        }
}
