package net.blueshell.api.oidc.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest

class PublicClientRefreshConverterTest {
    private val converter = PublicClientRefreshConverter()

    private fun refresh(path: String) =
        MockHttpServletRequest("POST", path).apply {
            addParameter("grant_type", "refresh_token")
            addParameter("client_id", "pinger-app")
            addParameter("refresh_token", "r1")
        }

    @Test
    fun `a refresh on the token endpoint authenticates the public client`() {
        assertThat(converter.convert(refresh("/oauth2/token"))?.principal).isEqualTo("pinger-app")
    }

    @Test
    fun `a client_id alone never authenticates on introspection or revocation`() {
        assertThat(converter.convert(refresh("/oauth2/introspect"))).isNull()
        assertThat(converter.convert(refresh("/oauth2/revoke"))).isNull()
    }
}
