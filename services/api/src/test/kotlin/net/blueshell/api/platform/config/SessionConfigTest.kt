package net.blueshell.api.platform.config

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.session.web.http.CookieSerializer.CookieValue
import java.time.Duration

class SessionConfigTest {

    private fun serializerFor(domain: String) =
        SessionConfig(
            cookieName = "SESSION",
            cookieDomain = domain,
            sameSite = "None",
            requireHttps = true,
            sessionTimeout = Duration.ofDays(30),
        ).cookieSerializer()

    @Test
    fun `strips a leading dot from the cookie domain so Spring Session accepts it`() {
        val request = MockHttpServletRequest().apply { isSecure = true }
        val response = MockHttpServletResponse()

        // `.esa-blueshell.nl` made DefaultCookieSerializer throw on every
        // session commit (Invalid cookie domain), 500-ing the whole API.
        serializerFor(".esa-blueshell.nl").writeCookieValue(CookieValue(request, response, "abc123"))

        val setCookie = response.getHeader("Set-Cookie")
        assertThat(setCookie).contains("Domain=esa-blueshell.nl")
        assertThat(setCookie).doesNotContain("Domain=.esa-blueshell.nl")
    }

    @Test
    fun `leaves an already-dotless domain unchanged`() {
        val request = MockHttpServletRequest().apply { isSecure = true }
        val response = MockHttpServletResponse()

        serializerFor("esa-blueshell.nl").writeCookieValue(CookieValue(request, response, "abc123"))

        assertThat(response.getHeader("Set-Cookie")).contains("Domain=esa-blueshell.nl")
    }
}
