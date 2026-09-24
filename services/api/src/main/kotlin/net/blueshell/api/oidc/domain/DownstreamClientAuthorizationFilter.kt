package net.blueshell.api.oidc.domain

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import net.blueshell.api.security.SignInContext
import net.blueshell.api.security.SignIns
import net.blueshell.api.security.StepUp
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.shared.security.UserPrincipal
import org.springframework.security.authentication.AnonymousAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.filter.OncePerRequestFilter
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Every client registered with this server is an admin tool (see RegisteredClients), so
 * authorization requests are admin-only across the board. The gate does not branch on the
 * request's own `client_id`: letting that parameter decide whether the check runs would hand an
 * attacker the switch that turns it off (CWE-807). An account with two-factor gives a fresh code
 * for every authorization, trusted browser or not.
 */
internal class DownstreamClientAuthorizationFilter(
    private val signIns: SignIns,
) : OncePerRequestFilter() {
    override fun shouldNotFilter(request: HttpServletRequest): Boolean = request.requestURI != "/oauth2/authorize"

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val auth = SecurityContextHolder.getContext().authentication
        if (auth == null || auth is AnonymousAuthenticationToken || !auth.isAuthenticated) {
            // Unauthenticated — let the entry point redirect to /login.
            filterChain.doFilter(request, response)
            return
        }
        if (auth.authorities.none { it.authority == Role.ADMIN.reprString }) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Admin access required")
            return
        }
        val signIn = SignInContext.current()
        val proved = signIn != null && signIns.steppedUpWithin(signIn, StepUp.WINDOW)
        if ((auth.principal as? UserPrincipal)?.hasTwoFactor == true && !proved) {
            val target = LoginRedirectTarget.forRequest(request.requestURI, request::getParameter)
            response.sendRedirect("/login?stepUp=1&redirect=${URLEncoder.encode(target, StandardCharsets.UTF_8)}")
            return
        }
        filterChain.doFilter(request, response)
    }
}
