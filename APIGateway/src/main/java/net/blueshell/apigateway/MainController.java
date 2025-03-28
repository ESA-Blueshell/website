package net.blueshell.apigateway;

import net.blueshell.common.communication.AsyncCommunicationService;
import net.blueshell.common.communication.CommunicationService;
import net.blueshell.common.communication.IAsyncCommunicationService;
import net.blueshell.common.communication.ICommunicationService;
import net.blueshell.common.communication.communicators.ApiGatewayCommunicator;
import net.blueshell.common.communication.communicators.base.MessageType;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MainController {

    private final static ICommunicationService communicationService = new CommunicationService();
    private final static IAsyncCommunicationService asyncCommunicationService = new AsyncCommunicationService();
    private final RabbitTemplate template;

    @Autowired
    public MainController(RabbitTemplate template) {
        System.out.println("Blog Service Started");
        this.template = template;
    }

    @RequestMapping("/")
    public String home() {
        return "test gateway";
    }

    @RequestMapping("/blog")
    public ResponseEntity<String> blog() {
        return communicationService.sendToBlogService("/", MessageType.GET, null, null);
    }

    @RequestMapping("/blog/post")
    public ResponseEntity<String> blogPost() {
        return communicationService.sendToBlogService("/", MessageType.POST, "Some bodyy", null);
    }

    @RequestMapping("/email")
    public String email() {
        return asyncCommunicationService.sendToEmailParserService(new ApiGatewayCommunicator(template), "ApiGateway message");
    }

    @RequestMapping("/email/queue")
    public ResponseEntity<String> emailQueue() {
        return communicationService.sendToEmailParserService("/queue", MessageType.GET, null, null);
    }

    @RequestMapping("/event")
    public String event() {
        return asyncCommunicationService.sendToEventParserService(new ApiGatewayCommunicator(template), "ApiGateway message");
    }

    @RequestMapping("/event/queue")
    public ResponseEntity<String> eventQueue() {
        return communicationService.sendToEventParserService("/queue", MessageType.GET, null, null);
    }

    @RequestMapping("/social-media")
    public ResponseEntity<String> socialMedia() {
        return communicationService.sendToSocialMediaService("/", MessageType.GET, null, null);
    }

    @RequestMapping("/social-media/queue")
    public ResponseEntity<String> socialMediaQueue() {
        return communicationService.sendToSocialMediaService("/queue", MessageType.GET, null, null);
    }

    @RequestMapping("/telemetry")
    public ResponseEntity<String> telemetry() {
        return communicationService.sendToTelemetryService("/", MessageType.GET, null, null);
    }
}
