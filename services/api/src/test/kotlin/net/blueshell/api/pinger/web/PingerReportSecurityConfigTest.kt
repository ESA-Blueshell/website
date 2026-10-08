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
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter
import java.time.Instant

class PingerReportSecurityConfigTest {
    private val jwkSource = mock<JWKSource<SecurityContext>>()

    private fun jwtWithAudience(audience: List<String>): Jwt =
        Jwt
            .withTokenValue("token")
            .header("alg", "none")
            .subject("1")
            .audience(audience)
            .issuedAt(Instant.EPOCH)
            .expiresAt(Instant.EPOCH.plusSeconds(60))
            .build()

    @Test
    fun `the audience validator accepts a pinger-app token and rejects the rest`() {
        val validator = PingerReportSecurityConfig().audienceValidator()

        assertThat(validator.validate(jwtWithAudience(listOf("pinger-app"))).hasErrors()).isFalse()
        assertThat(validator.validate(jwtWithAudience(listOf("headlamp"))).hasErrors()).isTrue()
        assertThat(validator.validate(jwtWithAudience(emptyList())).hasErrors()).isTrue()
    }

    @Test
    fun `the chain is scoped to the report paths`() {
        val http = StandInHttpSecurity.create()

        PingerReportSecurityConfig().pingerReportFilterChain(http, jwkSource, "", "https://esa-blueshell.nl/api")

        verify(http).securityMatcher("/pinger/report/**")
    }

    @Test
    fun `the service token filter runs before the bearer filter`() {
        val http = StandInHttpSecurity.create()

        val chain = PingerReportSecurityConfig().pingerReportFilterChain(http, jwkSource, "s3cret", "https://esa-blueshell.nl/api")

        assertThat(chain).isNotNull
        verify(http).addFilterBefore(
            any<PingerServiceTokenAuthenticationFilter>(),
            eq(BearerTokenAuthenticationFilter::class.java),
        )
    }

    @Test
    fun `the jwt customizer wires the decoder and the member converter`() {
        val jwtConfigurer = mock<OAuth2ResourceServerConfigurer<HttpSecurity>.JwtConfigurer>()

        PingerReportSecurityConfig().jwtCustomizer(jwkSource, "https://esa-blueshell.nl/api").customize(jwtConfigurer)

        verify(jwtConfigurer).decoder(any())
        verify(jwtConfigurer).jwtAuthenticationConverter(any())
    }
}
