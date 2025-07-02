package net.blueshell.api.config;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class RestClientConfig {

    /**
     * Central RestTemplate bean.
     * <p>
     * - Uses Spring’s {@link RestTemplateBuilder} so any
     *   auto-registered customisers (Jackson, timeouts, interceptors, etc.)
     *   are applied automatically.
     */
    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder.build();
    }
}

