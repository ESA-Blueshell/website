package net.blueshell.api.file.domain

import net.blueshell.api.file.api.PublicFileUrls
import net.blueshell.api.file.api.asImage
import net.blueshell.api.file.persistence.FileRepository
import net.blueshell.api.shared.enums.FileType
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.testsupport.UserTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
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
import java.util.concurrent.TimeUnit
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

    @Value($$"${storage.location}")
    private lateinit var storageLocation: String

    private val root: Path by lazy { Paths.get(storageLocation) }

    private fun jpegOf(width: Int, height: Int): ByteArray =
        java.io.ByteArrayOutputStream().also { out ->
            val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
            for (x in 0 until width) {
                for (y in 0 until height) {
                    image.setRGB(x, y, if ((x / 8 + y / 8) % 2 == 0) 0xff336699.toInt() else 0xffe6f0ff.toInt())
                }
            }
            ImageIO.write(image, "jpg", out)
        }.toByteArray()

    private fun upload(type: FileType, width: Int, height: Int): String {
        val admin = createUserWithRole(Role.ADMIN)
        val result = mvc.perform(
            multipart(PublicFileUrls.UPLOAD)
                .file(MockMultipartFile("file", "picture.jpg", MediaType.IMAGE_JPEG_VALUE, jpegOf(width, height)))
                .param("type", type.name)
                .with(bearer(admin)).with(csrfToken()),
        ).andExpect(status().isCreated).andReturn()
        return mapper.readTree(result.response.contentAsString)["path"].asText()
    }

    /** What a browser would get back, decoded, so a width that lies about itself fails here. */
    private fun servedWidth(url: String): Int {
        val bytes = mvc.perform(get(url)).andExpect(status().isOk).andReturn().response.contentAsByteArray
        val webp = Files.createTempFile("rendition-served-", ".webp")
        val png = Files.createTempFile("rendition-served-", ".png")
        try {
            Files.write(webp, bytes)
            val process = ProcessBuilder(listOf("dwebp", "-quiet", webp.toString(), "-o", png.toString()))
                .redirectErrorStream(true).start()
            val finished = process.waitFor(10, TimeUnit.SECONDS)
            val output = process.inputStream.bufferedReader().readText()
            assertThat(finished).describedAs(output).isTrue()
            assertThat(process.exitValue()).describedAs(output).isEqualTo(0)
            return (ImageIO.read(png.toFile()) ?: throw AssertionError("dwebp produced a file ImageIO could not read")).width
        } finally {
            Files.deleteIfExists(webp)
            Files.deleteIfExists(png)
        }
    }

    private fun widthsOf(path: String): List<Int> =
        fileRepository.findByPath(path).orElseThrow().renditions.mapNotNull { it.renditionWidth }

    @Test
    fun `a poster is stored at each width its kind lists, and at none wider than itself`() {
        val path = upload(FileType.TEAM_POSTER, 1000, 400)

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
        val path = upload(FileType.TEAM_POSTER, 1000, 400)

        val stored = fileRepository.findByPath(path).orElseThrow()
        assertThat(stored.renditions).isNotEmpty
        stored.renditions.forEach { copy ->
            assertThat(servedWidth(PublicFileUrls.of(copy))).isEqualTo(copy.renditionWidth)
        }
    }

    /** The payload carries them, so the pages can compose a `srcset` from what they are given. */
    @Test
    fun `the rendition list reaches the payload, narrowest first`() {
        val path = upload(FileType.TEAM_POSTER, 1000, 400)
        val image = fileRepository.findByPath(path).orElseThrow().asImage()

        assertThat(image.renditions.map { it.width }).containsExactly(320, 640, 960)
        assertThat(image.renditions.map { it.url })
            .allMatch { it.startsWith("/files/public/team-posters/") }
    }

    /**
     * The point of addressing a width by its source's hash rather than by its own bytes: a
     * width whose bytes have gone missing is written again to the address somebody is already
     * holding, so a lost storage volume repairs itself rather than invalidating every url.
     */
    @Test
    fun `a width whose bytes have gone missing is written again to the address it had`() {
        val path = upload(FileType.TEAM_POSTER, 1000, 400)
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
        val path = upload(FileType.TEAM_POSTER, 1000, 400)
        val source = fileRepository.findByPath(path).orElseThrow()

        // What a picture stored before this existed looks like: the record, the bytes, and no
        // copies. Removed through the repository so the source's own row is left as it was.
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

    /** A copy is not a picture somebody uploaded, so it is never given copies of its own. */
    @Test
    fun `a width is not itself stored at widths`() {
        val path = upload(FileType.TEAM_POSTER, 1000, 400)
        val copy = fileRepository.findByPath(path).orElseThrow().renditions.first()

        assertThat(renditions.derive(copy)).isEmpty()
        assertThat(copy.renditions).isEmpty()
    }
}
