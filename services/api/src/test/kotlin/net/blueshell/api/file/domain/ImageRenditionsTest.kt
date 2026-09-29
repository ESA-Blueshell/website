package net.blueshell.api.file.domain

import net.blueshell.api.file.api.BlobStore
import net.blueshell.api.shared.job.JobQueue
import net.blueshell.api.shared.job.JobTrigger
import net.blueshell.api.testsupport.Entities
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
        val source = Entities.file(id = 7, path = "banners/party.gif", mediaType = "image/gif")

        assertThat(ImageRenditions(writer, animated, blobs, jobs).request(source)).isEmpty()

        verify(jobs).runAsync(ImageJobs.DeriveRenditions, ImageJobs.DeriveRenditionsPayload(7), JobTrigger.SITE_ACTION)
        verifyNoInteractions(writer)
    }
}
