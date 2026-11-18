package net.blueshell.api.vote.domain

import net.blueshell.api.vote.persistence.TrackSource
import org.springframework.http.MediaType
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import tools.jackson.databind.ObjectMapper

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
    private val rest: RestClient,
    private val json: ObjectMapper,
    private val endpoints: Map<TrackSource, String> = OEMBED_ENDPOINTS,
) : TrackPreviews {
    override fun of(link: TrackLink): TrackPreview? {
        val body =
            try {
                rest
                    .get()
                    .uri("${endpoints.getValue(link.source)}?format=json&url={url}", link.url)
                    .accept(MediaType.APPLICATION_JSON)
                    .exchange { _, response -> response.bodyTo(String::class.java).takeIf { response.statusCode.is2xxSuccessful } }
            } catch (_: RestClientException) {
                null
            } ?: return null
        val answer = runCatching { json.readTree(body) }.getOrNull() ?: return null
        val title = answer.get("title")?.asString()?.takeIf { it.isNotBlank() } ?: return null
        return TrackPreview(title = title, thumbnailUrl = answer.get("thumbnail_url")?.asString())
    }
}
