package net.blueshell.api.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.config.PageableHandlerMethodArgumentResolverCustomizer;

@Configuration
class PagingConfig {

    @Bean
    PageableHandlerMethodArgumentResolverCustomizer unpagedByDefault() {
        return resolver -> resolver.setFallbackPageable(Pageable.unpaged());
    }
}

