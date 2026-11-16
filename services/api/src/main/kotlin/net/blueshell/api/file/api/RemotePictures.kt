package net.blueshell.api.file.api

import java.io.IOException
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
 * Fetches only over https, only from the hosts [allowed] names, and follows no redirect, so an
 * address a link or another service's answer carries cannot point the api anywhere else.
 */
class HostBoundRemotePictures(
    private val http: HttpClient,
    private val allowed: (String) -> Boolean,
) : RemotePictures {
    override fun fetch(url: String): RemotePicture? {
        val uri = runCatching { URI(url) }.getOrNull() ?: return null
        if (uri.scheme != "https" || !allowed(uri.host.orEmpty().lowercase())) return null
        return read(uri)
    }

    /** Asks [uri] for the picture, whatever its host; [fetch] decides which may be asked. */
    internal fun read(uri: URI): RemotePicture? {
        val request =
            HttpRequest
                .newBuilder(uri)
                .timeout(TIMEOUT)
                .GET()
                .build()
        val response =
            try {
                http.send(request, HttpResponse.BodyHandlers.ofByteArray())
            } catch (_: IOException) {
                return null
            }
        val type =
            response
                .headers()
                .firstValue("Content-Type")
                .orElse("")
                .substringBefore(';')
                .trim()
                .lowercase()
        val body = response.body()
        val usable = response.statusCode() == OK && type in TYPES && body.isNotEmpty() && body.size <= MAX_BYTES
        return if (usable) RemotePicture(body, type) else null
    }

    private companion object {
        const val OK = 200
        const val MAX_BYTES = 5 * 1024 * 1024
        val TYPES = setOf("image/jpeg", "image/png", "image/webp")
        val TIMEOUT: Duration = Duration.ofSeconds(5)
    }
}
