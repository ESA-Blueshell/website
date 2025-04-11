package net.blueshell.apigateway.controllers;

import net.blueshell.common.communication.IAsyncCommunicationService;
import net.blueshell.common.communication.ICommunicationService;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/eventparser")
public class EventParserController extends SwaggerController {
    private final ICommunicationService communicationService;
    private final IAsyncCommunicationService asyncCommunicationService;

    public EventParserController(IAsyncCommunicationService asyncCommunicationService,
                          ICommunicationService communicationService) {

        this.asyncCommunicationService = asyncCommunicationService;
        this.communicationService = communicationService;
    }

    @GetMapping
    public String addToEventQueue() {
        return asyncCommunicationService.sendToEventParserService("ApiGateway message");
    }

    @GetMapping("/queue")
    public ResponseEntity<String> getEventQueue() {
        return communicationService.sendToEventParserService("/queue", HttpMethod.GET, String.class);
    }

    @Override
    protected Object sendSwaggerRequestToService() {
        return communicationService.sendToEventParserService(SWAGGER_SERVICE_URL, HttpMethod.GET, Object.class);
    }
}
