package net.blueshell.eventparser.stream;

import java.util.function.Consumer;

import lombok.extern.slf4j.Slf4j;
import net.blueshell.common.dto.EventDTO;
import net.blueshell.common.dto.SocialDTO;
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
    public Consumer<EventDTO> asyncParseEvent() {
        return eventDTO -> {
            SocialDTO socialDTO = eventMapper.toSocialDto(eventDTO);
            log.info("Sending Social DTO: {} to Social Media Service.", socialDTO.getId());
            streamBridge.send("social-out-0", socialDTO);
        };
    }
}

