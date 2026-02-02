package net.blueshell.api.config

import net.blueshell.api.model.User
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.domain.AuditorAware
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContextHolder
import java.util.*
import java.util.function.Function
import java.util.function.Predicate

@Configuration
class JpaConfig {
    @Bean
    fun auditorAware(): AuditorAware<User?> {
        return AuditorAware {
            Optional.ofNullable<Authentication?>(
                SecurityContextHolder
                    .getContext()
                    .authentication
            ).filter(Predicate { obj: Authentication? -> obj!!.isAuthenticated })
                .map<User?>(Function { u: Authentication? -> u as User })
        }
    }
}

