package net.blueshell.api.vote.domain

/** What YouTube or Spotify says a link is. [thumbnailUrl] is theirs, and only ever fetched by the api. */
data class TrackPreview(
    val title: String,
    val thumbnailUrl: String?,
)

/** Asks the link's own service what it is, through its keyless oEmbed endpoint. */
interface TrackPreviews {
    /** The link's title and thumbnail, or null when the service does not describe it. */
    fun of(link: TrackLink): TrackPreview?
}
