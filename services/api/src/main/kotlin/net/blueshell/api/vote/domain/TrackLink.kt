package net.blueshell.api.vote.domain

import net.blueshell.api.vote.persistence.TrackSource
import java.net.URI

/** A link reduced to what identifies it, so every spelling of one video or track is one track. */
data class TrackLink(
    val source: TrackSource,
    val externalId: String,
) {
    /** The address oEmbed is asked about and the page links to. */
    val url: String
        get() =
            when (source) {
                TrackSource.YOUTUBE -> "https://www.youtube.com/watch?v=$externalId"
                TrackSource.SPOTIFY -> "https://open.spotify.com/track/$externalId"
            }

    companion object {
        private val YOUTUBE_ID = Regex("^[A-Za-z0-9_-]{11}$")
        private val SPOTIFY_ID = Regex("^[A-Za-z0-9]{22}$")
        private val YOUTUBE_HOSTS = setOf("youtube.com", "www.youtube.com", "m.youtube.com", "music.youtube.com")

        /** Reads a pasted link, or refuses one that is not a YouTube video or a Spotify track. */
        fun parse(pasted: String): TrackLink {
            val uri = runCatching { URI(pasted.trim()) }.getOrNull()
            val host = uri?.host?.lowercase()
            if (uri == null || host == null || uri.scheme?.lowercase() !in setOf("http", "https")) throw UnsupportedTrackLink()
            val segments =
                uri.path
                    .orEmpty()
                    .split('/')
                    .filter { it.isNotEmpty() }
            val link =
                when {
                    host == "youtu.be" -> segments.firstOrNull()?.let { youtube(it) }
                    host in YOUTUBE_HOSTS -> youtubePath(uri, segments)
                    // An open.spotify.com path may lead with a locale segment such as `intl-nl`.
                    host == "open.spotify.com" -> spotifyPath(segments)
                    else -> null
                }
            return link ?: throw UnsupportedTrackLink()
        }

        private fun youtubePath(
            uri: URI,
            segments: List<String>,
        ): TrackLink? =
            when (segments.firstOrNull()) {
                "watch" -> queryParameter(uri, "v")?.let { youtube(it) }
                "shorts", "live", "embed" -> segments.getOrNull(1)?.let { youtube(it) }
                else -> null
            }

        private fun spotifyPath(segments: List<String>): TrackLink? {
            val path = segments.dropWhile { it.startsWith("intl-") }
            return if (path.firstOrNull() == "track") path.getOrNull(1)?.let { spotify(it) } else null
        }

        private fun youtube(id: String) = if (YOUTUBE_ID.matches(id)) TrackLink(TrackSource.YOUTUBE, id) else null

        private fun spotify(id: String) = if (SPOTIFY_ID.matches(id)) TrackLink(TrackSource.SPOTIFY, id) else null

        private fun queryParameter(
            uri: URI,
            name: String,
        ): String? =
            uri.rawQuery
                ?.split('&')
                ?.map { it.split('=', limit = 2) }
                ?.firstOrNull { it.first() == name }
                ?.getOrNull(1)
    }
}
