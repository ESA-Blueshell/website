package net.blueshell.api.domain.telemetry.web

import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.annotation.security.PermitAll
import jakarta.ws.rs.PathParam
import net.blueshell.api.shared.enums.PlatformType
import net.blueshell.api.shared.web.BaseController
import net.blueshell.api.telemetry.application.TelemetryService
import net.blueshell.api.telemetry.web.dto.TelemetryDTO
import net.blueshell.api.telemetry.web.mapping.asDto
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*

@RestController
@Tag(name = "Telemetries")
class TelemetryController(service: TelemetryService) : BaseController<TelemetryService>(service) {
    @GetMapping("/telemetry/{id}")
    @PermitAll
    fun findTelemetryById(@PathVariable id: Long): TelemetryDTO? {
        val telemetry = service.findById(id)
        return telemetry.asDto()
    }

    @PostMapping("/telemetry")
    @PreAuthorize("hasAuthority('BOARD')")
    @ResponseStatus(HttpStatus.CREATED)
    fun createTelemetry(@PathParam("platform") platform: PlatformType, @PathParam("url") url: String): TelemetryDTO? {
        val telemetry = service.createTelemetry(platform, url)
        return telemetry.asDto()
    }
}
