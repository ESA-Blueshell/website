package net.blueshell.api.system.frontend.security

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

    private companion object {
        const val DEFAULT_PASSWORD = "Password123!"
    }
}
