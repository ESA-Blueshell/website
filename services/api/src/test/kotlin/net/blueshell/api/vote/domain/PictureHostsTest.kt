package net.blueshell.api.vote.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class PictureHostsTest {
    @Test
    fun `the picture hosts are IGDB's, YouTube's and Spotify's`() {
        assertThat(listOf("images.igdb.com", "i.ytimg.com", "i.scdn.co", "image-cdn-fa.spotifycdn.com").all(::pictureHost)).isTrue
        assertThat(listOf("example.com", "spotifycdn.com.evil.net", "ytimg.com").none(::pictureHost)).isTrue
    }

    @Test
    fun `an IGDB cover address is built only from an id IGDB would issue`() {
        assertThat(igdbCoverUrl("co1abc", "t_cover_big")).isEqualTo("https://images.igdb.com/igdb/image/upload/t_cover_big/co1abc.jpg")
        assertThat(igdbCoverUrl("../x", "t_cover_big")).isNull()
        assertThat(igdbCoverUrl("", "t_cover_big")).isNull()
    }
}
