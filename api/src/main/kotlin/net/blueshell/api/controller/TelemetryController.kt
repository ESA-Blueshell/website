package net.blueshell.api.controller

import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.annotation.security.PermitAll
import jakarta.ws.rs.PathParam
import net.blueshell.api.base.BaseController
import net.blueshell.api.common.enums.PlatformType
import net.blueshell.api.dto.TelemetryDTO
import net.blueshell.api.mapper.TelemetryMapper
import net.blueshell.api.service.TelemetryService
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*

@RestController
@Tag(name = "Telemetries")
class TelemetryController protected constructor(service: TelemetryService, mapper: TelemetryMapper) :
    BaseController<TelemetryService, TelemetryMapper>(service, mapper) {
    @GetMapping("/telemetry/{id}")
    @PermitAll
    fun findTelemetryById(@PathVariable id: Long?): TelemetryDTO? {
        val telemetry = service.findById(id)
        return mapper.toDTO(telemetry)
    }

    @PostMapping("/telemetry")
    @PreAuthorize("hasAuthority('BOARD')")
    @ResponseStatus(HttpStatus.CREATED)
    fun createTelemetry(@PathParam("platform") platform: PlatformType, @PathParam("url") url: String): TelemetryDTO? {
        val telemetry = service.createTelemetry(platform, url)
        return mapper.toDTO(telemetry)
    }
}
