package net.blueshell.api.platform.config

import net.blueshell.api.domain.user.persistence.User
import net.blueshell.api.domain.user.persistence.repository.UserRepository
import net.blueshell.api.infrastructure.security.SecurityUtils
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.domain.AuditorAware
import java.util.*

@Configuration
class JpaConfig(
    private val users: UserRepository
) {
    @Bean
    fun auditorAware(): AuditorAware<User> {
        return AuditorAware {
            val principal = SecurityUtils.currentPrincipal() ?: return@AuditorAware Optional.empty()
            users.findById(principal.id)
        }
    }
}
