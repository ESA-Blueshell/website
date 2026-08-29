package net.blueshell.api.file.web

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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * The one endpoint that stores a picture meant to be seen.
 *
 * It is deliberately not tied to what the picture ends up on. Storing and applying are
 * separate so that a dialog can show what was chosen and still leave the record alone when
 * somebody cancels — the applying is asserted where the records are, in EsportsMediaIT.
 */
@SpringBootTest
class PublicImageUploadIT : UserTestSupport() {
    @Autowired
    private lateinit var fileRepository: FileRepository

    /** A one-pixel PNG: the smallest thing that is genuinely the content type it claims. */
    private val pngBytes = java.util.Base64.getDecoder().decode(
        "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==",
    )

    private fun png(name: String = "picture.png") =
        MockMultipartFile("file", name, MediaType.IMAGE_PNG_VALUE, pngBytes)

    private fun jpegOf(width: Int, height: Int) =
        MockMultipartFile(
            "file",
            "picture.jpg",
            MediaType.IMAGE_JPEG_VALUE,
            java.io.ByteArrayOutputStream().also { out ->
                val image = java.awt.image.BufferedImage(width, height, java.awt.image.BufferedImage.TYPE_INT_RGB)
                javax.imageio.ImageIO.write(image, "jpg", out)
            }.toByteArray(),
        )

    /**
     * The answer carries the widths as well as the picture, so a picker can draw what was
     * chosen at the size it will actually be drawn — before anything has been saved.
     */
    @Test
    fun `the answer carries the widths the picture is stored at`() {
        val admin = createUserWithRole(Role.ADMIN)

        mvc.perform(
            multipart(PublicFileUrls.UPLOAD).file(jpegOf(1000, 400))
                .param("type", FileType.TEAM_POSTER.name)
                .with(bearer(admin)).with(csrfToken()),
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.width").value(1000))
            .andExpect(jsonPath("$.height").value(400))
            .andExpect(jsonPath("$.renditions[*].width").value(org.hamcrest.Matchers.contains(320, 640, 960)))
    }

    @Test
    fun `somebody who may edit esports is told where their picture is stored`() {
        val admin = createUserWithRole(Role.ADMIN)

        val result = mvc.perform(
            multipart(PublicFileUrls.UPLOAD).file(png())
                .param("type", FileType.TEAM_POSTER.name)
                .with(bearer(admin)).with(csrfToken()),
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.path").value(org.hamcrest.Matchers.startsWith("team-posters/")))
            .andExpect(jsonPath("$.url").value(org.hamcrest.Matchers.startsWith("/files/public/team-posters/")))
            .andReturn()

        // Where it says it is, is where it is: the picture can be fetched straight away, by
        // somebody who is not signed in, before anything has been saved.
        val path = mapper.readTree(result.response.contentAsString)["path"].asText()
        mvc.perform(get("/files/public/$path")).andExpect(status().isOk)
    }

    /**
     * The endpoint exists to put pictures on public pages, so it admits only kinds that are
     * public. Otherwise it would be a way to put a private document behind a route anybody can
     * fetch from.
     */
    @Test
    fun `a kind that is not publicly readable is refused`() {
        val admin = createUserWithRole(Role.ADMIN)

        mvc.perform(
            multipart(PublicFileUrls.UPLOAD).file(png())
                .param("type", FileType.DOCUMENT.name)
                .with(bearer(admin)).with(csrfToken()),
        ).andExpect(status().isBadRequest)

        assertThat(fileRepository.findAll().filter { it.type == FileType.DOCUMENT }).isEmpty()
    }

    @Test
    fun `somebody who may not edit esports is refused`() {
        val member = createUserWithRole(Role.MEMBER)

        mvc.perform(
            multipart(PublicFileUrls.UPLOAD).file(png())
                .param("type", FileType.TEAM_POSTER.name)
                .with(bearer(member)).with(csrfToken()),
        ).andExpect(status().isForbidden)
    }

    @Test
    fun `a visitor who is not signed in is refused`() {
        mvc.perform(
            multipart(PublicFileUrls.UPLOAD).file(png())
                .param("type", FileType.TEAM_POSTER.name)
                .with(csrfToken()),
        ).andExpect(status().isUnauthorized)
    }

    @Test
    fun `a picture has to be an image`() {
        val admin = createUserWithRole(Role.ADMIN)
        val pdf = MockMultipartFile("file", "poster.pdf", MediaType.APPLICATION_PDF_VALUE, pngBytes)

        mvc.perform(
            multipart(PublicFileUrls.UPLOAD).file(pdf)
                .param("type", FileType.TEAM_POSTER.name)
                .with(bearer(admin)).with(csrfToken()),
        ).andExpect(status().isUnsupportedMediaType)
    }

    /** The endpoints that uploaded or cleared one image on one record no longer exist. */
    @Test
    fun `the per-record upload endpoints are gone`() {
        val admin = createUserWithRole(Role.ADMIN)

        for (path in listOf("/esports/teams/1/poster", "/esports/roster/1/icon")) {
            mvc.perform(multipart(path).file(png()).with(bearer(admin)).with(csrfToken()))
                .andExpect(status().isNotFound)
        }
        // The banner endpoint is still there, but it names a stored picture rather than
        // carrying one, so a multipart body is no longer something it knows how to read.
        mvc.perform(
            multipart("/esports/banners").file(png()).param("game", "VALORANT")
                .with(bearer(admin)).with(csrfToken()),
        ).andExpect(status().isUnsupportedMediaType)
    }
}
