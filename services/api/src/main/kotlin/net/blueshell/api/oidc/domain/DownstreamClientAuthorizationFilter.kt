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
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.filter.OncePerRequestFilter
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Gates an authorization request by the policy its client asks for. Every admin tool
 * (see RegisteredClients) stays admin-only and, for a two-factor account, needs a fresh code for
 * every authorization. The one exception is `pinger-app`, which a plain member signs in: it needs
 * only the member role and no step-up.
 *
 * Branching on `client_id` is safe only because the weaker policy never reaches a stronger
 * resource. A member-grade `pinger-app` token is honoured by nothing but the member-scoped
 * `pinger/report` chain, and its code is delivered only to a loopback address the member's own
 * machine owns, so the parameter cannot turn the admin gate off on an admin tool (CWE-807): the
 * admin branch stands for every other client.
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
            filterChain.doFilter(request, response)
            return
        }
        if (request.getParameter("client_id") == "pinger-app") {
            enforceMemberPolicy(request, response, filterChain, auth)
        } else {
            enforceAdminPolicy(request, response, filterChain, auth)
        }
    }

    private fun enforceMemberPolicy(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
        auth: Authentication,
    ) {
        if (auth.authorities.none { it.authority == Role.MEMBER.reprString }) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Member access required")
            return
        }
        filterChain.doFilter(request, response)
    }

    private fun enforceAdminPolicy(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
        auth: Authentication,
    ) {
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
