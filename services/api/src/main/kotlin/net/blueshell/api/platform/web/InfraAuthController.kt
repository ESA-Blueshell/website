package net.blueshell.api.platform.web

import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.annotation.security.PermitAll
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import net.blueshell.api.infrastructure.security.SecurityUtils
import net.blueshell.api.shared.enums.Role
import org.springframework.beans.factory.annotation.Value
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/infra")
@Tag(name = "Infra")
class InfraAuthController(
    @param:Value($$"${frontend.url:http://localhost:3000}")
    private val frontendUrl: String
) {
    /**
     * Traefik ForwardAuth endpoint.
     *
     * Returns 200 (with X-Webauth-User header) for authenticated ADMIN users so that Traefik
     * allows the request through to the infra service. Grafana reads X-Webauth-User via its
     * Auth Proxy configuration to auto-sign-in the user.
     *
     * Returns 302 to the frontend login page for unauthenticated requests.
     * Returns 403 for authenticated users that lack the ADMIN role.
     */
    @GetMapping("/forward-auth")
    @PermitAll
    fun forwardAuth(request: HttpServletRequest, response: HttpServletResponse) {
        val principal = SecurityUtils.currentPrincipal()

        if (principal == null) {
            val originalUrl = buildOriginalUrl(request)
            val loginUrl = "$frontendUrl/login?redirect=${encodeUrl(originalUrl)}"
            response.sendRedirect(loginUrl)
            return
        }

        if (!SecurityUtils.hasAuthority(Role.ADMIN)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN)
            return
        }

        response.setHeader("X-Webauth-User", principal.username)
        response.status = HttpServletResponse.SC_OK
    }

    private fun buildOriginalUrl(request: HttpServletRequest): String {
        val host = request.getHeader("X-Forwarded-Host") ?: return frontendUrl
        val uri = request.getHeader("X-Forwarded-Uri") ?: "/"
        val proto = request.getHeader("X-Forwarded-Proto") ?: "https"
        return "$proto://$host$uri"
    }

    private fun encodeUrl(url: String): String =
        java.net.URLEncoder.encode(url, Charsets.UTF_8)
}
