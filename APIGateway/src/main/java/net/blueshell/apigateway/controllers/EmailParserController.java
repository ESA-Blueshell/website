package net.blueshell.apigateway.controllers;

import net.blueshell.common.dto.EmailDTO;
import net.blueshell.common.communication.IAsyncCommunicationService;
import net.blueshell.common.communication.ICommunicationService;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.sql.Timestamp;
import java.time.Instant;

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
        EmailDTO email = new EmailDTO();
        email.setHtml("<html><body><h1>This is a test email</h1></body></html>");
        email.setPublishedAt(Timestamp.from(Instant.now()));

        return asyncCommunicationService.sendToEmailParserService(email);
    }

    @GetMapping("/queue")
    public ResponseEntity<String> getEmailQueue() {
        return communicationService.sendToEmailParserService("/queue", HttpMethod.GET, String.class);
    }
}
