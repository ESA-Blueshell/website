package net.blueshell.api.file.domain

import net.blueshell.api.file.api.BlobStore
import net.blueshell.api.file.api.PublicFileUrls
import net.blueshell.api.shared.enums.FileType
import net.blueshell.api.shared.enums.Role
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
 * A still upload, converted by the real imaging service: the master and every width are stored
 * as WebP at the sizes the in-process converter drew them at.
 */
@SpringBootTest
class ImagingServiceIT : UserTestSupport() {
    @Autowired
    private lateinit var converter: ImageConverter

    @Autowired
    private lateinit var blobs: BlobStore

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

    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun imaging(registry: DynamicPropertyRegistry) {
            registry.add("app.imaging.url") { ImagingService.url }
        }
    }
}
