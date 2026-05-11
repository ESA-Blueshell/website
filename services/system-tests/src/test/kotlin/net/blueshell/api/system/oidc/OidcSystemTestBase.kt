package net.blueshell.api.system.oidc

import net.blueshell.api.ApiApplication
import net.blueshell.api.config.TestCleanUpListener
import net.blueshell.api.factory.user.persistence.UserFactory
import net.blueshell.api.infrastructure.security.JwtTokenGenerator
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestExecutionListeners
import java.net.CookieManager
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration

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
abstract class OidcSystemTestBase {

    @Autowired
    protected lateinit var userFactory: UserFactory

    @Autowired
    protected lateinit var tokenGenerator: JwtTokenGenerator

    @Value($$"\${security.auth-cookie.name:BSH_AUTH}")
    protected lateinit var authCookieName: String

    protected val baseUrl: String = "http://localhost:8080"

    /**
     * HttpClient that does NOT follow redirects — every OIDC test needs to
     * inspect 302 Location headers (login redirect, unauthorized redirect,
     * authorize → callback with code).
     */
    protected fun newClient(): HttpClient =
        HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER)
            .cookieHandler(CookieManager())
            .connectTimeout(Duration.ofSeconds(5))
            .build()

    protected fun sessionTokenFor(username: String): String =
        tokenGenerator.generateToken(username)

    /**
     * GET against the running app. Matches the browser-driven OIDC flow:
     * session JWT carried as the BSH_AUTH cookie (not Authorization: Bearer
     * — `.oidc()` brings in a resource-server BearerTokenAuthenticationFilter
     * that would reject our HS256 session JWT), Accept: text/html so the
     * SAS chain dispatches AuthenticationExceptions to our loginRedirect
     * entry point rather than the resource-server's 401 default.
     */
    protected fun get(
        path: String,
        sessionToken: String? = null,
        headers: Map<String, String> = emptyMap(),
        client: HttpClient = newClient(),
    ): HttpResponse<String> {
        val builder = HttpRequest.newBuilder()
            .uri(URI.create(if (path.startsWith("http")) path else baseUrl + path))
            .GET()
            .timeout(Duration.ofSeconds(10))
            .header("Accept", "text/html")
        sessionToken?.let { builder.header("Cookie", "$authCookieName=$it") }
        headers.forEach { (k, v) -> builder.header(k, v) }
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString())
    }

    protected fun urlEncode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8)
}
