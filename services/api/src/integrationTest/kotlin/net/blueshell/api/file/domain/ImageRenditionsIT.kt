package net.blueshell.api.file.domain

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import net.blueshell.api.file.api.BlobStore
import net.blueshell.api.file.api.PublicFileUrls
import net.blueshell.api.file.api.asImage
import net.blueshell.api.file.persistence.File
import net.blueshell.api.file.persistence.FileRepository
import net.blueshell.api.shared.enums.FileType
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.testsupport.AnimatedGifs
import net.blueshell.api.testsupport.UserTestSupport
import net.blueshell.api.testsupport.WebpFrames
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import javax.imageio.ImageIO

/**
 * A picture is stored at a ladder of widths, so a phone takes a small one and a desktop takes
 * a large one and neither pays for the other's.
 *
 * Proven against the real converter and the real bytes rather than against how the encoder was
 * called: the assertions here are what a visitor's browser would receive and decode, which is
 * the only thing the ladder exists to change.
 */
@SpringBootTest
class ImageRenditionsIT : UserTestSupport() {
    @Autowired
    private lateinit var fileRepository: FileRepository

    @Autowired
    private lateinit var renditions: ImageRenditionWriter

    @Autowired
    private lateinit var backfill: StoredImageRenditionsBackfill

    @Autowired
    private lateinit var imageRenditions: ImageRenditions

    @Autowired
    private lateinit var blobs: BlobStore

    @Value($$"${storage.location}")
    private lateinit var storageLocation: String

    private val root: Path by lazy { Paths.get(storageLocation) }

    private fun jpegOf(
        width: Int,
        height: Int,
    ): ByteArray =
        java.io
            .ByteArrayOutputStream()
            .also { out ->
                val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
                for (x in 0 until width) {
                    for (y in 0 until height) {
                        image.setRGB(x, y, if ((x / 8 + y / 8) % 2 == 0) 0xff336699.toInt() else 0xffe6f0ff.toInt())
                    }
                }
                ImageIO.write(image, "jpg", out)
            }.toByteArray()

    private fun upload(
        type: FileType,
        width: Int,
        height: Int,
    ): String {
        val admin = createUserWithRole(Role.ADMIN)
        val result =
            mvc
                .perform(
                    multipart(PublicFileUrls.UPLOAD)
                        .file(MockMultipartFile("file", "picture.jpg", MediaType.IMAGE_JPEG_VALUE, jpegOf(width, height)))
                        .param("type", type.name)
                        .with(signedIn(admin))
                        .with(csrfToken()),
                ).andExpect(status().isCreated)
                .andReturn()
        return mapper.readTree(result.response.contentAsString)["path"].asString()
    }

    /** The width a browser would be told, read from the served header, so a width that lies about itself fails here. */
    private fun servedWidth(url: String): Int = WebpDimensions.of(served(url))?.width ?: throw AssertionError("$url is not a WebP")

    private fun widthsOf(path: String): List<Int> =
        fileRepository
            .findByPath(path)
            .orElseThrow()
            .renditions
            .mapNotNull { it.renditionWidth }

    @Test
    fun `a poster is stored at each width its kind lists, and at none wider than itself`() {
        val path = upload(FileType.TEAM_BANNER, 1000, 400)

        // 1280 and up are wider than the picture, and nothing is upscaled.
        assertThat(widthsOf(path)).containsExactly(320, 640, 960)
    }

    @Test
    fun `an icon is stored at the widths its own kind lists`() {
        val path = upload(FileType.ROSTER_ICON, 400, 400)

        assertThat(widthsOf(path)).containsExactly(128, 256)
    }

    @Test
    fun `each width is fetchable and decodes to the width its address claims`() {
        val path = upload(FileType.TEAM_BANNER, 1000, 400)

        val stored = fileRepository.findByPath(path).orElseThrow()
        assertThat(stored.renditions).isNotEmpty
        stored.renditions.forEach { copy ->
            assertThat(servedWidth(PublicFileUrls.of(copy))).isEqualTo(copy.renditionWidth)
        }
    }

    /** The payload carries them, so the pages can compose a `srcset` from what they are given. */
    @Test
    fun `the rendition list reaches the payload, narrowest first`() {
        val path = upload(FileType.TEAM_BANNER, 1000, 400)
        val image = fileRepository.findByPath(path).orElseThrow().asImage()

        assertThat(image.renditions.map { it.width }).containsExactly(320, 640, 960)
        assertThat(image.renditions.map { it.url })
            .allMatch { it.startsWith("/files/public/team-banners/") }
    }

    /**
     * The point of addressing a width by its source's hash rather than by its own bytes: a
     * width whose bytes have gone missing is written again to the address somebody is already
     * holding, so a lost storage volume repairs itself rather than invalidating every url.
     */
    @Test
    fun `a width whose bytes have gone missing is written again to the address it had`() {
        val path = upload(FileType.TEAM_BANNER, 1000, 400)
        val source = fileRepository.findByPath(path).orElseThrow()
        val copy = source.renditions.first()
        val bytes = root.resolve(copy.path).normalize()

        Files.delete(bytes)
        mvc.perform(get(PublicFileUrls.of(copy))).andExpect(status().isNotFound)

        backfill.run()

        assertThat(Files.exists(bytes)).isTrue()
        assertThat(servedWidth(PublicFileUrls.of(copy))).isEqualTo(copy.renditionWidth)
    }

    /**
     * Pictures stored before the ladder existed gain their widths without anybody re-uploading
     * them, and a second pass writes nothing new — which is what lets this run on every start.
     */
    @Test
    fun `a picture stored without widths gains them, and a second pass adds none`() {
        val path = upload(FileType.TEAM_BANNER, 1000, 400)
        val source = fileRepository.findByPath(path).orElseThrow()

        // A picture with no copies: the record and the bytes alone. Removed through the
        // repository, so the source's own row is left as it was.
        val before = source.renditions.toList()
        before.forEach { copy ->
            Files.deleteIfExists(root.resolve(copy.path).normalize())
            fileRepository.delete(copy)
        }

        assertThat(backfill.run()).isEqualTo(before.size)
        assertThat(widthsOf(path)).containsExactly(320, 640, 960)

        val again = renditions.derive(fileRepository.findByPath(path).orElseThrow())
        assertThat(again.map { it.renditionWidth }).containsExactly(320, 640, 960)
        assertThat(widthsOf(path)).containsExactly(320, 640, 960)
    }

    /**
     * A committee posts an animation and the site serves an animation, at whatever width the
     * page asks for. Half a ladder of stills would be the banner freezing on a phone and
     * moving on a desktop, which is a difference a visitor can see and cannot explain.
     */
    @Test
    fun `a banner that moves is stored at every width, and every width still moves`() {
        val path = uploadGif(AnimatedGifs.patched(1000, 400))
        val source = fileRepository.findByPath(path).orElseThrow()

        assertThat(storedDurations(source.path)).containsExactly(120, 80, 200, 40)

        renditions.derive(source)

        // 1280 and up are wider than the picture, and nothing is upscaled.
        assertThat(widthsOf(path)).containsExactly(160, 320, 480, 640, 960)
        fileRepository.findByPath(path).orElseThrow().renditions.forEach { copy ->
            val served = served(PublicFileUrls.of(copy))
            assertThat(WebpDimensions.isAnimated(served)).describedAs("%s moves", copy.path).isTrue()
            assertThat(WebpDimensions.of(served)?.width).isEqualTo(copy.renditionWidth)
            assertThat(storedDurations(copy.path))
                .describedAs("the frames of %s", copy.path)
                .containsExactly(120, 80, 200, 40)
        }
    }

    private fun served(url: String): ByteArray =
        mvc
            .perform(get(url))
            .andExpect(status().isOk)
            .andReturn()
            .response.contentAsByteArray

    /**
     * One converter run per frame per width is not something an upload should hold open, so
     * the work is a job row that can fail, be retried and be read about on its own.
     */
    @Test
    fun `a banner that moves is queued rather than converted while somebody waits`() {
        val path = uploadGif(AnimatedGifs.patched(1000, 400))

        assertThat(widthsOf(path)).isEmpty()
        assertThat(jobExecutions.findAll().map { it.jobType }).contains(ImageJobs.DeriveRenditions.type)
    }

    /** A GIF of one frame is a still, whatever the still converter makes of the format. */
    @Test
    fun `a gif of one frame is accepted and stored at its widths`() {
        val path = uploadGif(AnimatedGifs.single(1000, 400))
        val source = fileRepository.findByPath(path).orElseThrow()

        assertThat(source.mediaType).isEqualTo("image/webp")
        assertThat(storedDurations(source.path)).isNull()

        renditions.derive(source)

        assertThat(widthsOf(path)).containsExactly(160, 320, 480, 640, 960)
    }

    /**
     * A picture the converter will not take is reported once, without a stack trace.
     *
     * The backfill runs on every start and the refusal is not recorded, so a line per width per
     * start is how a log stops being read, which is the state this replaced.
     */
    @Test
    fun `a picture the converter refuses is reported once, and without a stack trace`() {
        val source = storedGarbage()
        val appender = capture()
        try {
            assertThat(renditions.derive(source)).isEmpty()

            val refusals = appender.list.filter { it.formattedMessage.contains("the converter refused") }
            assertThat(refusals).hasSize(1)
            assertThat(refusals.single().formattedMessage).contains(source.path, "320px", "640px", "960px")
            assertThat(refusals.single().throwableProxy).isNull()
        } finally {
            (LoggerFactory.getLogger(ImageRenditionWriter::class.java) as Logger).detachAppender(appender)
        }
    }

    private fun capture(): ListAppender<ILoggingEvent> =
        ListAppender<ILoggingEvent>().also { appender ->
            appender.start()
            (LoggerFactory.getLogger(ImageRenditionWriter::class.java) as Logger).addAppender(appender)
        }

    /**
     * A record whose bytes no converter will read, with a size on it so the ladder is attempted.
     *
     * Stored as `image/png`, which is the shape of the problem: the record says a picture and
     * the bytes are not one, so every width is refused and none of them says anything new.
     */
    private fun storedGarbage(): File {
        val key = StoredFileNames.keyOf(FileType.TEAM_BANNER.directory, "not-a-picture.png")
        blobs.put(key, "not a picture".byteInputStream())
        return fileRepository.save(
            File(
                name = "not-a-picture.png",
                path = key,
                uploader = createUserWithRole(Role.ADMIN),
                mediaType = "image/png",
                size = 13,
                width = 1000,
                height = 400,
                type = FileType.TEAM_BANNER,
            ),
        )
    }

    /** How long each frame stored at [key] is shown, or nothing where it is a still. */
    private fun storedDurations(key: String): List<Int>? = blobs.open(key).use { WebpFrames.durationsOf(it.readAllBytes()) }

    private fun uploadGif(bytes: ByteArray): String {
        val admin = createUserWithRole(Role.ADMIN)
        val result =
            mvc
                .perform(
                    multipart(PublicFileUrls.UPLOAD)
                        .file(MockMultipartFile("file", "banner.gif", MediaType.IMAGE_GIF_VALUE, bytes))
                        .param("type", FileType.EVENT_BANNER.name)
                        .with(signedIn(admin))
                        .with(csrfToken()),
                ).andExpect(status().isCreated)
                .andReturn()
        return mapper.readTree(result.response.contentAsString)["path"].asString()
    }

    /** A copy is not a picture somebody uploaded, so it is never given copies of its own. */
    @Test
    fun `a width is not itself stored at widths`() {
        val path = upload(FileType.TEAM_BANNER, 1000, 400)
        val copy =
            fileRepository
                .findByPath(path)
                .orElseThrow()
                .renditions
                .first()

        assertThat(renditions.derive(copy)).isEmpty()
        assertThat(copy.renditions).isEmpty()
    }
}
