package net.blueshell.api.pinger.web

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockFilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.core.context.SecurityContextHolder

class PingerServiceTokenAuthenticationFilterTest {
    @AfterEach
    fun tearDown() {
        SecurityContextHolder.clearContext()
    }

    private fun run(
        configuredToken: String,
        presented: String?,
    ): MockFilterChain {
        val request = MockHttpServletRequest("GET", "/pinger/report/whoami")
        presented?.let { request.addHeader("X-Pinger-Service-Token", it) }
        val chain = MockFilterChain()
        PingerServiceTokenAuthenticationFilter(configuredToken).doFilter(request, MockHttpServletResponse(), chain)
        return chain
    }

    @Test
    fun `the right token signs the request in as the service`() {
        run("s3cret", "s3cret")

        val authentication = SecurityContextHolder.getContext().authentication
        assertThat(authentication).isNotNull
        assertThat(authentication!!.name).isEqualTo("sitecie")
        assertThat(authentication.authorities.map { it.authority }).containsExactly("SITECIE")
    }

    @Test
    fun `a wrong token leaves the request unauthenticated`() {
        val chain = run("s3cret", "guess")

        assertThat(SecurityContextHolder.getContext().authentication).isNull()
        assertThat(chain.request).isNotNull()
    }

    @Test
    fun `an empty configured token turns the header off`() {
        run("", "")

        assertThat(SecurityContextHolder.getContext().authentication).isNull()
    }

    @Test
    fun `a request without the header passes straight through`() {
        val chain = run("s3cret", null)

        assertThat(SecurityContextHolder.getContext().authentication).isNull()
        assertThat(chain.request).isNotNull()
    }
}
