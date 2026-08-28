package net.blueshell.api.security

import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.web.context.DelegatingSecurityContextRepository
import org.springframework.security.web.context.HttpSessionSecurityContextRepository
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository
import org.springframework.security.web.context.SecurityContextRepository

@Configuration
class SecurityContextRepositoryConfig {

    @Bean
    fun securityContextRepository(): SecurityContextRepository =
        DelegatingSecurityContextRepository(
            RequestAttributeSecurityContextRepository(),
            HttpSessionSecurityContextRepository(),
        )

    // JwtAuthFilter is @Component, which Spring Boot would otherwise
    // auto-register as a servlet-container filter outside the security
    // chain. It must only run inside the SecurityFilterChain, where its
    // SecurityContext mutation is saved via the repository.
    @Bean
    fun jwtAuthFilterRegistration(filter: JwtAuthFilter): FilterRegistrationBean<JwtAuthFilter> =
        FilterRegistrationBean(filter).apply { isEnabled = false }
}
