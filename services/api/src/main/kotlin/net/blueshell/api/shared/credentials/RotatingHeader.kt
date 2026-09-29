package net.blueshell.api.shared.credentials

import org.springframework.http.HttpRequest
import org.springframework.http.client.ClientHttpRequestExecution
import org.springframework.http.client.ClientHttpRequestInterceptor
import org.springframework.http.client.ClientHttpResponse

/**
 * Sends a credential as it stands now in [header], after [prefix], so a key rotated in Vault
 * needs no restart (api ADR-033).
 */
class RotatingHeader(
    private val header: String,
    private val secret: RotatingSecret,
    private val prefix: String = "",
) : ClientHttpRequestInterceptor {
    override fun intercept(
        request: HttpRequest,
        body: ByteArray,
        execution: ClientHttpRequestExecution,
    ): ClientHttpResponse {
        request.headers.set(header, prefix + secret.current())
        return execution.execute(request, body)
    }
}
