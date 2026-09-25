package net.blueshell.api.platform.config

import net.blueshell.api.security.JwtAuthFilter
import net.blueshell.api.security.JwtAuthenticationEntryPoint
import net.blueshell.api.testsupport.StandInHttpSecurity
import org.junit.jupiter.api.Test
import org.mockito.Answers
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer

/**
 * The auth chain's rules, read off a stand-in [HttpSecurity] that hands each DSL block a stand-in
 * configurer. Nothing is enforced here, which the integration suite covers; this pins what the chain
 * asks for without a Spring context.
 */
class AuthChainTest {
    private val registry: AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry =
        mock(defaultAnswer = Answers.RETURNS_DEEP_STUBS)

    private fun chain(): SecurityConfig =
        SecurityConfig(
            authenticationEntryPoint = mock<JwtAuthenticationEntryPoint>(),
            jwtAuthFilter = mock<JwtAuthFilter>(),
            securityContextRepository = mock(),
            publicAuthRateLimitFilterProvider = mock(),
            securityCorsProperties = SecurityCorsProperties(),
            openApiPublicEnabled = false,
            requireHttps = true,
            csrfCookieSameSite = "None",
        )

    @Test
    fun `lets anybody make the anonymous reads, and asks everything else to log in`() {
        val http = StandInHttpSecurity.create(registry)

        chain().authChain(http, mock())

        verify(registry).requestMatchers(HttpMethod.GET, *SecurityConfig.ANONYMOUS_READS)
        verify(registry).anyRequest()
    }
}
