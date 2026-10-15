package net.blueshell.api.file.domain

import net.blueshell.api.file.api.BlobStore
import net.blueshell.api.file.api.PublicFileUrls
import net.blueshell.api.file.persistence.File
import net.blueshell.api.file.persistence.FileRepository
import net.blueshell.api.jobs.api.JobExecutor
import net.blueshell.api.shared.enums.FileType
import net.blueshell.api.shared.enums.JobExecutionStatus
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.testsupport.AnimatedGifs
import net.blueshell.api.testsupport.UserTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.http.MediaType
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

/**
 * With the imaging service down, a still upload is refused for now and nothing of it is kept,
 * and an animation's widths wait on a job that fails and is retried.
 */
@SpringBootTest
@Import(ImagingServiceDownIT.Down::class)
class ImagingServiceDownIT : UserTestSupport() {
    @Autowired
    private lateinit var files: FileRepository

    @Autowired
    private lateinit var blobs: BlobStore

    @Autowired
    private lateinit var renditions: ImageRenditions

    @Autowired
    private lateinit var executor: JobExecutor

    @Test
    fun `a still upload answers 503 and stores no file`() {
        val admin = createUserWithRole(Role.ADMIN)
        val jpeg =
            ByteArrayOutputStream()
                .also { ImageIO.write(BufferedImage(800, 400, BufferedImage.TYPE_INT_RGB), "jpg", it) }
                .toByteArray()
        val before = files.count()

        mvc
            .perform(
                multipart(PublicFileUrls.UPLOAD)
                    .file(MockMultipartFile("file", "picture.jpg", MediaType.IMAGE_JPEG_VALUE, jpeg))
                    .param("type", FileType.EVENT_BANNER.name)
                    .with(signedIn(admin))
                    .with(csrfToken()),
            ).andExpect(status().isServiceUnavailable)

        assertThat(files.count()).isEqualTo(before)
    }

    @Test
    fun `an animation's widths are queued, and the job fails and is retried rather than given up`() {
        // A GIF master, as the rows stored before WebP masters hold, which is what a start's backfill queues.
        val key = StoredFileNames.keyOf(FileType.EVENT_BANNER.directory, "down-banner.gif")
        val gif = AnimatedGifs.patched(1000, 400)
        blobs.put(key, gif.inputStream())
        val source =
            files.save(
                File(
                    name = "banner.gif",
                    path = key,
                    uploader = createUserWithRole(Role.ADMIN),
                    mediaType = MediaType.IMAGE_GIF_VALUE,
                    size = gif.size.toLong(),
                    width = 1000,
                    height = 400,
                    type = FileType.EVENT_BANNER,
                ),
            )

        assertThat(renditions.request(source)).isEmpty()
        val payload = mapper.writeValueAsString(ImageJobs.DeriveRenditionsPayload(source.id!!))
        val job = jobExecutions.findAll().single { it.jobType == ImageJobs.DeriveRenditions.type && it.payload == payload }
        executor.execute(job)

        val retried = jobExecutions.findById(job.id!!).orElseThrow()
        assertThat(retried.status).isEqualTo(JobExecutionStatus.QUEUED)
        assertThat(retried.nextAttemptAt).isNotNull()
        assertThat(retried.errorType).isEqualTo(ImagingUnavailableException::class.java.name)
        assertThat(files.findByPath(key).orElseThrow().renditions).isEmpty()
    }

    /** The converter pointed at a port nothing listens on, in place of the running service. */
    @TestConfiguration(proxyBeanMethods = false)
    class Down {
        @Bean
        @Primary
        fun downConverter(scratch: ScratchSpace): ImageConverter = ImagingServiceConverter("http://127.0.0.1:1", scratch)
    }
}
