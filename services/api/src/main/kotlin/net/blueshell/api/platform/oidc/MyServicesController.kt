package net.blueshell.api.platform.oidc

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
    ServiceEntry("listmonk", "Listmonk", "https://mail-admin.v2.esa-blueshell.nl", "/icons/listmonk.svg", "Newsletter & mailing"),
    ServiceEntry("stalwart", "Mail admin", "https://mail.v2.esa-blueshell.nl/webadmin", "/icons/stalwart.svg", "Mail server admin"),
    ServiceEntry("headlamp", "Headlamp", "https://kube.v2.esa-blueshell.nl", "/icons/headlamp.svg", "Kubernetes dashboard"),
    ServiceEntry("vault", "Vault", "https://vault.v2.esa-blueshell.nl", "/icons/vault.svg", "Secrets management"),
    ServiceEntry("status", "Status", "https://status.v2.esa-blueshell.nl", "/icons/gatus.svg", "Service status page"),
)

@RestController
@RequestMapping("/api/me/services")
class MyServicesController {

    @GetMapping
    fun myServices(@AuthenticationPrincipal principal: UserPrincipal?): ResponseEntity<List<ServiceEntry>> {
        if (principal == null) {
            return ResponseEntity.ok(listOf(
                ALL_SERVICES.first { it.id == "status" }
            ))
        }
        val isAdmin = principal.roles.any { it.matchesRole(Role.ADMIN) }
        val isMember = principal.roles.any { it.matchesRole(Role.MEMBER) }

        val visible = ALL_SERVICES.filter { service ->
            when (service.id) {
                "headlamp", "vault" -> isAdmin
                "listmonk", "stalwart" -> isAdmin
                else -> true
            }
        }
        return ResponseEntity.ok(visible)
    }
}
