package net.blueshell.api.platform.oidc

import net.blueshell.api.shared.security.UserPrincipal
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Called by the Traefik ForwardAuth middleware for every request to a protected IngressRoute.
 * Returns 200 with user headers on success; 401 when the BSH_AUTH cookie is missing or invalid
 * (handled by the existing JwtAuthFilter + JwtAuthenticationEntryPoint before this controller).
 */
@RestController
@RequestMapping("/oauth2/forward-auth")
class ForwardAuthController {

    @GetMapping
    fun forwardAuth(@AuthenticationPrincipal principal: UserPrincipal?): ResponseEntity<Void> {
        if (principal == null) {
            return ResponseEntity.status(401).build()
        }
        return ResponseEntity.ok()
            .header("X-User-Id", principal.id.toString())
            .header("X-User-Groups", principal.roles.flatMap { it.allInheritedRoles }.map { it.name }.joinToString(","))
            .build()
    }
}
