package net.blueshell.socialmediaservice.stream;

import lombok.extern.slf4j.Slf4j;
import net.blueshell.common.dto.SocialDTO;
import net.blueshell.socialmediaservice.service.SocialMediaService;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.function.Consumer;

@Configuration
@Slf4j
public class SocialMediaStreamConfig {

    private final SocialMediaService socialMediaService;

    public SocialMediaStreamConfig(StreamBridge streamBridge, SocialMediaService socialMediaService) {
        this.socialMediaService = socialMediaService;
    }

    @Bean
    public Consumer<SocialDTO> asyncParseEvent() {
        return socialDTO -> {
            log.info("Publishing socialDTO: {} to Social Media.", socialDTO.getId());
            socialMediaService.distribute(socialDTO);
        };
    }
}

