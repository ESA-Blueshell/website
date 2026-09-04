package net.blueshell.api.system.oidc.playwright

import com.microsoft.playwright.APIRequestContext
import com.microsoft.playwright.Playwright
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import net.blueshell.api.system.oidc.OidcTestHelper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

/**
 * Drives `/oauth2/jwks` against a real api wired to Vault Transit, covering the path that needs
 * `org.bouncycastle:bcpkix-jdk18on` on the runtime classpath — Nimbus's
 * `RSAKey.parseFromPEMEncodedObjects` reaches `JcaPEMKeyConverter`, and without it this 500s.
 *
 * Not run as part of `:check`. Bring up the stack first:
 *
 *   docker compose -f docker-compose.yml -f docker-compose.oidc-e2e.yml \
 *     --profile oidc-e2e up -d
 *
 * Then:
 *
 *   ./gradlew :tests:system:vaultOidcLiveTest
 *
 * Uses Playwright's APIRequest client rather than a chromium BrowserContext: chromium tries
 * h2c/altsvc handshakes that the Tomcat connector closes, tripping ECONNRESET on the first
 * plain-HTTP localhost request.
 */
@Tag("vault-oidc-live")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class VaultTransitJwksPlaywrightTest {

    private lateinit var playwright: Playwright
    private lateinit var request: APIRequestContext

    private val apiBaseUrl: String =
        System.getProperty("test.api.url", "http://localhost:8080")

    @BeforeAll
    fun startPlaywright() {
        waitForApiReady()
        playwright = Playwright.create()
        request = playwright.request().newContext()
    }

    /**
     * Block until the api answers 200 on /health. In dev compose the api uses
     * Spring Boot devtools, which auto-restarts whenever Gradle touches the
     * shared `/src` mount — so any preceding `gradle` invocation (e.g. the one
     * that just compiled this test class) can leave the api mid-restart when
     * we start. This is a *precondition*, not a retry on the JWKS assertion.
     */
    private fun waitForApiReady() {
        val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build()
        val request = HttpRequest.newBuilder(URI.create("$apiBaseUrl/health"))
            .timeout(Duration.ofSeconds(2))
            .GET()
            .build()
        val deadline = System.nanoTime() + Duration.ofSeconds(60).toNanos()
        while (System.nanoTime() < deadline) {
            val ok = runCatching { client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode() == 200 }
                .getOrDefault(false)
            if (ok) return
            Thread.sleep(500)
        }
        error("api at $apiBaseUrl did not become ready within 60s — is the oidc-e2e compose stack up?")
    }

    @AfterAll
    fun stopPlaywright() {
        request.dispose()
        playwright.close()
    }

    @Test
    fun `jwks endpoint serves at least one RSA signing key when transit is enabled`() {
        val response = request.get("$apiBaseUrl/oauth2/jwks")

        assertThat(response.status())
            .withFailMessage(
                "Expected 200 from /oauth2/jwks, got %d. Body:\n%s",
                response.status(),
                response.text(),
            ).isEqualTo(200)

        val body = OidcTestHelper.parseJson(response.text())
        val keys = body["keys"]
        assertThat(keys).isNotNull
        assertThat(keys.isArray).isTrue()
        assertThat(keys.size())
            .withFailMessage("Expected /oauth2/jwks to publish ≥1 key; got %s", keys)
            .isGreaterThanOrEqualTo(1)

        val first = OidcTestHelper.mapElements(keys) { it }.first()
        assertThat(first["kty"]?.asString()).isEqualTo("RSA")
        assertThat(first["n"]?.asString()).isNotBlank()
        assertThat(first["e"]?.asString()).isNotBlank()
        // kid/alg/use must be present and match what VaultTransitJwtEncoder
        // stamps into the JWT header — go-oidc-v3 (Vault) filters JWKS keys
        // by kid and refuses to verify when there's no match.
        assertThat(first["kid"]?.asString())
            .withFailMessage("JWKS key must carry kid='api-jwt:v<N>' so Vault's go-oidc verifier picks it up")
            .startsWith("api-jwt:v")
        assertThat(first["alg"]?.asString()).isEqualTo("RS256")
        assertThat(first["use"]?.asString()).isEqualTo("sig")
    }
}
