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
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.io.IOException

@Component
class JwtAuthFilter(
    private val jwtTokenUtil: JwtTokenUtil,
    private val jwtRevocationService: JwtRevocationService,
    private val userService: UserService,
    private val authTokenCookieService: AuthTokenCookieService
) :
    OncePerRequestFilter() {
    @Throws(ServletException::class, IOException::class)
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val token = resolveToken(request) ?: run {
            filterChain.doFilter(request, response)
            return
        }

        val validation = jwtTokenUtil.parseAndValidate(token)
        if (!validation.isValid) {
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
            SecurityContextHolder.getContext().authentication = auth
        }

        filterChain.doFilter(request, response)
    }

    private fun resolveToken(request: HttpServletRequest): String? {
        val header = request.getHeader("Authorization")
        if (!header.isNullOrBlank() && header.startsWith("Bearer ")) {
            return header.substring(7).trim().takeIf { it.isNotBlank() }
        }
        return authTokenCookieService.resolveToken(request)
    }
}
