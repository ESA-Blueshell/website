package net.blueshell.apigateway.controllers;

import net.blueshell.common.communication.AsyncCommunicationService;
import net.blueshell.common.communication.CommunicationService;
import net.blueshell.common.communication.IAsyncCommunicationService;
import net.blueshell.common.communication.ICommunicationService;
import net.blueshell.common.communication.communicators.base.MessageType;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController("email")
public class EmailParserController {
    private final static ICommunicationService communicationService = new CommunicationService();
    private final IAsyncCommunicationService asyncCommunicationService;

    @Autowired
    public EmailParserController(RabbitTemplate template) {
        this.asyncCommunicationService = new AsyncCommunicationService(template);
    }

    @GetMapping("/")
    public String addToEmailQueue() {
        return asyncCommunicationService.sendToEmailParserService("ApiGateway message");
    }

    @GetMapping("/queue")
    public ResponseEntity<String> getEmailQueue() {
        return communicationService.sendToEmailParserService("/queue", MessageType.GET, String.class);
    }
}
