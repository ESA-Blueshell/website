package net.blueshell.api.vote.domain

import net.blueshell.api.vote.persistence.TrackSource
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class TrackServiceTest {
    @Test
    fun `each service says where a track opens and where it plays`() {
        assertThat(TrackSource.YOUTUBE.service.watchUrl("dQw4w9WgXcQ")).isEqualTo("https://www.youtube.com/watch?v=dQw4w9WgXcQ")
        assertThat(TrackSource.YOUTUBE.service.embedUrl("dQw4w9WgXcQ")).isEqualTo("https://www.youtube-nocookie.com/embed/dQw4w9WgXcQ")
        assertThat(TrackSource.SPOTIFY.service.watchUrl("x")).isEqualTo("https://open.spotify.com/track/x")
        assertThat(TrackSource.SPOTIFY.service.embedUrl("x")).isEqualTo("https://open.spotify.com/embed/track/x")
    }

    @Test
    fun `each service names its own oEmbed endpoint and thumbnail hosts`() {
        assertThat(TrackSource.entries.map { it.service.oEmbedEndpoint })
            .containsExactly("https://www.youtube.com/oembed", "https://open.spotify.com/oembed")
        assertThat(TrackSource.YOUTUBE.service.isThumbnailHost("i.ytimg.com")).isTrue
        assertThat(TrackSource.YOUTUBE.service.isThumbnailHost("i.scdn.co")).isFalse
        assertThat(TrackSource.SPOTIFY.service.isThumbnailHost("image-cdn-fa.spotifycdn.com")).isTrue
    }
}
