package net.blueshell.api.security

import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository
import org.springframework.security.web.context.SecurityContextRepository

@Configuration
class SecurityContextRepositoryConfig {
    // Request-scoped only: the auth cookie and its sign-in are the one credential, so the servlet
    // session never carries a principal from one request to the next (api ADR-030).
    @Bean
    fun securityContextRepository(): SecurityContextRepository = RequestAttributeSecurityContextRepository()

    // JwtAuthFilter is @Component, which Spring Boot would otherwise
    // auto-register as a servlet-container filter outside the security
    // chain. It must only run inside the SecurityFilterChain, where its
    // SecurityContext mutation is saved via the repository.
    @Bean
    fun jwtAuthFilterRegistration(filter: JwtAuthFilter): FilterRegistrationBean<JwtAuthFilter> =
        FilterRegistrationBean(filter).apply { isEnabled = false }
}
