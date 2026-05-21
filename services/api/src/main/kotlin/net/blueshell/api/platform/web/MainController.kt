package net.blueshell.api.platform.web

import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.annotation.security.PermitAll
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@Tag(name = "Health")
class MainController {
    // `/health` is the external-facing endpoint: the public Gatus probe at
    // `https://esa-blueshell.nl/api/health` arrives here as `/health`
    // because Traefik's apex `PathPrefix(/api)` rule strips the `/api`
    // prefix uniformly (see apps/edge/ingressroutes/api.yaml).
    // Pod-level k8s probes use Spring Boot's
    // /actuator/health/{liveness,readiness} on the management port (8081)
    // instead, so they reflect real AvailabilityState rather than a
    // hardcoded `true`.
    @GetMapping("/health")
    @PermitAll
    fun healthCheck(): Boolean {
        return true
    }
}
