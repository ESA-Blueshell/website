package net.blueshell.api.vote.domain

import net.blueshell.api.vote.persistence.TrackSource

/**
 * What the api knows about a service a track can come from: where a track opens, where it plays
 * inside a page, which oEmbed endpoint describes it and which host its thumbnails come from.
 * Adding a service starts here and in [TrackLink.parse], which reads its links.
 */
internal class TrackService(
    private val watchPrefix: String,
    private val embedPrefix: String,
    val oEmbedEndpoint: String,
    val isThumbnailHost: (String) -> Boolean,
) {
    fun watchUrl(externalId: String) = watchPrefix + externalId

    fun embedUrl(externalId: String) = embedPrefix + externalId
}

internal val TrackSource.service: TrackService
    get() =
        when (this) {
            // The privacy-enhanced player, which sets no cookie until the video plays.
            TrackSource.YOUTUBE ->
                TrackService(
                    watchPrefix = "https://www.youtube.com/watch?v=",
                    embedPrefix = "https://www.youtube-nocookie.com/embed/",
                    oEmbedEndpoint = "https://www.youtube.com/oembed",
                    isThumbnailHost = { it == "i.ytimg.com" },
                )
            TrackSource.SPOTIFY ->
                TrackService(
                    watchPrefix = "https://open.spotify.com/track/",
                    embedPrefix = "https://open.spotify.com/embed/track/",
                    oEmbedEndpoint = "https://open.spotify.com/oembed",
                    isThumbnailHost = { it == "i.scdn.co" || it.endsWith(".spotifycdn.com") },
                )
        }
