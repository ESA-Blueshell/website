package net.blueshell.api.contact.domain

import net.blueshell.api.shared.credentials.RotatingSecret
import org.springframework.http.HttpRequest
import org.springframework.http.client.ClientHttpRequestExecution
import org.springframework.http.client.ClientHttpRequestInterceptor
import org.springframework.http.client.ClientHttpResponse

/** Sends the Brevo key as it stands now, so a key rotated in Vault needs no restart (api ADR-033). */
internal class BrevoApiKeyHeader(
    private val key: RotatingSecret,
) : ClientHttpRequestInterceptor {
    override fun intercept(
        request: HttpRequest,
        body: ByteArray,
        execution: ClientHttpRequestExecution,
    ): ClientHttpResponse {
        request.headers.set("api-key", key.current())
        return execution.execute(request, body)
    }
}
