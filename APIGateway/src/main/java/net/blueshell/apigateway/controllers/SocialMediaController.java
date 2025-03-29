package net.blueshell.apigateway.controllers;

import net.blueshell.common.communication.AsyncCommunicationService;
import net.blueshell.common.communication.CommunicationService;
import net.blueshell.common.communication.IAsyncCommunicationService;
import net.blueshell.common.communication.ICommunicationService;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/social-media")
public class SocialMediaController {
    private final static ICommunicationService communicationService = new CommunicationService();
    private final IAsyncCommunicationService asyncCommunicationService;
    @Autowired

    public SocialMediaController(RabbitTemplate template) {
        this.asyncCommunicationService = new AsyncCommunicationService(template);
    }
    @GetMapping
    public ResponseEntity<String> getSocialMedia() {
        return communicationService.sendToSocialMediaService("/", HttpMethod.GET, String.class);
    }

    @GetMapping("/queue")
    public ResponseEntity<String> getSocialMediaQueue() {
        return communicationService.sendToSocialMediaService("/queue", HttpMethod.GET, String.class);
    }

    @PostMapping("/queue")
    public String addToSocialMediaQueue(@RequestBody String body) {
        return asyncCommunicationService.sendToSocialMediaService(body);
    }
}
