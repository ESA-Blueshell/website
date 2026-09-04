package net.blueshell.api.oidc.web

import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.annotation.security.PermitAll
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.shared.security.UserPrincipal
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

data class ServiceEntry(
    val id: String,
    val name: String,
    val url: String,
    val iconUrl: String,
    val description: String,
)

private val ALL_SERVICES = listOf(
    ServiceEntry("stalwart", "Mail admin", "https://stalwart.esa-blueshell.nl", "/icons/stalwart.svg", "Mail server admin"),
    ServiceEntry("headlamp", "Headlamp", "https://headlamp.esa-blueshell.nl", "/icons/headlamp.svg", "Kubernetes dashboard"),
    ServiceEntry("vault", "Vault", "https://vault.esa-blueshell.nl", "/icons/vault.svg", "Secrets management"),
    ServiceEntry("traefik", "Traefik", "https://traefik.esa-blueshell.nl/dashboard/", "/icons/traefik.svg", "Edge router dashboard"),
    ServiceEntry("status", "Status", "https://status.esa-blueshell.nl", "/icons/gatus.svg", "Service status page"),
)

@RestController
@Tag(name = "My Services")
@RequestMapping("/me/services")
class MyServicesController {

    @GetMapping
    @PermitAll
    fun myServices(@AuthenticationPrincipal principal: UserPrincipal?): ResponseEntity<List<ServiceEntry>> {
        if (principal == null) {
            return ResponseEntity.ok(listOf(
                ALL_SERVICES.first { it.id == "status" }
            ))
        }
        // The same role gates as ForwardAuthController.HOST_ROLE, so the catalogue offers only
        // what forwardAuth will let through. Status is public on both sides: no gate here, no
        // HOST_ROLE entry, and no forward-auth middleware on its IngressRoute. Both must
        // change together.
        val visible = ALL_SERVICES.filter { service ->
            val required = when (service.id) {
                "headlamp", "vault", "traefik" -> Role.ADMIN
                "stalwart"                     -> Role.BOARD
                else                           -> null
            }
            required == null || principal.hasAuthority(required)
        }
        return ResponseEntity.ok(visible)
    }
}
