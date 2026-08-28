package net.blueshell.api.telemetry.web

import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.annotation.security.PermitAll
import jakarta.validation.Valid
import net.blueshell.api.telemetry.domain.TelemetryService
import net.blueshell.api.shared.web.BaseController
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*

@RestController
@Tag(name = "Telemetries")
class TelemetryController(
    service: TelemetryService
) : BaseController<TelemetryService>(service) {
    @GetMapping("/telemetry/{id}")
    @PermitAll
    fun findTelemetryById(@PathVariable id: Long): TelemetryResponse? {
        return service.findById(id).asResponse()
    }

    @PostMapping("/telemetry")
    @PreAuthorize("hasPermission('__NO_TARGET__', 'Telemetry', 'write')")
    @ResponseStatus(HttpStatus.CREATED)
    fun createTelemetry(
        @Valid @RequestBody request: CreateTelemetryRequest
    ): TelemetryResponse? {
        return service.createTelemetry(request.platform, request.url).asResponse()
    }
}
