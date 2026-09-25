package net.blueshell.api.oidc.domain

import com.nimbusds.jose.jwk.source.JWKSource
import com.nimbusds.jose.proc.SecurityContext
import net.blueshell.api.security.JwtAuthFilter
import net.blueshell.api.security.SignIns
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.annotation.Order
import org.springframework.security.config.Customizer
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configurers.oauth2.server.authorization.OAuth2AuthorizationServerConfigurer
import org.springframework.security.oauth2.server.authorization.InMemoryOAuth2AuthorizationConsentService
import org.springframework.security.oauth2.server.authorization.InMemoryOAuth2AuthorizationService
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsentService
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.context.SecurityContextHolderFilter
import org.springframework.security.web.context.SecurityContextRepository
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

@Configuration
class AuthorizationServerConfig {
    @Bean
    @Order(1)
    // jwkSource and tokenCustomizer are declared so the container resolves them
    // before this chain is built; the body reaches them through the shared
    // authorization-server configurer rather than by name.
    @Suppress("UnusedParameter")
    fun authorizationServerFilterChain(
        http: HttpSecurity,
        jwkSource: JWKSource<SecurityContext>,
        tokenCustomizer: OAuth2TokenCustomizer<JwtEncodingContext>,
        jwtAuthFilter: JwtAuthFilter,
        securityContextRepository: SecurityContextRepository,
        signIns: SignIns,
    ): SecurityFilterChain {
        val authServerConfigurer = OAuth2AuthorizationServerConfigurer()

        authServerConfigurer
            .oidc(Customizer.withDefaults())

        http
            .securityMatcher(authServerConfigurer.endpointsMatcher)
            .with(authServerConfigurer) {}
            .authorizeHttpRequests { it.anyRequest().authenticated() }
            .securityContext { it.securityContextRepository(securityContextRepository) }
            // Must run before Spring SAS's OAuth2AuthorizationCodeRequestValidatingFilter
            // (positioned before AbstractPreAuthenticatedProcessingFilter), otherwise the
            // validating filter snapshots SecurityContext while it's still anonymous and
            // the endpoint filter later issues `?error=invalid_request&error_description=
            // OAuth 2.0 Parameter: principal` to the client's redirect_uri.
            .addFilterAfter(jwtAuthFilter, SecurityContextHolderFilter::class.java)
            .addFilterAfter(DownstreamClientAuthorizationFilter(signIns), JwtAuthFilter::class.java)
            .exceptionHandling {
                it.authenticationEntryPoint(loginRedirectEntryPoint())
            }.csrf { it.ignoringRequestMatchers(authServerConfigurer.endpointsMatcher) }

        return http.build()
    }

    private fun loginRedirectEntryPoint(): AuthenticationEntryPoint =
        AuthenticationEntryPoint { request, response, _ ->
            val target = LoginRedirectTarget.forRequest(request.requestURI, request::getParameter)
            response.sendRedirect("/login?redirect=${URLEncoder.encode(target, StandardCharsets.UTF_8)}")
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
        @Value("\${auth.issuer:https://esa-blueshell.nl/api}") issuer: String,
    ): AuthorizationServerSettings =
        AuthorizationServerSettings
            .builder()
            .issuer(issuer)
            .build()

    // In-memory rather than JDBC: UserPrincipal isn't in the Jackson allowlist
    // SecurityJackson2Modules ships, so JdbcOAuth2AuthorizationService can't
    // round-trip the principal. Replicas=1 means in-memory is fine; revisit
    // if/when we scale out.
    @Bean
    fun authorizationService(): OAuth2AuthorizationService = InMemoryOAuth2AuthorizationService()

    @Bean
    fun authorizationConsentService(): OAuth2AuthorizationConsentService = InMemoryOAuth2AuthorizationConsentService()
}
