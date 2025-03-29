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
import org.springframework.web.bind.annotation.RestController;

@RestController("event")
public class EventParserController {
    private final static ICommunicationService communicationService = new CommunicationService();
    private final static IAsyncCommunicationService asyncCommunicationService = new AsyncCommunicationService();
    private final ICommunicator asyncCommunicator;

    @Autowired
    public EventParserController(RabbitTemplate template) {
        this.asyncCommunicator = new ApiGatewayCommunicator(template);
    }

    @GetMapping("/")
    public String addToEventQueue() {
        return asyncCommunicationService.sendToEventParserService(asyncCommunicator, "ApiGateway message");
    }

    @GetMapping("/queue")
    public ResponseEntity<String> getEventQueue() {
        return communicationService.sendToEventParserService("/queue", MessageType.GET, String.class);
    }
}
