package net.blueshell.api.file.domain

import net.blueshell.api.file.api.BlobStore
import net.blueshell.api.file.api.PublicFileUrls
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
import org.springframework.http.MediaType
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

/**
 * Uploads converted by the real imaging service: a still's master and every width stored as WebP
 * at the sizes the in-process converter drew them at, and an animated banner kept animated at
 * every width.
 */
@SpringBootTest
class ImagingServiceIT : UserTestSupport() {
    @Autowired
    private lateinit var converter: ImageConverter

    @Autowired
    private lateinit var blobs: BlobStore

    @Autowired
    private lateinit var files: FileRepository

    @Autowired
    private lateinit var executor: JobExecutor

    @Autowired
    private lateinit var webpEncoder: WebpEncoder

    @Autowired
    private lateinit var scratch: ScratchSpace

    private fun picture(
        format: String,
        mediaType: String,
        width: Int,
        height: Int,
    ) = MockMultipartFile(
        "file",
        "picture.$format",
        mediaType,
        ByteArrayOutputStream()
            .also { out ->
                val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
                image.setRGB(0, 0, 0x80FF0000.toInt())
                ImageIO.write(if (format == "jpg") image.withoutAlpha() else image, format, out)
            }.toByteArray(),
    )

    private fun BufferedImage.withoutAlpha() =
        BufferedImage(width, height, BufferedImage.TYPE_INT_RGB).also { it.graphics.drawImage(this, 0, 0, null) }

    private fun upload(
        file: MockMultipartFile,
        type: FileType,
    ) = mapper.readTree(
        mvc
            .perform(
                multipart(PublicFileUrls.UPLOAD)
                    .file(file)
                    .param("type", type.name)
                    .with(signedIn(createUserWithRole(Role.ADMIN)))
                    .with(csrfToken()),
            ).andExpect(status().isCreated)
            .andReturn()
            .response.contentAsString,
    )

    private fun storedSize(path: String) = blobs.open(path).use { WebpDimensions.of(it) }

    @Test
    fun `the service converts, not the api`() {
        assertThat(converter).isInstanceOf(ImagingServiceConverter::class.java)
    }

    @Test
    fun `a photograph over the ceiling is stored as WebP under it, with every width`() {
        val stored = upload(picture("jpg", MediaType.IMAGE_JPEG_VALUE, 3000, 1200), FileType.EVENT_BANNER)

        assertThat(stored["width"].asInt()).isEqualTo(2560)
        assertThat(stored["height"].asInt()).isEqualTo(1024)
        assertThat(storedSize(stored["path"].asString())).isEqualTo(ImageDimensions.Size(2560, 1024))
        val renditions = stored["renditions"]
        assertThat(renditions.size()).isPositive()
        renditions.forEach { rendition ->
            val width = rendition["width"].asInt()
            val size = storedSize(rendition["url"].asString().substringAfter("${PublicFileUrls.PATH}/"))
            assertThat(size?.width).isEqualTo(width)
            assertThat(size?.height).isEqualTo(Math.round(width * 1024.0 / 2560).toInt())
        }
    }

    @Test
    fun `an icon keeps its size and is stored lossless`() {
        val stored = upload(picture("png", MediaType.IMAGE_PNG_VALUE, 300, 300), FileType.COMMITTEE_ICON)

        val path = stored["path"].asString()
        assertThat(storedSize(path)).isEqualTo(ImageDimensions.Size(300, 300))
        // VP8L is the lossless bitstream, which carries the alpha in itself.
        val header = blobs.open(path).use { it.readNBytes(16) }
        assertThat(String(header, 12, 4, Charsets.US_ASCII)).isEqualTo("VP8L")
    }

    @Test
    fun `an animated banner is stored animated, and its job writes every width animated`() {
        val stored = upload(gif(AnimatedGifs.patched(1000, 400)), FileType.EVENT_BANNER)
        val path = stored["path"].asString()

        assertThat(durationsOf(path)).containsExactly(120, 80, 200, 40)
        assertThat(stored["renditions"].size()).isZero()

        runRenditionJobOf(files.findByPath(path).orElseThrow().id!!)

        val widths = files.findByPath(path).orElseThrow().renditions
        assertThat(widths.map { it.renditionWidth }).containsExactlyInAnyOrder(160, 320, 480, 640, 960)
        widths.forEach { width ->
            assertThat(storedSize(width.path)?.width).isEqualTo(width.renditionWidth)
            assertThat(durationsOf(width.path)).describedAs("the frames of %s", width.path).containsExactly(120, 80, 200, 40)
        }
    }

    @Test
    fun `a banner whose frames will not all decode is stored as its first frame, with every width`() {
        val broken = withBrokenLastFrame(AnimatedGifs.patched(1000, 400))

        val stored = upload(gif(broken), FileType.EVENT_BANNER)

        assertThat(durationsOf(stored["path"].asString())).isNull()
        assertThat(storedSize(stored["path"].asString())).isEqualTo(ImageDimensions.Size(1000, 400))
        val renditions = stored["renditions"]
        assertThat((0 until renditions.size()).map { renditions[it]["width"].asInt() }).containsExactly(160, 320, 480, 640, 960)
    }

    private fun gif(bytes: ByteArray) = MockMultipartFile("file", "banner.gif", MediaType.IMAGE_GIF_VALUE, bytes)

    /** The frame durations of the WebP stored at [path], or nothing where it is a still. */
    private fun durationsOf(path: String) = scratch.hold(blobs.open(path)).use(webpEncoder::animationOf)?.durationsMillis

    private fun runRenditionJobOf(fileId: Long) {
        val payload = mapper.writeValueAsString(ImageJobs.DeriveRenditionsPayload(fileId))
        val job = jobExecutions.findAll().single { it.jobType == ImageJobs.DeriveRenditions.type && it.payload == payload }
        executor.execute(job)
        assertThat(jobExecutions.findById(job.id!!).orElseThrow().status).isEqualTo(JobExecutionStatus.SUCCESS)
    }

    /** [gif] with its last frame's LZW code size made one no GIF has, so that frame alone will not decode. */
    private fun withBrokenLastFrame(gif: ByteArray): ByteArray {
        val bytes = gif.copyOf()
        var at = 13 + colourTableOf(bytes[10])
        var last = -1
        while (bytes[at] != TRAILER) {
            if (bytes[at] == IMAGE) {
                at += 10 + colourTableOf(bytes[at + 9])
                last = at
                at = pastSubBlocks(bytes, at + 1)
            } else {
                at = pastSubBlocks(bytes, at + 2)
            }
        }
        bytes[last] = 12
        return bytes
    }

    private fun colourTableOf(packed: Byte) = if (packed.toInt() and 0x80 != 0) 3 shl ((packed.toInt() and 0x07) + 1) else 0

    private fun pastSubBlocks(
        bytes: ByteArray,
        from: Int,
    ): Int {
        var at = from
        while (bytes[at].toInt() != 0) at += 1 + (bytes[at].toInt() and 0xFF)
        return at + 1
    }

    companion object {
        private const val IMAGE: Byte = 0x2C
        private const val TRAILER: Byte = 0x3B

        @JvmStatic
        @DynamicPropertySource
        fun imaging(registry: DynamicPropertyRegistry) {
            registry.add("app.imaging.url") { ImagingService.url }
        }
    }
}
