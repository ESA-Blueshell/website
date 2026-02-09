package net.blueshell.api.platform.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableHandlerMethodArgumentResolver
import org.springframework.data.web.config.PageableHandlerMethodArgumentResolverCustomizer

@Configuration
internal class PagingConfig {
    @Bean
    fun unpagedByDefault(): PageableHandlerMethodArgumentResolverCustomizer {
        return PageableHandlerMethodArgumentResolverCustomizer { resolver: PageableHandlerMethodArgumentResolver? ->
            resolver!!.setFallbackPageable(
                Pageable.unpaged()
            )
        }
    }
}

