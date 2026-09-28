package net.blueshell.api.file.domain

import net.blueshell.api.file.api.BlobStore
import net.blueshell.api.file.persistence.File
import net.blueshell.api.shared.job.JobQueue
import net.blueshell.api.shared.job.JobTrigger
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions

class ImageRenditionsTest {
    private val writer: ImageRenditionWriter = mock()
    private val jobs: JobQueue = mock()
    private val blobs: BlobStore = mock { on { exists("banners/party.gif") } doReturn true }
    private val animated: AnimatedImages = mock { on { animates(eq("image/gif"), any()) } doReturn true }

    @Test
    fun `queues the widths of a picture that moves rather than writing them now`() {
        val source: File =
            mock {
                on { id } doReturn 7
                on { path } doReturn "banners/party.gif"
                on { mediaType } doReturn "image/gif"
            }

        assertThat(ImageRenditions(writer, animated, blobs, jobs).request(source)).isEmpty()

        verify(jobs).runAsync(ImageJobs.DeriveRenditions, ImageJobs.DeriveRenditionsPayload(7), JobTrigger.SITE_ACTION)
        verifyNoInteractions(writer)
    }
}
