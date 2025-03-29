package net.blueshell.apigateway.controllers;

import net.blueshell.common.Image;
import net.blueshell.common.ParsedEmail;
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

import java.util.ArrayList;

@RestController
@RequestMapping("/email")
public class EmailParserController {
    private final static ICommunicationService communicationService = new CommunicationService();
    private final IAsyncCommunicationService asyncCommunicationService;

    @Autowired
    public EmailParserController(RabbitTemplate template) {
        this.asyncCommunicationService = new AsyncCommunicationService(template);
    }

    @GetMapping
    public String addToEmailQueue() {
        return asyncCommunicationService.sendToEmailParserService("ApiGateway message");
    }

    @GetMapping("/queue")
    public ResponseEntity<String> getEmailQueue() {
        ParsedEmail email = new ParsedEmail();
        email.setPlainText("This is a test parsed email");
        email.setRawHTML("<html><body><h1>This is a test email</h1></body></html>");

        return communicationService.sendToEmailParserService("/queue", HttpMethod.GET, email, String.class);
    }
}
