package net.blueshell.eventparser.stream;

import java.util.function.Consumer;

import lombok.extern.slf4j.Slf4j;
import net.blueshell.dto.EventDTO;
import net.blueshell.dto.SocialDTO;
import net.blueshell.eventparser.mapper.EventMapper;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@Slf4j
public class EventParserStreamConfig {

    private final EventMapper eventMapper;
    private final StreamBridge streamBridge;

    public EventParserStreamConfig(EventMapper eventMapper, StreamBridge streamBridge) {
        this.eventMapper = eventMapper;
        this.streamBridge = streamBridge;
    }

    @Bean
    public Consumer<EventDTO> parseEvent() {
        return eventDTO -> {
            SocialDTO socialDto = eventMapper.toSocialDto(eventDTO);
            streamBridge.send("social.events", socialDto);
        };
    }
}

