package net.blueshell.api.file.web

import net.blueshell.api.file.api.BlobStore
import net.blueshell.api.file.api.PublicFileUrls
import net.blueshell.api.file.persistence.File
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

    @Autowired
    private lateinit var blobs: BlobStore

    /** A one-pixel PNG: the smallest thing that is genuinely the content type it claims. */
    private val pngBytes = java.util.Base64.getDecoder().decode(
        "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==",
    )

    private fun png(name: String = "picture.png") =
        MockMultipartFile("file", name, MediaType.IMAGE_PNG_VALUE, pngBytes)

    /** A logo of shapes and flat colour, which is what a vector icon is for. */
    private val LOGO =
        """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24"><path d="M2 2h20v20H2z"/></svg>"""

    private fun svg(document: String, name: String = "logo.svg") =
        MockMultipartFile("file", name, "image/svg+xml", document.toByteArray())

    private fun sha256(document: String): String =
        java.security.MessageDigest.getInstance("SHA-256")
            .digest(document.toByteArray())
            .joinToString("") { "%02x".format(it) }

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
                .param("type", FileType.TEAM_BANNER.name)
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
                .param("type", FileType.TEAM_BANNER.name)
                .with(bearer(admin)).with(csrfToken()),
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.path").value(org.hamcrest.Matchers.startsWith("team-banners/")))
            .andExpect(jsonPath("$.url").value(org.hamcrest.Matchers.startsWith("/files/public/team-banners/")))
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
                .param("type", FileType.TEAM_BANNER.name)
                .with(bearer(member)).with(csrfToken()),
        ).andExpect(status().isForbidden)
    }

    @Test
    fun `a visitor who is not signed in is refused`() {
        mvc.perform(
            multipart(PublicFileUrls.UPLOAD).file(png())
                .param("type", FileType.TEAM_BANNER.name)
                .with(csrfToken()),
        ).andExpect(status().isUnauthorized)
    }

    @Test
    fun `a picture has to be an image`() {
        val admin = createUserWithRole(Role.ADMIN)
        val pdf = MockMultipartFile("file", "poster.pdf", MediaType.APPLICATION_PDF_VALUE, pngBytes)

        mvc.perform(
            multipart(PublicFileUrls.UPLOAD).file(pdf)
                .param("type", FileType.TEAM_BANNER.name)
                .with(bearer(admin)).with(csrfToken()),
        ).andExpect(status().isUnsupportedMediaType)
    }

    /**
     * A logo may be a vector, and one is kept exactly as it was handed over.
     *
     * Byte-for-byte and addressed by the hash of those bytes: no conversion, no re-encoding and
     * no ladder, because a browser scales a vector and a ladder of widths is what a resolution
     * needs. An icon that arrives as a bitmap is asserted a few tests below; this adds a format
     * rather than replacing one.
     */
    @Test
    fun `a game icon may be a vector, stored as it arrived and addressed by its own bytes`() {
        val admin = createUserWithRole(Role.ADMIN)

        val result = mvc.perform(
            multipart(PublicFileUrls.UPLOAD).file(svg(LOGO))
                .param("type", FileType.GAME_ICON.name)
                .with(bearer(admin)).with(csrfToken()),
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.path").value("game-icons/${sha256(LOGO)}.svg"))
            .andExpect(jsonPath("$.renditions").isEmpty)
            .andReturn()

        val path = mapper.readTree(result.response.contentAsString)["path"].asText()
        val served = mvc.perform(get("/files/public/$path")).andExpect(status().isOk).andReturn().response

        assertThat(served.contentAsByteArray).isEqualTo(LOGO.toByteArray())
        assertThat(served.contentType).startsWith("image/svg+xml")
    }

    @Test
    fun `a vector an icon has no use for is refused with the reason`() {
        val admin = createUserWithRole(Role.ADMIN)
        val refusals = mapOf(
            """<svg xmlns="http://www.w3.org/2000/svg"><script>alert(1)</script></svg>"""
                to "That SVG contains a script, which an icon cannot.",
            """<svg xmlns="http://www.w3.org/2000/svg" onload="alert(1)"/>"""
                to "That SVG carries the event handler onload, which an icon cannot.",
            """<svg xmlns="http://www.w3.org/2000/svg"><foreignObject width="1" height="1"/></svg>"""
                to "That SVG contains a foreignObject, which an icon cannot.",
            """<svg xmlns="http://www.w3.org/2000/svg"><image href="https://elsewhere.example/x"/></svg>"""
                to "That SVG points at something outside itself, which an icon cannot.",
        )

        for ((document, reason) in refusals) {
            mvc.perform(
                multipart(PublicFileUrls.UPLOAD).file(svg(document))
                    .param("type", FileType.TEAM_ICON.name)
                    .with(bearer(admin)).with(csrfToken()),
            )
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.detail").value(reason))
        }

        assertThat(fileRepository.findAll().filter { it.type == FileType.TEAM_ICON }).isEmpty()
    }

    /** A claim is not a fact, and the bytes are what is stored and served. */
    @Test
    fun `a file claiming to be a vector whose bytes are not one is refused`() {
        val admin = createUserWithRole(Role.ADMIN)

        mvc.perform(
            multipart(PublicFileUrls.UPLOAD).file(svg("<html><body>not a logo</body></html>"))
                .param("type", FileType.GAME_ICON.name)
                .with(bearer(admin)).with(csrfToken()),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.detail").value("That file is not an SVG."))
    }

    /** A banner is a photograph. Only a logo is line and flat colour, so only a logo is vector. */
    @Test
    fun `a banner cannot be a vector`() {
        val admin = createUserWithRole(Role.ADMIN)

        for (kind in listOf(FileType.GAME_BANNER, FileType.TEAM_BANNER, FileType.EVENT_BANNER)) {
            mvc.perform(
                multipart(PublicFileUrls.UPLOAD).file(svg(LOGO))
                    .param("type", kind.name)
                    .with(bearer(admin)).with(csrfToken()),
            ).andExpect(status().isUnsupportedMediaType)
        }
    }

    /** The ladder is still there for the format that needs one. */
    @Test
    fun `an icon that arrives as a bitmap keeps its widths`() {
        val admin = createUserWithRole(Role.ADMIN)

        mvc.perform(
            multipart(PublicFileUrls.UPLOAD).file(jpegOf(600, 600))
                .param("type", FileType.GAME_ICON.name)
                .with(bearer(admin)).with(csrfToken()),
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.url").value(org.hamcrest.Matchers.endsWith(".webp")))
            .andExpect(jsonPath("$.renditions[*].width").value(org.hamcrest.Matchers.contains(128, 256, 512)))
    }

    /**
     * The defence that holds whatever the check above missed.
     *
     * Asserted against a file that really does contain a script, put into storage the way a
     * check that had been walked around would have left it there — the upload refuses this
     * document, and the point is what happens when one like it is served anyway. The bytes come
     * back unchanged, because nothing is rewritten; what stops the browser is the policy.
     */
    @Test
    fun `a stored vector carrying a script is served under a policy that runs none of it`() {
        val admin = createUserWithRole(Role.ADMIN)
        val hostile = """<svg xmlns="http://www.w3.org/2000/svg" onload="fetch('/x')">""" +
            """<script>alert(document.cookie)</script></svg>"""
        val path = "game-icons/${sha256(hostile)}.svg"
        blobs.put(path, hostile.byteInputStream())
        fileRepository.save(
            File(
                name = "logo.svg",
                path = path,
                uploader = admin,
                mediaType = "image/svg+xml",
                size = hostile.toByteArray().size.toLong(),
                type = FileType.GAME_ICON,
            ),
        )

        val served = mvc.perform(get("/files/public/$path")).andExpect(status().isOk).andReturn().response

        assertThat(served.contentAsByteArray).isEqualTo(hostile.toByteArray())
        val directives = served.getHeader("Content-Security-Policy")!!
            .split(';').map(String::trim).filter(String::isNotEmpty)
        assertThat(directives).contains("default-src 'none'", "sandbox")
        assertThat(directives).noneMatch { it.contains("allow-scripts") }
        assertThat(served.getHeader("X-Content-Type-Options")).isEqualTo("nosniff")
    }

    /** The endpoints that uploaded or cleared one image on one record no longer exist. */
    @Test
    fun `the per-record upload endpoints are gone`() {
        val admin = createUserWithRole(Role.ADMIN)

        // The banner endpoints went with the per-season banners they existed to manage. A
        // game's picture and a team's are now fields of the writes that save the game and the
        // team, and both name a picture the one upload endpoint already stored.
        val gone = listOf("/esports/teams/1/poster", "/esports/roster/1/icon", "/esports/banners")
        for (path in gone) {
            mvc.perform(multipart(path).file(png()).with(bearer(admin)).with(csrfToken()))
                .andExpect(status().isNotFound)
        }
    }
}
