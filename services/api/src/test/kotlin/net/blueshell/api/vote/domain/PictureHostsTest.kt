package net.blueshell.api.vote.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class PictureHostsTest {
    @Test
    fun `the picture hosts are IGDB's, YouTube's and Spotify's`() {
        assertThat(listOf("images.igdb.com", "i.ytimg.com", "i.scdn.co", "image-cdn-fa.spotifycdn.com").all(::isPictureHost)).isTrue
        assertThat(listOf("example.com", "spotifycdn.com.evil.net", "ytimg.com").none(::isPictureHost)).isTrue
    }
}
