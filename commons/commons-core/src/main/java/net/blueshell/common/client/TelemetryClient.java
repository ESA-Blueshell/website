package net.blueshell.common.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;
import net.blueshell.common.dto.TelemetryDTO;
import net.blueshell.common.enums.PlatformType;

import java.util.UUID;

@FeignClient(
        name = "TelemetryService",
        contextId = "telemetryClient"
)
public interface TelemetryClient {

    @GetMapping("/telemetry/{id}")
    TelemetryDTO getTelemetry(@PathVariable("id") UUID id);

    @PostMapping("/telemetry")
    TelemetryDTO createTelemetry(@RequestParam("platform") PlatformType platform, @RequestParam("url") String url);
}