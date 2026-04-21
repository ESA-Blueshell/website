package net.blueshell.api.platform.oidc

import com.nimbusds.jose.jwk.source.JWKSource
import com.nimbusds.jose.proc.SecurityContext
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.annotation.Order
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.config.Customizer
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationConsentService
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationService
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsentService
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository
import org.springframework.security.config.annotation.web.configurers.oauth2.server.authorization.OAuth2AuthorizationServerConfigurer
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint

@Configuration
class AuthorizationServerConfig {

    @Bean
    @Order(1)
    fun authorizationServerFilterChain(
        http: HttpSecurity,
        jwkSource: JWKSource<SecurityContext>,
        tokenCustomizer: OAuth2TokenCustomizer<JwtEncodingContext>,
    ): SecurityFilterChain {
        val authServerConfigurer = OAuth2AuthorizationServerConfigurer()

        authServerConfigurer
            .oidc(Customizer.withDefaults())

        http
            .securityMatcher(authServerConfigurer.endpointsMatcher)
            .with(authServerConfigurer) {}
            .authorizeHttpRequests { it.anyRequest().authenticated() }
            .exceptionHandling {
                it.authenticationEntryPoint(LoginUrlAuthenticationEntryPoint("/login"))
            }
            .csrf { it.ignoringRequestMatchers(authServerConfigurer.endpointsMatcher) }

        return http.build()
    }

    @Bean
    @Order(2)
    fun loginFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .securityMatcher("/login", "/login/**")
            .authorizeHttpRequests { it.anyRequest().permitAll() }
            .formLogin(Customizer.withDefaults())
        return http.build()
    }

    @Bean
    fun authorizationServerSettings(
        @Value("\${auth.issuer:https://auth.v2.esa-blueshell.nl}") issuer: String,
    ): AuthorizationServerSettings {
        return AuthorizationServerSettings.builder()
            .issuer(issuer)
            .build()
    }

    @Bean
    fun authorizationService(
        jdbcTemplate: JdbcTemplate,
        registeredClientRepository: RegisteredClientRepository,
    ): OAuth2AuthorizationService {
        return JdbcOAuth2AuthorizationService(jdbcTemplate, registeredClientRepository)
    }

    @Bean
    fun authorizationConsentService(
        jdbcTemplate: JdbcTemplate,
        registeredClientRepository: RegisteredClientRepository,
    ): OAuth2AuthorizationConsentService {
        return JdbcOAuth2AuthorizationConsentService(jdbcTemplate, registeredClientRepository)
    }
}
