package net.blueshell.api.vote.domain

import net.blueshell.api.vote.persistence.TrackSource
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class TrackLinkTest {
    @ParameterizedTest
    @ValueSource(
        strings = [
            "https://youtu.be/dQw4w9WgXcQ",
            "https://youtu.be/dQw4w9WgXcQ?si=share",
            "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
            "https://www.youtube.com/watch?feature=share&v=dQw4w9WgXcQ&t=42s",
            "http://youtube.com/watch?v=dQw4w9WgXcQ",
            "https://m.youtube.com/watch?v=dQw4w9WgXcQ",
            "https://music.youtube.com/watch?v=dQw4w9WgXcQ&list=RD",
            "https://www.youtube.com/shorts/dQw4w9WgXcQ",
            "https://www.youtube.com/live/dQw4w9WgXcQ",
            "https://www.youtube.com/embed/dQw4w9WgXcQ",
            "  https://YOUTU.BE/dQw4w9WgXcQ  ",
        ],
    )
    fun `every spelling of one YouTube video is one link`(pasted: String) {
        val link = TrackLink.parse(pasted)

        assertThat(link).isEqualTo(TrackLink(TrackSource.YOUTUBE, "dQw4w9WgXcQ"))
        assertThat(link.url).isEqualTo("https://www.youtube.com/watch?v=dQw4w9WgXcQ")
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "https://open.spotify.com/track/4cOdK2wGLETKBW3PvgPWqT",
            "https://open.spotify.com/track/4cOdK2wGLETKBW3PvgPWqT?si=abc",
            "https://open.spotify.com/intl-nl/track/4cOdK2wGLETKBW3PvgPWqT",
        ],
    )
    fun `every spelling of one Spotify track is one link`(pasted: String) {
        val link = TrackLink.parse(pasted)

        assertThat(link).isEqualTo(TrackLink(TrackSource.SPOTIFY, "4cOdK2wGLETKBW3PvgPWqT"))
        assertThat(link.url).isEqualTo("https://open.spotify.com/track/4cOdK2wGLETKBW3PvgPWqT")
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "not a link",
            "ftp://youtu.be/dQw4w9WgXcQ",
            "https://example.com/watch?v=dQw4w9WgXcQ",
            "https://youtu.be/",
            "https://youtu.be/short",
            "https://www.youtube.com/watch",
            "https://www.youtube.com/watch?list=RD",
            "https://www.youtube.com/watch?v",
            "https://www.youtube.com/channel/UC123",
            "https://www.youtube.com/shorts",
            "https://open.spotify.com/album/4cOdK2wGLETKBW3PvgPWqT",
            "https://open.spotify.com/track/tooShort",
            "https://open.spotify.com/track",
            "youtu.be/dQw4w9WgXcQ",
            "https://[broken",
        ],
    )
    fun `anything but a YouTube video or a Spotify track is refused`(pasted: String) {
        assertThatThrownBy { TrackLink.parse(pasted) }.isInstanceOf(UnsupportedTrackLink::class.java)
    }
}
