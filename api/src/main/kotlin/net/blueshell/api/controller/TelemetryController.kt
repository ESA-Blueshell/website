package net.blueshell.api.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.security.PermitAll;
import jakarta.ws.rs.PathParam;
import net.blueshell.api.base.BaseController;
import net.blueshell.api.common.enums.PlatformType;
import net.blueshell.api.dto.TelemetryDTO;
import net.blueshell.api.mapper.TelemetryMapper;
import net.blueshell.api.model.Telemetry;
import net.blueshell.api.service.TelemetryService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@Tag(name = "Telemetries")
public class TelemetryController extends BaseController<TelemetryService, TelemetryMapper> {

    protected TelemetryController(TelemetryService service, TelemetryMapper mapper) {
        super(service, mapper);
    }

    @GetMapping("/telemetry/{id}")
    @PermitAll
    public TelemetryDTO findTelemetryById(@PathVariable Long id) {
        Telemetry telemetry = service.findById(id);
        return mapper.toDTO(telemetry);
    }

    @PostMapping("/telemetry")
    @PreAuthorize("hasAuthority('BOARD')")
    @ResponseStatus(HttpStatus.CREATED)
    public TelemetryDTO createTelemetry(@PathParam("platform") PlatformType platform, @PathParam("url") String url) {
        Telemetry telemetry = service.createTelemetry(platform, url);
        return mapper.toDTO(telemetry);
    }
}
