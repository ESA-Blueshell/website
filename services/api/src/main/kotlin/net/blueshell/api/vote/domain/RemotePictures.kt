package net.blueshell.api.vote.domain

import net.blueshell.api.shared.enums.FileType
import java.io.IOException
import java.io.InputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/** A picture fetched from another service, as it was sent. */
class RemotePicture(
    val bytes: ByteArray,
    val mediaType: String,
)

/** Fetches a picture another service hosts on a visitor's behalf, so their browser never asks for it. */
interface RemotePictures {
    /** The picture at [url], or null where it cannot be fetched or is not a picture we take. */
    fun fetch(url: String): RemotePicture?
}

/**
 * Fetches only over https on its default port, only from the hosts [allowed] names, and follows no
 * redirect, so an address a link or another service's answer carries cannot point the api anywhere
 * else. A picture is taken on the terms a stored vote cover is, and read no further than its limit.
 * It stays on [HttpClient] rather than RestClient to read the body as a capped stream.
 */
class HostBoundRemotePictures(
    private val http: HttpClient,
    private val allowed: (String) -> Boolean,
) : RemotePictures {
    override fun fetch(url: String): RemotePicture? {
        val uri = runCatching { URI(url) }.getOrNull() ?: return null
        if (uri.scheme != "https" || uri.port !in HTTPS_PORTS || !allowed(uri.host.orEmpty().lowercase())) return null
        val request =
            HttpRequest
                .newBuilder(uri)
                .timeout(TIMEOUT)
                .GET()
                .build()
        return try {
            val response = http.send(request, HttpResponse.BodyHandlers.ofInputStream())
            response.body().use { pictureOf(response, it) }
        } catch (_: IOException) {
            null
        }
    }

    private fun pictureOf(
        response: HttpResponse<*>,
        body: InputStream,
    ): RemotePicture? {
        val type =
            response
                .headers()
                .firstValue("Content-Type")
                .orElse("")
                .substringBefore(';')
                .trim()
                .lowercase()
        val declared = response.headers().firstValueAsLong("Content-Length").orElse(0)
        if (response.statusCode() != OK || type !in TYPES || declared > MAX_BYTES) return null
        val bytes = body.readNBytes(MAX_BYTES + 1)
        return if (bytes.isNotEmpty() && bytes.size <= MAX_BYTES) RemotePicture(bytes, type) else null
    }

    private companion object {
        const val OK = 200
        val HTTPS_PORTS = setOf(-1, 443)
        val TYPES = FileType.VOTE_COVER.allowedMediaTypes
        val MAX_BYTES = requireNotNull(FileType.VOTE_COVER.maxBytes).toInt()
        val TIMEOUT: Duration = Duration.ofSeconds(5)
    }
}
