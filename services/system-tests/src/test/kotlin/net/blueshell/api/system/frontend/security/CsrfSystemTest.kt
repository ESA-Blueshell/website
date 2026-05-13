package net.blueshell.api.system.frontend.security

import net.blueshell.api.system.frontend.helper.AuthHelper
import net.blueshell.api.system.frontend.helper.LoginDomainHelper
import net.blueshell.systemtests.PlaywrightTestBase
import net.blueshell.systemtests.TestEnvironment
import net.blueshell.systemtests.TestHelper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import tools.jackson.databind.ObjectMapper
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

@Tag("system")
class CsrfSystemTest : PlaywrightTestBase() {

    @Test
    fun `cross-origin state changing request without csrf token is rejected`() {
        val response = HttpClient.newHttpClient().send(
            HttpRequest.newBuilder(URI.create("${TestEnvironment.apiUrl}/auth"))
                .header("Origin", frontendUrl)
                .header("Content-Type", "application/json")
                .POST(
                    HttpRequest.BodyPublishers.ofString(
                        """{"username":"does-not-exist","password":"invalid-password"}""",
                    ),
                )
                .build(),
            HttpResponse.BodyHandlers.ofString(),
        )

        assertThat(response.statusCode()).isEqualTo(403)
    }

    @Test
    fun `frontend login flow succeeds and stores csrf cookie`() {
        val user = TestHelper.registerActivateAndPromote("MEMBER")

        val loginStatus = AuthHelper.submitLogin(page, frontendUrl, user.username, user.password)
        assertThat(loginStatus).isEqualTo(200)

        assertThat(
            page.context().cookies().any { cookie ->
                cookie.name == "XSRF-TOKEN" && cookie.value.isNotBlank()
            },
        ).isTrue()
    }

    @Test
    fun `login page flow uses csrf body token and succeeds`() {
        val user = TestHelper.registerActivateAndPromote("MEMBER")

        var csrfBodyToken: String? = null
        var csrfCookieToken: String? = null
        page.onResponse { response ->
            if (response.request().method() == "GET" && response.url().contains("/csrf")) {
                csrfBodyToken = objectMapper.readTree(response.text())["token"].asText()
                val setCookie = response.headerValue("set-cookie") ?: ""
                csrfCookieToken = CSRF_COOKIE_PATTERN.find(setCookie)?.groupValues?.get(1)
            }
        }

        page.navigate("$frontendUrl/login/")
        LoginDomainHelper.fillLoginCredentials(page, user.username, user.password)

        val authResponse = page.waitForResponse({ response ->
            response.request().method() == "POST" && response.url().contains("/auth")
        }) {
            LoginDomainHelper.clickLoginSubmit(page)
        }

        val csrfBody = csrfBodyToken ?: "<missing>"
        val csrfCookie = csrfCookieToken ?: "<missing>"
        val csrfHeaderOnAuth = authResponse.request().headers()["x-xsrf-token"] ?: "<missing>"

        assertThat(csrfBody).isNotBlank()
        assertThat(csrfCookie).isNotBlank()
        assertThat(csrfHeaderOnAuth).isNotBlank()
        assertThat(csrfBody).isNotEqualTo(csrfCookie)
        assertThat(csrfHeaderOnAuth).isEqualTo(csrfBody)

        assertThat(authResponse.status())
            .withFailMessage(
                "Expected /auth status 200 from login flow. Got %s (csrfBody=%s, csrfCookie=%s, authHeader=%s)",
                authResponse.status(),
                csrfBody,
                csrfCookie,
                csrfHeaderOnAuth,
            )
            .isEqualTo(200)
    }

    private companion object {
        val CSRF_COOKIE_PATTERN = Regex("""XSRF-TOKEN=([^;]+)""")
        val objectMapper = ObjectMapper()
    }
}
