package net.blueshell.api.file.web

import net.blueshell.api.file.persistence.FileRepository
import net.blueshell.api.shared.enums.FileType
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.testsupport.UserTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.hamcrest.Matchers.containsString
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import net.blueshell.api.factory.event.web.request.EventRequestFactory

@SpringBootTest
class FileControllerIT : UserTestSupport() {
    @Autowired
    private lateinit var eventRequestFactory: EventRequestFactory


    @Autowired
    private lateinit var fileRepository: FileRepository

    @Nested
    inner class DownloadEventBanner {

        @Test
        fun `downloads event banner`() {
            val board = createUserWithRole(Role.BOARD)
            val event = createEventFixture()
            attachEventBanner(event)

            mvc.perform(
                get("/events/{eventId}/banners", event.id)
                    .with(bearer(board))
            )
                .andExpect(status().isOk)
                .andExpect(content().contentType("image/png"))
                .andExpect(header().string("Content-Disposition", containsString("banner.png")))
        }

        @Test
        fun `returns not found when event has no banner`() {
            val board = createUserWithRole(Role.BOARD)
            val event = createEventFixture()

            mvc.perform(
                get("/events/{eventId}/banners", event.id)
                    .with(bearer(board))
            )
                .andExpect(status().isNotFound)
        }
    }

    @Nested
    inner class UploadEventBanner {

        /**
         * A banner is stored the way every picture a public page draws is stored.
         *
         * Converted rather than kept as sent, so what comes back is a WebP at a public
         * address, with the widths it is served at beside it.
         */
        @Test
        fun `uploads event banner`() {
            val committee = createUserWithRole(Role.COMMITTEE)
            val file = eventRequestFactory.eventBannerMultipart(
                content = eventRequestFactory.pngBytes(width = 1000, height = 400),
            )

            val result = mvc.perform(
                multipart("/events/banners")
                    .file(file)
                    .with(bearer(committee))
            )
                .andExpect(status().isCreated)
                .andExpect(jsonPath("$.id").isNumber)
                .andExpect(jsonPath("$.mediaType").value("image/webp"))
                .andExpect(jsonPath("$.type").value("EVENT_BANNER"))
                .andReturn()

            val id = mapper.readTree(result.response.contentAsByteArray).path("id").asLong()
            val persisted = fileRepository.findById(id).orElseThrow()
            assertThat(persisted.type).isEqualTo(FileType.EVENT_BANNER)
            assertThat(persisted.mediaType).isEqualTo("image/webp")
            assertThat(persisted.path).startsWith("event-banners/")
            assertThat(persisted.width).isEqualTo(1000)
            assertThat(persisted.height).isEqualTo(400)
            assertThat(persisted.renditions.mapNotNull { it.renditionWidth }).containsExactly(320, 640, 960)
        }

        @Test
        fun `returns bad request for unsupported media type`() {
            val committee = createUserWithRole(Role.COMMITTEE)
            val file = MockMultipartFile(
                "file",
                "banner.txt",
                "text/plain",
                "plain-text".toByteArray()
            )

            mvc.perform(
                multipart("/events/banners")
                    .file(file)
                    .with(bearer(committee))
            )
                .andExpect(status().isBadRequest)
        }

        @Test
        fun `returns bad request for oversized file`() {
            val committee = createUserWithRole(Role.COMMITTEE)
            val oversized = ByteArray(10 * 1024 * 1024 + 1) { 1 }
            val file = MockMultipartFile(
                "file",
                "large.png",
                "image/png",
                oversized
            )

            mvc.perform(
                multipart("/events/banners")
                    .file(file)
                    .with(bearer(committee))
            )
                .andExpect(status().isBadRequest)
        }
    }
}
