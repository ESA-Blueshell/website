package net.blueshell.eventparser.controller;

import jakarta.validation.Valid;
import net.blueshell.common.dto.SocialDTO;
import net.blueshell.common.dto.EventDTO;
import net.blueshell.eventparser.mapper.EventMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.logging.Logger;

@RestController
public class EventParserController {

    private static final Logger logger = Logger.getLogger(EventParserController.class.getName());
    private final EventMapper eventMapper;
    private final StreamBridge streamBridge;

    @Autowired
    public EventParserController(EventMapper eventMapper, StreamBridge streamBridge) {
        this.eventMapper = eventMapper;
        this.streamBridge = streamBridge;
    }

    @PostMapping("/parse")
    public SocialDTO parseEvent(@Valid @RequestBody EventDTO eventDTO) {
        SocialDTO socialDTO = eventMapper.toSocialDto(eventDTO);
        streamBridge.send("social.post", socialDTO);
        return socialDTO;
    }
}
