package net.blueshell.api.platform.config

import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.client.RestTemplate

@Configuration
class RestClientConfig {
    /**
     * Central RestTemplate bean.
     * 
     * 
     * - Uses Spring’s [RestTemplateBuilder] so any
     * auto-registered customizers (Jackson, timeouts, interceptors, etc.)
     * are applied automatically.
     */
    @Bean
    fun restTemplate(builder: RestTemplateBuilder): RestTemplate? {
        return builder.build()
    }
}

