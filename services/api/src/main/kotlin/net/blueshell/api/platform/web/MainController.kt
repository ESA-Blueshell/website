package net.blueshell.api.platform.web

import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.annotation.security.PermitAll
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.info.BuildProperties
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@Tag(name = "Health")
class MainController(
    buildProperties: ObjectProvider<BuildProperties>,
) {
    // The bean exists only where the build wrote build-info.properties, so a
    // run from source answers `unknown` rather than refusing to start.
    private val version: String = buildProperties.getIfAvailable()?.version ?: "unknown"

    /** What is running, on the port the outside world can reach. */
    data class Version(
        val version: String,
    )

    // Actuator carries this too, but on the management port, which is
    // pod-local: a person asking what is running cannot reach 8081.
    @GetMapping("/version")
    @PermitAll
    fun version(): Version = Version(version)

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
    // A constant is the answer: reaching the handler at all is the check.
    @Suppress("FunctionOnlyReturningConstant")
    fun healthCheck(): Boolean {
        return true
    }
}
