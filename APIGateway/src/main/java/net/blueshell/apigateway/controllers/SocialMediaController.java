package net.blueshell.apigateway.controllers;

import net.blueshell.common.communicator.SocialMediaCommunicator;
import net.blueshell.common.dto.SocialDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/social-media")
public class SocialMediaController {

    @Autowired
    private SocialMediaCommunicator communicator;

    @GetMapping
    public SocialDTO getSocialMedia() {
        return communicator.sendSync("/", HttpMethod.GET, SocialDTO.class);
    }

    @GetMapping("/queue")
    public String getSocialMediaQueue() {
        return communicator.sendSync("/queue", HttpMethod.GET, String.class);
    }

    @PostMapping("/queue")
    public String addToSocialMediaQueue(@RequestBody String body) {
        return communicator.sendAsync(body);
    }
}
