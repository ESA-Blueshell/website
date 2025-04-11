package net.blueshell.apigateway.controllers;

import net.blueshell.common.communication.IAsyncCommunicationService;
import net.blueshell.common.communication.ICommunicationService;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/socialmedia")
public class SocialMediaController extends SwaggerController {
    private final ICommunicationService communicationService;
    private final IAsyncCommunicationService asyncCommunicationService;

    public SocialMediaController(IAsyncCommunicationService asyncCommunicationService,
                          ICommunicationService communicationService) {

        this.asyncCommunicationService = asyncCommunicationService;
        this.communicationService = communicationService;
    }

    @GetMapping
    public ResponseEntity<Boolean> getSocialMedia() {
        return communicationService.sendToSocialMediaService("/", HttpMethod.GET, Boolean.class);
    }

    @GetMapping("/queue")
    public ResponseEntity<String> getSocialMediaQueue() {
        return communicationService.sendToSocialMediaService("/queue", HttpMethod.GET, String.class);
    }

    @PostMapping("/queue")
    public String addToSocialMediaQueue(@RequestBody String body) {
        return asyncCommunicationService.sendToSocialMediaService(body);
    }

    @Override
    protected Object sendSwaggerRequestToService() {
        return communicationService.sendToSocialMediaService(SWAGGER_SERVICE_URL, HttpMethod.GET, Object.class);
    }
}
