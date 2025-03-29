package net.blueshell.apigateway.controllers;

import net.blueshell.common.communication.CommunicationService;
import net.blueshell.common.communication.ICommunicationService;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/telemetry")
public class TelemetryController {
    private final static ICommunicationService communicationService = new CommunicationService();

    @GetMapping
    public ResponseEntity<String> getTelemetry() {
        return communicationService.sendToTelemetryService("/", HttpMethod.GET, String.class);
    }
}
