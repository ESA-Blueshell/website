package net.blueshell.api.vote.domain

import net.blueshell.api.vote.persistence.TrackSource
import tools.jackson.databind.ObjectMapper
import java.io.IOException
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration

private val OEMBED_ENDPOINTS =
    mapOf(
        TrackSource.YOUTUBE to "https://www.youtube.com/oembed",
        TrackSource.SPOTIFY to "https://open.spotify.com/oembed",
    )

/**
 * Describes a link through YouTube's and Spotify's oEmbed endpoints, which need no key and count
 * against no quota. A private or removed video answers 401 or 404, which reads as no preview.
 */
class OEmbedTrackPreviews(
    private val http: HttpClient,
    private val json: ObjectMapper,
    private val endpoints: Map<TrackSource, String> = OEMBED_ENDPOINTS,
) : TrackPreviews {
    override fun of(link: TrackLink): TrackPreview? {
        val url = URLEncoder.encode(link.url, StandardCharsets.UTF_8)
        val request =
            HttpRequest
                .newBuilder(URI.create("${endpoints.getValue(link.source)}?format=json&url=$url"))
                .timeout(TIMEOUT)
                .header("Accept", "application/json")
                .GET()
                .build()
        val response =
            try {
                http.send(request, HttpResponse.BodyHandlers.ofString())
            } catch (_: IOException) {
                return null
            }
        if (response.statusCode() !in OK_RANGE) return null
        val answer = runCatching { json.readTree(response.body()) }.getOrNull() ?: return null
        val title = answer.get("title")?.asString()?.takeIf { it.isNotBlank() } ?: return null
        return TrackPreview(title = title, thumbnailUrl = answer.get("thumbnail_url")?.asString())
    }

    private companion object {
        val OK_RANGE = 200..299
        val TIMEOUT: Duration = Duration.ofSeconds(5)
    }
}
