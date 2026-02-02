package net.blueshell.api.config;

import net.blueshell.api.model.User;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.security.core.Authentication;

import java.util.Optional;

@Configuration
public class JpaConfig {

    @Bean
    public AuditorAware<User> auditorAware() {
        return () -> Optional.ofNullable(
                        org.springframework.security.core.context.SecurityContextHolder
                                .getContext()
                                .getAuthentication()
                ).filter(Authentication::isAuthenticated)
                .map((u) -> (User) u);
    }
}

