package net.blueshell.api.platform.config

import net.blueshell.api.testsupport.ValkeyTestContainerConfig
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalManagementPort
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

/**
 * actuatorChain keeps CSRF enabled. The management endpoints answer on their
 * own port, which MockMvc never maps, so this drives a real servlet container
 * to prove the probes kubelet, Prometheus and Gatus depend on still pass, and
 * that no request to the management port leaves a servlet session behind.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = ["management.server.port=0"],
)
@ActiveProfiles("test")
@Import(ValkeyTestContainerConfig::class)
class ActuatorProbeCsrfIT {

    @LocalManagementPort
    private var managementPort: Int = 0

    private val client: HttpClient = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NEVER)
        .build()

    private fun send(path: String, method: String = "GET"): HttpResponse<String> =
        client.send(
            HttpRequest.newBuilder(URI.create("http://localhost:$managementPort$path"))
                .method(method, HttpRequest.BodyPublishers.noBody())
                .build(),
            HttpResponse.BodyHandlers.ofString(),
        )

    private fun HttpResponse<*>.sessionCookies(): List<String> =
        headers().allValues("set-cookie").filter { it.startsWith("JSESSIONID") }

    @Test
    fun `the probes and scrapes the cluster relies on answer without a csrf token`() {
        listOf(
            "/actuator/health/liveness",
            "/actuator/health/readiness",
            "/actuator/info",
            "/actuator/prometheus",
            "/actuator/metrics",
        ).forEach { path ->
            assertThat(send(path).statusCode()).describedAs(path).isEqualTo(200)
        }
    }

    // The aggregate endpoint reports 503 whenever any indicator is DOWN, which
    // is environment-dependent (no mail server here). What matters is that
    // security lets the request reach the endpoint at all.
    @Test
    fun `the aggregate health endpoint is not challenged`() {
        assertThat(send("/actuator/health").statusCode()).isNotIn(401, 403)
    }

    @Test
    fun `read methods are neither challenged nor given a session`() {
        listOf("GET", "HEAD", "OPTIONS").forEach { method ->
            val response = send("/actuator/health", method)

            assertThat(response.statusCode()).describedAs(method).isNotIn(401, 403)
            assertThat(response.sessionCookies()).describedAs(method).isEmpty()
        }
    }

    // Nothing legitimately writes to the management port, and CSRF now refuses
    // the attempt. The cookie-backed token repository keeps that refusal from
    // minting a servlet session per request.
    @Test
    fun `state changing methods are refused without minting a session`() {
        listOf("POST", "PUT", "PATCH", "DELETE").forEach { method ->
            val response = send("/actuator/health", method)

            assertThat(response.statusCode()).describedAs(method).isEqualTo(403)
            assertThat(response.sessionCookies()).describedAs(method).isEmpty()
        }
    }
}
