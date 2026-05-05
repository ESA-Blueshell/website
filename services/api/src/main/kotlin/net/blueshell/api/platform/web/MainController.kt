package net.blueshell.api.platform.web

import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.annotation.security.PermitAll
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@Tag(name = "Health")
class MainController {
    // `/health` is the single source of truth. The public Gatus probe at
    // `https://v2.esa-blueshell.nl/api/health` arrives here as `/health`
    // because Traefik's apex `PathPrefix(/api)` rule now strips the
    // `/api` prefix uniformly (see apps/edge/ingressroutes/api.yaml).
    // The pod-level k8s probes hit port 8080 directly via tcpSocket
    // (no path), so changing this controller's path no longer needs
    // a lockstep manifest update.
    @GetMapping("/health")
    @PermitAll
    fun healthCheck(): Boolean {
        return true
    }
}
