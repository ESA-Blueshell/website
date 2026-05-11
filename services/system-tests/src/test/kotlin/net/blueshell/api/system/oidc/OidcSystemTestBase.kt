package net.blueshell.api.system.oidc

import net.blueshell.api.ApiApplication
import net.blueshell.api.config.TestCleanUpListener
import net.blueshell.api.factory.user.persistence.UserFactory
import net.blueshell.api.infrastructure.security.JwtTokenGenerator
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
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

    protected fun bearerToken(username: String): String =
        tokenGenerator.generateToken(username)

    protected fun get(
        path: String,
        bearer: String? = null,
        headers: Map<String, String> = emptyMap(),
        client: HttpClient = newClient(),
    ): HttpResponse<String> {
        val builder = HttpRequest.newBuilder()
            .uri(URI.create(if (path.startsWith("http")) path else baseUrl + path))
            .GET()
            .timeout(Duration.ofSeconds(10))
        bearer?.let { builder.header("Authorization", "Bearer $it") }
        headers.forEach { (k, v) -> builder.header(k, v) }
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString())
    }

    protected fun urlEncode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8)
}
