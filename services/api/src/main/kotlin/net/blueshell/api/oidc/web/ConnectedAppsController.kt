package net.blueshell.api.oidc.web

import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag
import net.blueshell.api.oidc.domain.ConnectedApp
import net.blueshell.api.oidc.domain.ConnectedApps
import net.blueshell.api.shared.security.UserPrincipal
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.time.Instant

/** One connected app as the security page shows it; the page formats [authorizedAt] itself. */
@Schema(name = "ConnectedAppResponse")
data class ConnectedAppResponse(
    val id: String,
    val name: String,
    val authorizedAt: Instant?,
)

/** The member's connected apps, listed and revoked on the account security page. Cookie-only (api ADR-030). */
@RestController
@Tag(name = "Connected Apps")
@RequestMapping("/me/connected-apps")
class ConnectedAppsController(
    private val connectedApps: ConnectedApps,
) {
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    fun connectedApps(
        @AuthenticationPrincipal principal: UserPrincipal,
    ): List<ConnectedAppResponse> = connectedApps.of(principal.username).map { it.asResponse() }

    @DeleteMapping("/{appId}")
    @PreAuthorize("isAuthenticated()")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun revokeConnectedApp(
        @AuthenticationPrincipal principal: UserPrincipal,
        @PathVariable appId: String,
    ) = connectedApps.revoke(principal.username, appId)

    private fun ConnectedApp.asResponse() = ConnectedAppResponse(id, name, authorizedAt)
}
