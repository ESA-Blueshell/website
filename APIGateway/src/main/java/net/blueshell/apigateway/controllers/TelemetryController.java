package net.blueshell.apigateway.controllers;

import net.blueshell.common.communication.ICommunicationService;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/telemetry")
public class TelemetryController extends SwaggerController {
    private final ICommunicationService communicationService;

    public TelemetryController(ICommunicationService communicationService) {
        this.communicationService = communicationService;
    }

    @GetMapping
    public ResponseEntity<String> getTelemetry() {
        return communicationService.sendToTelemetryService("/", HttpMethod.GET, String.class);
    }

    @Override
    protected Object sendSwaggerRequestToService() {
        return communicationService.sendToTelemetryService(SWAGGER_SERVICE_URL, HttpMethod.GET, Object.class);
    }
}
