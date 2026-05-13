package net.blueshell.api.system.oidc

import net.blueshell.systemtests.PlaywrightShardCondition
import net.blueshell.systemtests.TestEnvironment
import net.blueshell.systemtests.TestHelper
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import java.net.CookieManager
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration

/**
 * Base for OIDC system tests that talk to the api strictly over HTTP.
 * The api runs as a docker-compose service on `localhost:8080`; the
 * test bodies never inject beans or reach into repositories, every
 * observable behaviour comes from real HTTP requests routed through
 * `TestHelper`.
 */
@ExtendWith(PlaywrightShardCondition::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class OidcSystemTestBase {

    protected val baseUrl: String = TestEnvironment.apiUrl

    protected val authCookieName: String get() = TestEnvironment.authCookieName

    /**
     * HttpClient that does NOT follow redirects — every OIDC test
     * needs to inspect 302 Location headers (login redirect,
     * unauthorized redirect, authorize → callback with code).
     */
    protected fun newClient(): HttpClient =
        HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER)
            .cookieHandler(CookieManager())
            .connectTimeout(Duration.ofSeconds(5))
            .build()

    /**
     * Log a `RegisteredUser` in and return the auth cookie value.
     * Mints the JWT through the real `/auth` endpoint — same shape
     * as the previous `tokenGenerator.generateToken(...)` call, just
     * routed through HTTP rather than an in-process bean.
     */
    protected fun sessionTokenFor(user: TestHelper.RegisteredUser): String =
        TestHelper.login(user).auth

    /**
     * GET against the running app. Carries the session JWT as the
     * auth cookie (not `Authorization: Bearer` — the OIDC chain
     * would reject our HS256 session JWT through the resource-server
     * filter), and asks for `text/html` so the SAS chain dispatches
     * `AuthenticationException` to the loginRedirect entry point
     * rather than the resource server's 401 default.
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
