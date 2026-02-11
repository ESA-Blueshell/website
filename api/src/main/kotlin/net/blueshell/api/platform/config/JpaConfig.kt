package net.blueshell.api.platform.config

import net.blueshell.api.domain.user.persistence.User
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.domain.AuditorAware
import org.springframework.security.core.context.SecurityContextHolder
import java.util.*

@Configuration
class JpaConfig {
    @Bean
    fun auditorAware(): AuditorAware<User> {
        return AuditorAware {
            Optional.ofNullable(SecurityContextHolder.getContext().authentication)
                .filter { it.isAuthenticated }
                .map { it.principal as User }
        }
    }
}

