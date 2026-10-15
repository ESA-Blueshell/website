package net.blueshell.api.file.domain

import net.blueshell.api.file.api.BlobStore
import net.blueshell.api.shared.job.JobQueue
import net.blueshell.api.shared.job.JobTrigger
import net.blueshell.api.testsupport.Entities
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

class ImageRenditionsTest {
    private val writer: ImageRenditionWriter = mock()
    private val jobs: JobQueue = mock()
    private val blobs: BlobStore = mock()

    private fun stored(
        path: String,
        bytes: ByteArray,
    ) {
        whenever(blobs.exists(path)).thenReturn(true)
        whenever(blobs.open(path)).thenAnswer { bytes.inputStream() }
    }

    private fun webp(animated: Boolean): ByteArray =
        "RIFF".toByteArray() + byteArrayOf(22, 0, 0, 0) + "WEBPVP8X".toByteArray() + byteArrayOf(10, 0, 0, 0) +
            byteArrayOf(if (animated) 0x02 else 0, 0, 0, 0, 9, 0, 0, 9, 0, 0)

    private fun renditions() = ImageRenditions(writer, blobs, jobs)

    @Test
    fun `queues the widths of a gif rather than writing them now`() {
        val source = Entities.file(id = 7, path = "banners/party.gif", mediaType = "image/gif")
        stored(source.path, "GIF89a".toByteArray())

        assertThat(renditions().request(source)).isEmpty()

        verify(jobs).runAsync(ImageJobs.DeriveRenditions, ImageJobs.DeriveRenditionsPayload(7), JobTrigger.SITE_ACTION)
        verifyNoInteractions(writer)
    }

    @Test
    fun `queues the widths of a webp whose header says it moves`() {
        val source = Entities.file(id = 8, path = "banners/party.webp", mediaType = "image/webp")
        stored(source.path, webp(animated = true))

        assertThat(renditions().request(source)).isEmpty()

        verify(jobs).runAsync(ImageJobs.DeriveRenditions, ImageJobs.DeriveRenditionsPayload(8), JobTrigger.SITE_ACTION)
    }

    @Test
    fun `writes the widths of a still webp now`() {
        val source = Entities.file(id = 9, path = "banners/still.webp", mediaType = "image/webp")
        stored(source.path, webp(animated = false))
        val widths = listOf(Entities.file(id = 10, path = "banners/still-320.webp"))
        whenever(writer.derive(source)).doReturn(widths)

        assertThat(renditions().request(source)).isEqualTo(widths)
        verifyNoInteractions(jobs)
    }

    @Test
    fun `writes the widths of any other picture now, and of one whose bytes are gone`() {
        val vector = Entities.file(id = 11, path = "icons/logo.svg", mediaType = "image/svg+xml")
        stored(vector.path, "<svg/>".toByteArray())
        val gone = Entities.file(id = 12, path = "banners/gone.gif", mediaType = "image/gif")

        renditions().request(vector)
        renditions().request(gone)

        verify(writer).derive(vector)
        verify(writer).derive(gone)
        verifyNoInteractions(jobs)
    }
}
