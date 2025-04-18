package net.blueshell.telemetry.controller;

import jakarta.ws.rs.PathParam;
import net.blueshell.common.dto.TelemetryDTO;
import net.blueshell.common.enums.PlatformType;
import net.blueshell.db.BaseController;
import net.blueshell.telemetry.mapping.TelemetryMapper;
import net.blueshell.telemetry.model.Telemetry;
import net.blueshell.telemetry.service.TelemetryService;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
public class TelemetryController extends BaseController<TelemetryService, TelemetryMapper> {

    protected TelemetryController(TelemetryService service, TelemetryMapper mapper) {
        super(service, mapper);
    }

    @GetMapping("/{id}")
    public TelemetryDTO getTelemetry(
            @PathVariable UUID id
    ) {
        Telemetry telemetry = service.findById(id);
        return mapper.toDTO(telemetry);
    }

    @PostMapping
    public TelemetryDTO createTelemetry(
            @PathParam("platform") PlatformType platform,
            @PathParam("url") String url
    ) {
        Telemetry telemetry = service.createTelemetry(platform, url);
        return mapper.toDTO(telemetry);
    }
}
