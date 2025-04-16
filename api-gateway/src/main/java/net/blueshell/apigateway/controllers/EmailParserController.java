package net.blueshell.apigateway.controllers;

import net.blueshell.common.communicator.EmailParserCommunicator;
import net.blueshell.common.dto.EmailDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.sql.Timestamp;
import java.time.Instant;

@RestController
@RequestMapping("/email")
public class EmailParserController {

    @Autowired
    private EmailParserCommunicator communicator;

    @GetMapping
    public String addToEmailQueue() {
        EmailDTO email = new EmailDTO();
        email.setHtml("<html><body><h1>This is a test email</h1></body></html>");
        email.setPublishedAt(Timestamp.from(Instant.now()));

        return communicator.sendAsync(email);
    }

    @GetMapping("/queue")
    public String getEmailQueue() {
        return communicator.sendSync("/queue", HttpMethod.GET, String.class);
    }
}
