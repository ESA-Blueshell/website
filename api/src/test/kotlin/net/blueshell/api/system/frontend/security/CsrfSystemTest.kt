package net.blueshell.api.system.frontend.security

import com.fasterxml.jackson.databind.ObjectMapper
import com.microsoft.playwright.Page
import com.microsoft.playwright.options.AriaRole
import net.blueshell.api.factory.user.persistence.UserFactory
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.system.frontend.FrontendSystemTestBase
import net.blueshell.api.system.frontend.helper.AuthHelper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value

@Tag("system")
class CsrfSystemTest : FrontendSystemTestBase() {

    @Autowired
    private lateinit var userFactory: UserFactory

    @Value("\${app.url}")
    private lateinit var appUrl: String

    @Test
    fun `cross-origin state changing request without csrf token is rejected`() {
        withPage { page ->
            page.navigate(frontendUrl)

            val status = (page.evaluate(
                """
                async (baseUrl) => {
                  const response = await fetch(`${'$'}{baseUrl}/auth`, {
                    method: 'POST',
                    headers: {'Content-Type': 'application/json'},
                    body: JSON.stringify({username: 'does-not-exist', password: 'invalid-password'})
                  })
                  return response.status
                }
                """.trimIndent(),
                appUrl
            ) as Number).toInt()

            assertThat(status).isEqualTo(403)
        }
    }

    @Test
    fun `frontend login flow succeeds and stores csrf cookie`() {
        val user = userFactory.createUserWithRole(Role.MEMBER, enabled = true)

        withPage { page ->
            val loginStatus = AuthHelper.submitLogin(page, frontendUrl, user.username, DEFAULT_PASSWORD)
            assertThat(loginStatus).isEqualTo(200)

            assertThat(
                page.context().cookies().any { cookie ->
                    cookie.name == "XSRF-TOKEN" && cookie.value.isNotBlank()
                }
            ).isTrue()
        }
    }

    @Test
    fun `login page flow uses csrf body token and succeeds`() {
        val user = userFactory.createUserWithRole(Role.MEMBER, enabled = true)

        withPage { page ->
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
            page.getByLabel("Username").fill(user.username)
            page.getByRole(
                AriaRole.TEXTBOX,
                Page.GetByRoleOptions().setName("Password")
                ).fill(DEFAULT_PASSWORD)

            val authResponse = page.waitForResponse({ response ->
                response.request().method() == "POST" && response.url().contains("/auth")
            }) {
                page.getByRole(
                    AriaRole.BUTTON,
                    Page.GetByRoleOptions().setName("Login")
                ).click()
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
                    csrfHeaderOnAuth
                )
                .isEqualTo(200)
        }
    }

    private companion object {
        const val DEFAULT_PASSWORD = "Password123!"
        val CSRF_COOKIE_PATTERN = Regex("""XSRF-TOKEN=([^;]+)""")
        val objectMapper = ObjectMapper()
    }
}
