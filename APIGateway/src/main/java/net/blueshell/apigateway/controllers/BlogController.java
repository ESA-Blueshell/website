package net.blueshell.apigateway.controllers;

import net.blueshell.common.communication.IAsyncCommunicationService;
import net.blueshell.common.communication.ICommunicationService;
import net.blueshell.common.communication.communicators.Communicators;
import net.blueshell.common.dto.BlogDTO;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.ModelAndView;

import java.sql.Timestamp;
import java.time.Instant;

@RestController
@RequestMapping("/blog")
public class BlogController extends SwaggerController {
    private final ICommunicationService communicationService;
    private final IAsyncCommunicationService asyncCommunicationService;

    public BlogController(IAsyncCommunicationService asyncCommunicationService,
                          ICommunicationService communicationService) {

        this.asyncCommunicationService = asyncCommunicationService;
        this.communicationService = communicationService;
    }

    @GetMapping
    public ResponseEntity<String> getBlog() {
        return communicationService.sendToBlogService("/", HttpMethod.GET, String.class);
    }

    @GetMapping("/queue")
    public ResponseEntity<String> getBlogQueue() {
        return communicationService.sendToBlogService("/queue", HttpMethod.GET, String.class);
    }

    @GetMapping("/new")
    public String addToBlogQueue() {
        BlogDTO dto = new BlogDTO();
        dto.setHtml("<html><body><h1>This is a test email</h1></body></html>");
        dto.setPublishedAt(Timestamp.from(Instant.now()));
        System.out.println("Sending blog to email parser service asynchronously");

        return asyncCommunicationService.sendToBlogService(dto);
    }

    @Override
    protected Object sendSwaggerRequestToService() {
        return communicationService.sendToBlogService(SWAGGER_SERVICE_URL, HttpMethod.GET, Object.class);
    }
}
