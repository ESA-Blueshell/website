package net.blueshell.apigateway.controllers;

import net.blueshell.common.communication.AsyncCommunicationService;
import net.blueshell.common.communication.CommunicationService;
import net.blueshell.common.communication.IAsyncCommunicationService;
import net.blueshell.common.communication.ICommunicationService;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/event")
public class EventParserController {
    private final static ICommunicationService communicationService = new CommunicationService();
    private final IAsyncCommunicationService asyncCommunicationService;

    @Autowired
    public EventParserController(RabbitTemplate template) {
        this.asyncCommunicationService = new AsyncCommunicationService(template);
    }

    @GetMapping
    public String addToEventQueue() {
        return asyncCommunicationService.sendToEventParserService("ApiGateway message");
    }

    @GetMapping("/queue")
    public ResponseEntity<String> getEventQueue() {
        return communicationService.sendToEventParserService("/queue", HttpMethod.GET, String.class);
    }
}
