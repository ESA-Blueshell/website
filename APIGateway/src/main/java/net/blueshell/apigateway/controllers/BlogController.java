package net.blueshell.apigateway.controllers;

import net.blueshell.common.communication.AsyncCommunicationService;
import net.blueshell.common.communication.CommunicationService;
import net.blueshell.common.communication.IAsyncCommunicationService;
import net.blueshell.common.communication.ICommunicationService;
import net.blueshell.common.communication.communicators.base.MessageType;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController("queue")
public class BlogController {
    private final static ICommunicationService communicationService = new CommunicationService();
    private final IAsyncCommunicationService asyncCommunicationService;

    public BlogController(RabbitTemplate rabbitTemplate) {
        this.asyncCommunicationService = new AsyncCommunicationService(rabbitTemplate);
    }

    @GetMapping("/")
    public ResponseEntity<String> getBlog() {
        return communicationService.sendToBlogService("/", MessageType.GET, String.class);
    }

    @GetMapping("/queue")
    public ResponseEntity<String> getBlogQueue() {
        return communicationService.sendToBlogService("/queue", MessageType.GET, String.class);
    }

    @PostMapping("/queue")
    public String addToBlogQueue(@RequestParam String body) {
        return asyncCommunicationService.sendToBlogService(body);
    }
}
