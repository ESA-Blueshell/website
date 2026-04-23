package net.blueshell.api.platform.web

import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.annotation.security.PermitAll
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@Tag(name = "Health")
class MainController {
    // `/api/health` is an alias exposed for the public Gatus probe at
    // `https://v2.esa-blueshell.nl/api/health`, which flows through
    // Traefik's apex `PathPrefix(/api)` rule. `/health` stays in place
    // because it is the path pinned in the three k8s
    // startup/readiness/liveness probes on the Deployment; changing
    // it would require updating those in lockstep.
    @GetMapping(value = ["/health", "/api/health"])
    @PermitAll
    fun healthCheck(): Boolean {
        return true
    }
}
