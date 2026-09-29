package net.blueshell.api.platform.config

import net.blueshell.api.testsupport.UserTestSupport
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.RequestPostProcessor
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/** Where production requires HTTPS, a plain request is sent to HTTPS on whatever port it named. */
@SpringBootTest(properties = ["app.security.require-https=true"])
class HttpsRedirectIT : UserTestSupport() {
    private fun arrivingOn(
        scheme: String,
        port: Int,
    ) = RequestPostProcessor { request ->
        request.scheme = scheme
        request.isSecure = scheme == "https"
        request.serverName = "esa-blueshell.nl"
        request.serverPort = port
        request
    }

    @Test
    fun `a plain request a proxy forwards on port 443 is sent to HTTPS rather than failing`() {
        mvc
            .perform(get("/health").with(arrivingOn("http", 443)))
            .andExpect(status().isFound)
            // The filter names a port it mapped; :443 is where HTTPS is anyway.
            .andExpect(redirectedUrl("https://esa-blueshell.nl:443/health"))
    }

    @Test
    fun `a plain request on port 80 is sent to HTTPS on 443`() {
        mvc
            .perform(get("/health").with(arrivingOn("http", 80)))
            .andExpect(status().isFound)
            .andExpect(redirectedUrl("https://esa-blueshell.nl/health"))
    }

    @Test
    fun `a request that came over HTTPS is answered`() {
        mvc
            .perform(get("/health").with(arrivingOn("https", 443)))
            .andExpect(status().isOk)
    }
}
