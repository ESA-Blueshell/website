package net.blueshell.apigateway;

import net.blueshell.common.communication.AsyncCommunicationService;
import net.blueshell.common.communication.CommunicationService;
import net.blueshell.common.communication.IAsyncCommunicationService;
import net.blueshell.common.communication.ICommunicationService;
import net.blueshell.common.communication.communicators.ApiGatewayCommunicator;
import net.blueshell.common.communication.communicators.base.ICommunicator;
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
    private final ICommunicator asyncCommunicator;

    @Autowired
    public MainController(RabbitTemplate template) {
        System.out.println("Blog Service Started");
        this.asyncCommunicator = new ApiGatewayCommunicator(template);
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
        return communicationService.sendToBlogService("/", MessageType.POST, "Some bodyy");
    }

    @RequestMapping("/email")
    public String email() {
        return asyncCommunicationService.sendToEmailParserService(asyncCommunicator, "ApiGateway message");
    }

    @RequestMapping("/email/queue")
    public ResponseEntity<String> emailQueue() {
        return communicationService.sendToEmailParserService("/queue", MessageType.GET);
    }

    @RequestMapping("/event")
    public String event() {
        return asyncCommunicationService.sendToEventParserService(asyncCommunicator, "ApiGateway message");
    }

    @RequestMapping("/event/queue")
    public ResponseEntity<String> eventQueue() {
        return communicationService.sendToEventParserService("/queue", MessageType.GET);
    }

    @RequestMapping("/social-media")
    public ResponseEntity<String> socialMedia() {
        return communicationService.sendToSocialMediaService("/", MessageType.GET);
    }

    @RequestMapping("/social-media/queue")
    public ResponseEntity<String> socialMediaQueue() {
        return communicationService.sendToSocialMediaService("/queue", MessageType.GET);
    }

    @RequestMapping("/telemetry")
    public ResponseEntity<String> telemetry() {
        return communicationService.sendToTelemetryService("/", MessageType.GET);
    }
}
