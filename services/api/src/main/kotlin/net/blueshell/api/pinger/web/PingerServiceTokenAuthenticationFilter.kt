package net.blueshell.api.pinger.web

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.AnonymousAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken
import org.springframework.web.filter.OncePerRequestFilter
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/**
 * Signs the always-on SiteCie painter in from its `X-Pinger-Service-Token` header, so it reaches
 * the report endpoints without a member sign-in.
 *
 * The header is matched against the value Vault provisions in constant time, so a wrong token
 * leaks nothing through how long the comparison takes. An empty configured token disables the
 * header entirely, which is what every environment but production holds until Vault fills it.
 */
class PingerServiceTokenAuthenticationFilter(
    private val serviceToken: String,
) : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val presented = request.getHeader("X-Pinger-Service-Token")
        if (presented != null && serviceToken.isNotEmpty() && matches(presented, serviceToken)) {
            val authentication =
                PreAuthenticatedAuthenticationToken("sitecie", null, listOf(SimpleGrantedAuthority("SITECIE")))
            val existing = SecurityContextHolder.getContext().authentication
            if (existing == null || existing is AnonymousAuthenticationToken) {
                val context = SecurityContextHolder.createEmptyContext()
                context.authentication = authentication
                SecurityContextHolder.setContext(context)
            }
        }
        filterChain.doFilter(request, response)
    }

    private fun matches(
        presented: String,
        expected: String,
    ): Boolean =
        MessageDigest.isEqual(
            presented.toByteArray(StandardCharsets.UTF_8),
            expected.toByteArray(StandardCharsets.UTF_8),
        )
}
