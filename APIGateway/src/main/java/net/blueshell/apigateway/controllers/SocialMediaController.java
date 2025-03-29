package net.blueshell.apigateway.controllers;

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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController("social-media")
public class SocialMediaController {
    private final static ICommunicationService communicationService = new CommunicationService();
    private final static IAsyncCommunicationService asyncCommunicationService = new AsyncCommunicationService();
    private final ICommunicator asyncCommunicator;

    @Autowired
    public SocialMediaController(RabbitTemplate template) {
        this.asyncCommunicator = new ApiGatewayCommunicator(template);
    }
    @GetMapping("/")
    public ResponseEntity<String> getSocialMedia() {
        return communicationService.sendToSocialMediaService("/", MessageType.GET, String.class);
    }

    @GetMapping("/queue")
    public ResponseEntity<String> getSocialMediaQueue() {
        return communicationService.sendToSocialMediaService("/queue", MessageType.GET, String.class);
    }

    @PostMapping("/queue")
    public String addToSocialMediaQueue(@RequestBody String body) {
        return asyncCommunicationService.sendToSocialMediaService(asyncCommunicator, body);
    }
}
