package net.blueshell.socialmediaservice.controller;

import net.blueshell.dto.SocialDTO;
import net.blueshell.socialmediaservice.service.SocialMediaService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SocialMediaController {

    private final Logger logger = LoggerFactory.getLogger(SocialMediaController.class);
    private final SocialMediaService socialMediaService;

    public SocialMediaController(SocialMediaService socialMediaService) {
        this.socialMediaService = socialMediaService;
    }
}
