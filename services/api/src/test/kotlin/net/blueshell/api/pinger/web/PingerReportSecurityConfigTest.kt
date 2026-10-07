package net.blueshell.api.pinger.web

import com.nimbusds.jose.jwk.source.JWKSource
import com.nimbusds.jose.proc.SecurityContext
import net.blueshell.api.testsupport.StandInHttpSecurity
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configurers.oauth2.server.resource.OAuth2ResourceServerConfigurer
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter

class PingerReportSecurityConfigTest {
    private val jwkSource = mock<JWKSource<SecurityContext>>()

    @Test
    fun `the chain is scoped to the report paths`() {
        val http = StandInHttpSecurity.create()

        PingerReportSecurityConfig().pingerReportFilterChain(http, jwkSource, "")

        verify(http).securityMatcher("/pinger/report/**")
    }

    @Test
    fun `the service token filter runs before the bearer filter`() {
        val http = StandInHttpSecurity.create()

        val chain = PingerReportSecurityConfig().pingerReportFilterChain(http, jwkSource, "s3cret")

        assertThat(chain).isNotNull
        verify(http).addFilterBefore(
            any<PingerServiceTokenAuthenticationFilter>(),
            eq(BearerTokenAuthenticationFilter::class.java),
        )
    }

    @Test
    fun `the jwt customizer wires the decoder and the member converter`() {
        val jwtConfigurer = mock<OAuth2ResourceServerConfigurer<HttpSecurity>.JwtConfigurer>()

        PingerReportSecurityConfig().jwtCustomizer(jwkSource).customize(jwtConfigurer)

        verify(jwtConfigurer).decoder(any())
        verify(jwtConfigurer).jwtAuthenticationConverter(any())
    }
}
