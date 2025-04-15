package net.blueshell.apigateway.controllers;

import net.blueshell.common.communicator.TelemetryCommunicator;
import net.blueshell.common.dto.TelemetryDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/telemetry")
public class TelemetryController {

    @Autowired
    private TelemetryCommunicator communicator;

    @GetMapping
    public TelemetryDTO getTelemetry() {
        return communicator.sendSync("/", HttpMethod.GET, TelemetryDTO.class);
    }
}
