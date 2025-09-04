package net.blueshell.api.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.ws.rs.PathParam;
import net.blueshell.api.base.BaseController;
import net.blueshell.api.common.enums.PlatformType;
import net.blueshell.api.dto.TelemetryDTO;
import net.blueshell.api.mapper.TelemetryMapper;
import net.blueshell.api.model.Telemetry;
import net.blueshell.api.service.TelemetryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@Tag(name = "Telemetries")
public class TelemetryController extends BaseController<TelemetryService, TelemetryMapper> {

    protected TelemetryController(TelemetryService service, TelemetryMapper mapper) {
        super(service, mapper);
    }

    @GetMapping("/telemetry/{id}")
    public TelemetryDTO findTelemetryById(
            @PathVariable UUID id
    ) {
        Telemetry telemetry = service.findById(id);
        return mapper.toDTO(telemetry);
    }

    @PostMapping("/telemetry")
    public TelemetryDTO createTelemetry(
            @PathParam("platform") PlatformType platform,
            @PathParam("url") String url
    ) {
        Telemetry telemetry = service.createTelemetry(platform, url);
        return mapper.toDTO(telemetry);
    }
}
