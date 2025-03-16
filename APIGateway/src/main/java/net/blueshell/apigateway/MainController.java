package net.blueshell.apigateway;

import net.blueshell.common.TestClass;
import net.blueshell.common.communication.CommunicationService;
import net.blueshell.common.communication.ICommunicationService;
import net.blueshell.common.communication.MessageType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MainController {

    private final static ICommunicationService communicationService = new CommunicationService();

    @RequestMapping("/")
    public String home() {
        return TestClass.Test + "test";
    }

    @RequestMapping("/blog")
    public ResponseEntity<String> blog() {
        return communicationService.sendToBlogService("/", MessageType.GET, null, null);
    }

    @RequestMapping("/email")
    public ResponseEntity<String> email() {
        return communicationService.sendToEmailParserService("/", MessageType.GET, null, null);
    }

    @RequestMapping("/event")
    public ResponseEntity<String> event() {
        return communicationService.sendToEventParserService("/", MessageType.GET, null, null);
    }

    @RequestMapping("/social-media")
    public ResponseEntity<String> socialMedia() {
        return communicationService.sendToSocialMediaService("/", MessageType.GET, null, null);
    }

    @RequestMapping("/telemetry")
    public ResponseEntity<String> telemetry() {
        return communicationService.sendToTelemetryService("/", MessageType.GET, null, null);
    }
}
