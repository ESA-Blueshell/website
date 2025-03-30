package net.blueshell.apigateway.controllers;

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

@RestController
@RequestMapping("/email")
public class EmailParserController {
    private final ICommunicationService communicationService;
    private final IAsyncCommunicationService asyncCommunicationService;

    public EmailParserController(IAsyncCommunicationService asyncCommunicationService,
                          ICommunicationService communicationService) {

        this.asyncCommunicationService = asyncCommunicationService;
        this.communicationService = communicationService;
    }

    @GetMapping
    public String addToEmailQueue() {
        ParsedEmail email = new ParsedEmail();
        email.setPlainText("This is a test parsed email to EmailParser");
        email.setRawHTML("<html><body><h1>This is a test email</h1></body></html>");

        return asyncCommunicationService.sendToEmailParserService(email);
    }

    @GetMapping("/queue")
    public ResponseEntity<String> getEmailQueue() {
        return communicationService.sendToEmailParserService("/queue", HttpMethod.GET, String.class);
    }
}
