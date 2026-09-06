package net.blueshell.api.file.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * What a stored file is typed as.
 *
 * The extensions asserted here are the ones the table carries in its own right, so every
 * deployment answers them the same. Types only Spring's wider table would know are deliberately
 * not asserted: which of those it carries is the framework's business, not this site's.
 */
class MediaTypesTest {

    @Test
    fun `the pictures these pages draw are typed from their names`() {
        assertThat(MediaTypes.ofName("a.png")).isEqualTo("image/png")
        assertThat(MediaTypes.ofName("a.jpg")).isEqualTo("image/jpeg")
        assertThat(MediaTypes.ofName("a.jpeg")).isEqualTo("image/jpeg")
        assertThat(MediaTypes.ofName("a.gif")).isEqualTo("image/gif")
        assertThat(MediaTypes.ofName("a.svg")).isEqualTo("image/svg+xml")
    }

    @Test
    fun `webp is named, because it is what every picture on this site is converted to`() {
        assertThat(MediaTypes.ofName("a.webp")).isEqualTo("image/webp")
    }

    @Test
    fun `the documents and archives a member uploads are typed too`() {
        assertThat(MediaTypes.ofName("a.pdf")).isEqualTo("application/pdf")
        assertThat(MediaTypes.ofName("a.zip")).isEqualTo("application/zip")
        assertThat(MediaTypes.ofName("a.json")).isEqualTo("application/json")
        assertThat(MediaTypes.ofName("a.mp4")).isEqualTo("video/mp4")
        assertThat(MediaTypes.ofName("a.mp3")).isEqualTo("audio/mpeg")
    }

    @Test
    fun `an extension is read whatever case it arrived in`() {
        assertThat(MediaTypes.ofName("HOLIDAY.PNG")).isEqualTo("image/png")
    }

    @Test
    fun `an unknown extension is a stream of bytes rather than a guess`() {
        assertThat(MediaTypes.ofName("a.qqq")).isEqualTo(MediaTypes.OCTET_STREAM)
    }

    @Test
    fun `a name with no extension is a stream of bytes`() {
        assertThat(MediaTypes.ofName("README")).isEqualTo(MediaTypes.OCTET_STREAM)
    }

    @Test
    fun `a stored key is typed by its file rather than by its directory`() {
        assertThat(MediaTypes.ofName("my.documents/holiday.png")).isEqualTo("image/png")
    }
}
