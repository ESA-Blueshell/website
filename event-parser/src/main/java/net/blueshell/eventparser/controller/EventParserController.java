package net.blueshell.eventparser.controller;

import jakarta.validation.Valid;
import net.blueshell.common.communicator.SocialMediaCommunicator;
import net.blueshell.common.dto.SocialDTO;
import net.blueshell.common.dto.event.EventDTO;
import net.blueshell.eventparser.mapper.EventMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.logging.Logger;

@RestController
public class EventParserController {

    private static final Logger logger = Logger.getLogger(EventParserController.class.getName());
    private final EventMapper eventMapper;
    private final SocialMediaCommunicator socialMediaCommunicator;

    @Autowired
    public EventParserController(EventMapper eventMapper,
                                 SocialMediaCommunicator socialMediaCommunicator) {
        this.eventMapper = eventMapper;
        this.socialMediaCommunicator = socialMediaCommunicator;
    }

    @PostMapping("/parse")
    public SocialDTO parseEvent(@Valid @RequestBody EventDTO eventDTO) {
        SocialDTO socialDTO = eventMapper.toSocialDto(eventDTO);
        logger.info("Sending Social DTO: " + socialDTO.getId() + " to Social Media Service.");
        socialMediaCommunicator.sendAsync(socialDTO);
        return socialDTO;
    }

}
