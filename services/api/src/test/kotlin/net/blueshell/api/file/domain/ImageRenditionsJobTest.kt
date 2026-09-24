package net.blueshell.api.file.domain

import net.blueshell.api.file.persistence.File
import net.blueshell.api.file.persistence.FileRepository
import net.blueshell.api.jobs.api.JobOutcome
import net.blueshell.api.testsupport.runJob
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import tools.jackson.databind.ObjectMapper
import java.util.Optional

/**
 * The job that writes one picture's widths away from whatever asked for them.
 *
 * No Spring context: the handler's whole job is to find a record and hand it on, and the one
 * decision in it is what to do when the record is gone.
 */
class ImageRenditionsJobTest {
    private val files: FileRepository = mock()
    private val renditions: ImageRenditionWriter = mock()
    private val job = ImageRenditionsJob(ObjectMapper(), files, renditions)

    @Test
    fun `answers the job type the queue stores`() {
        assertThat(job.jobType).isEqualTo("image.derive-renditions")
    }

    @Test
    fun `writes the widths of the picture the payload names`() {
        val source: File = mock()
        whenever(files.findById(7L)).thenReturn(Optional.of(source))
        whenever(renditions.widthsOf(source)).thenReturn(ImageRenditionWriter.Widths(emptyList()))

        assertThat(job.runJob("""{"fileId":7}""")).isEqualTo(JobOutcome.Done)

        verify(renditions).widthsOf(source)
    }

    @Test
    fun `a picture that gets no widths is skipped with the writer's reason`() {
        val source: File = mock()
        whenever(files.findById(7L)).thenReturn(Optional.of(source))
        whenever(renditions.widthsOf(source)).thenReturn(ImageRenditionWriter.Widths.none("A vector picture needs no widths."))

        assertThat(job.runJob("""{"fileId":7}""")).isEqualTo(JobOutcome.Skipped("A vector picture needs no widths."))
    }

    /**
     * A picture deleted after the job was queued is not a failure: retrying would never find
     * one, and the queue would keep the row red until somebody looked at it.
     */
    @Test
    fun `a picture that is gone is skipped, not written and not retried`() {
        whenever(files.findById(7L)).thenReturn(Optional.empty())

        assertThat(job.runJob("""{"fileId":7}""")).isEqualTo(JobOutcome.Skipped("The picture has been deleted."))

        verifyNoInteractions(renditions)
    }
}
