package net.blueshell.api.file.domain

import net.blueshell.api.file.api.PublicFileUrls
import net.blueshell.api.file.persistence.FileRepository
import net.blueshell.api.shared.enums.FileType
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.testsupport.UserTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

/** With the imaging service down, a still upload is refused for now, and nothing of it is kept. */
@SpringBootTest(properties = ["app.imaging.url=http://127.0.0.1:1"])
class ImagingServiceDownIT : UserTestSupport() {
    @Autowired
    private lateinit var files: FileRepository

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
}
