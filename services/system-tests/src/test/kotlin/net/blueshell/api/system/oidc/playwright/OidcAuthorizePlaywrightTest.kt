package net.blueshell.api.system.oidc.playwright

import com.microsoft.playwright.Browser
import com.microsoft.playwright.BrowserType
import com.microsoft.playwright.Playwright
import com.microsoft.playwright.options.RequestOptions
import net.blueshell.api.ApiApplication
import net.blueshell.api.config.TestCleanUpListener
import net.blueshell.api.factory.user.persistence.UserFactory
import net.blueshell.api.infrastructure.security.JwtTokenGenerator
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.system.oidc.OidcTestHelper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestExecutionListeners
import java.util.stream.Stream

/**
 * Browser-driven smoke for /oauth2/authorize. Verifies that a real
 * HTTP client following Spring SAS's redirect chain ends up at the
 * registered `redirect_uri` with a `code=` and the original `state`
 * preserved — the same property OidcTokenClaimsSystemTest checks via
 * a raw HttpClient, but exercised through Playwright's APIRequest
 * (which is the same HTTP stack used for the rest of the system tests).
 *
 * We use APIRequest rather than a Page navigation because the
 * registered redirect_uri (https://headlamp.esa-blueshell.nl/...) is a
 * production hostname; a Page would resolve DNS to it and hang. Stays
 * inside the embedded server's response chain.
 *
 * Mirrors personal-stack's RabbitMqOidcPlaywrightTest pattern (Playwright
 * driving the redirect chain) without requiring a live downstream
 * service.
 */
@Tag("system")
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestExecutionListeners(
    listeners = [TestCleanUpListener::class],
    mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS
)
@SpringBootTest(
    classes = [ApiApplication::class],
    webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT,
    properties = ["server.port=8080", "app.jobs.auto-dispatch=true"]
)
class OidcAuthorizePlaywrightTest {

    @Autowired
    private lateinit var userFactory: UserFactory

    @Autowired
    private lateinit var tokenGenerator: JwtTokenGenerator

    private val baseUrl = "http://localhost:8080"

    private lateinit var playwright: Playwright
    private lateinit var browser: Browser

    @BeforeAll
    fun launchBrowser() {
        playwright = Playwright.create()
        browser = playwright.chromium().launch(BrowserType.LaunchOptions().setHeadless(true))
    }

    @AfterAll
    fun closeBrowser() {
        browser.close()
        playwright.close()
    }

    companion object {
        @JvmStatic
        fun clients(): Stream<Arguments> = Stream.of(
            Arguments.of(
                "headlamp",
                "https://headlamp.esa-blueshell.nl/oidc-callback",
                /* withPkce = */ true,
            ),
            Arguments.of(
                "vault",
                "https://vault.esa-blueshell.nl/ui/vault/auth/oidc/oidc/callback",
                /* withPkce = */ false,
            ),
        )
    }

    @ParameterizedTest(name = "{0}: admin OIDC authorize chain ends at redirect_uri with code")
    @MethodSource("clients")
    fun adminAuthorizeReachesCallback(clientId: String, redirect: String, withPkce: Boolean) {
        val admin = userFactory.createUserWithRole(Role.ADMIN)
        val token = tokenGenerator.generateToken(admin.username)
        val pkce = if (withPkce) OidcTestHelper.newPkce() else null

        val params = buildList {
            add("response_type=code")
            add("client_id=$clientId")
            add("scope=${enc("openid profile email groups")}")
            add("redirect_uri=${enc(redirect)}")
            add("state=pw-state")
            if (pkce != null) {
                add("code_challenge=${pkce.challenge}")
                add("code_challenge_method=S256")
            }
        }.joinToString("&")

        val context = browser.newContext()
        try {
            val api = context.request()
            val response = api.get(
                "$baseUrl/oauth2/authorize?$params",
                RequestOptions.create()
                    .setHeader("Cookie", "BSH_AUTH=$token")
                    .setHeader("Accept", "text/html")
                    .setMaxRedirects(0),
            )
            assertThat(response.status()).isEqualTo(302)
            val location = response.headers()["location"] ?: error("No Location header on authorize 302")
            assertThat(location).startsWith(redirect)
            assertThat(OidcTestHelper.queryParam(location, "code")).isNotBlank()
            assertThat(OidcTestHelper.queryParam(location, "state")).isEqualTo("pw-state")
        } finally {
            context.close()
        }
    }

    private fun enc(s: String): String = java.net.URLEncoder.encode(s, Charsets.UTF_8)
}
