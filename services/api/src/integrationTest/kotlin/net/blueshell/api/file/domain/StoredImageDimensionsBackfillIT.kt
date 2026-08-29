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

/**
 * Recording how large the pictures already in storage are.
 *
 * The pictures that matter here are the ones stored before a size was ever recorded. There is
 * no way to create one of those through the api any more, so the test makes one the only way
 * it can exist in production: a stored picture whose size is then cleared.
 */
@SpringBootTest
class StoredImageDimensionsBackfillIT : UserTestSupport() {

    @Autowired
    private lateinit var files: FileRepository

    @Autowired
    private lateinit var backfill: StoredImageDimensionsBackfill

    private fun pngOf(width: Int, height: Int): ByteArray =
        ByteArrayOutputStream().also {
            ImageIO.write(BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), "png", it)
        }.toByteArray()

    /** Stores a picture the way one is actually stored, and answers with the record. */
    private fun storedPoster(width: Int, height: Int): Long {
        val admin = createUserWithRole(Role.ADMIN)
        val posted = mvc.perform(
            multipart(PublicFileUrls.UPLOAD)
                .file(MockMultipartFile("file", "poster.png", MediaType.IMAGE_PNG_VALUE, pngOf(width, height)))
                .param("type", FileType.TEAM_POSTER.name)
                .with(bearer(admin)).with(csrfToken()),
        ).andExpect(status().isCreated).andReturn()
        val path = mapper.readTree(posted.response.contentAsString)["path"].asText()
        return files.findByPath(path).orElseThrow().id!!
    }

    @Test
    fun `a stored picture with no recorded size is measured`() {
        val id = storedPoster(9, 4)

        val stored = files.findById(id).orElseThrow()
        stored.width = null
        stored.height = null
        files.save(stored)

        backfill.run()

        val measured = files.findById(id).orElseThrow()
        assertThat(measured.width).isEqualTo(9)
        assertThat(measured.height).isEqualTo(4)
    }

    @Test
    fun `a picture that has already been measured is left alone`() {
        val id = storedPoster(7, 2)

        assertThat(backfill.run()).isZero()

        val untouched = files.findById(id).orElseThrow()
        assertThat(untouched.width).isEqualTo(7)
        assertThat(untouched.height).isEqualTo(2)
    }

    /**
     * A record whose bytes are not a picture is passed over rather than failing the run. The
     * other pictures in the same pass are still measured, which is the point: one unreadable
     * file must not stop every other one from getting a size.
     */
    @Test
    fun `something that cannot be read is passed over and does not stop the rest`() {
        val admin = createUserWithRole(Role.ADMIN)
        val unreadable = fileFactory.create(admin, name = "not-a-picture.png")
        val id = storedPoster(5, 3)

        files.findById(id).orElseThrow().let {
            it.width = null
            it.height = null
            files.save(it)
        }

        backfill.run()

        assertThat(files.findById(id).orElseThrow().width).isEqualTo(5)
        assertThat(files.findById(unreadable.id!!).orElseThrow().width).isNull()
    }
}
