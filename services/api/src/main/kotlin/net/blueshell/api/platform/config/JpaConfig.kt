package net.blueshell.api.platform.config

import net.blueshell.api.domain.user.application.UserService
import net.blueshell.api.domain.user.persistence.User
import net.blueshell.api.infrastructure.security.SecurityUtils
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.domain.AuditorAware
import java.util.*

/**
 * JPA configuration for audit awareness.
 * Uses UserService instead of UserRepository to comply with ADR-018 (Data Ownership).
 */
@Configuration
class JpaConfig(
    private val userService: UserService
) {
    @Bean
    fun auditorAware(): AuditorAware<User> {
        return AuditorAware {
            val principal = SecurityUtils.currentPrincipal() ?: return@AuditorAware Optional.empty()
            try {
                Optional.of(userService.findById(principal.id))
            } catch (e: Exception) {
                Optional.empty()
            }
        }
    }
}
