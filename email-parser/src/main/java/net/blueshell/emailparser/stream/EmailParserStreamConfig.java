package net.blueshell.emailparser.stream;

import java.util.function.Consumer;
import net.blueshell.common.dto.EmailDTO;
import net.blueshell.common.dto.InternalBlogDTO;
import net.blueshell.emailparser.mapper.EmailMapper;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class EmailParserStreamConfig {

    private final EmailMapper emailMapper;
    private final StreamBridge streamBridge;

    public EmailParserStreamConfig(EmailMapper emailMapper, StreamBridge streamBridge) {
        this.emailMapper = emailMapper;
        this.streamBridge = streamBridge;
    }

    @Bean
    public Consumer<EmailDTO> asyncParseEmail() {
        return emailDTO -> {
            InternalBlogDTO blogDto = emailMapper.toBlogDTO(emailDTO);
            streamBridge.send("blog-out-0", blogDto);
        };
    }
}
