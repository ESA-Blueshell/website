package net.blueshell.api.event.domain

import net.blueshell.api.event.persistence.Event
import net.blueshell.api.event.persistence.EventBanner
import net.blueshell.api.file.api.Image
import net.blueshell.api.file.api.ImageRendition
import net.blueshell.api.file.persistence.File
import net.blueshell.api.shared.enums.FileType
import net.blueshell.api.user.persistence.User
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import java.time.Instant

class EventLinkPreviewTest {
    @Nested
    inner class TheText {
        @Test
        fun `says the day and hours in Amsterdam time, then where`() {
            val preview = event(location = "Horst").linkPreview(FRONTEND, API)

            assertThat(preview.text).isEqualTo("Sat 12 September 2026, 20:00-23:00 · Horst")
        }

        @Test
        fun `names the day it ends when that is another day`() {
            val preview =
                event(end = Instant.parse("2026-09-13T00:30:00Z")).linkPreview(FRONTEND, API)

            assertThat(preview.text).isEqualTo("Sat 12 September 2026, 20:00 to Sun 13 September, 02:30")
        }

        @Test
        fun `says members only, and leaves out a blank location`() {
            val preview = event(location = " ", membersOnly = true).linkPreview(FRONTEND, API)

            assertThat(preview.text).isEqualTo("Sat 12 September 2026, 20:00-23:00 · Members only")
        }

        @Test
        fun `ends with the description as plain text`() {
            val preview =
                event(description = "# Come **play**\n\nWith [us](https://x.nl), `code`\nand <b>html</b>.").linkPreview(FRONTEND, API)

            assertThat(preview.text).endsWith(" · Come play With us, code and html.")
        }

        @Test
        fun `cuts a long description at a word`() {
            val preview = event(description = "word, ".repeat(60)).linkPreview(FRONTEND, API)
            val snippet = preview.text.substringAfter(" · ")

            assertThat(snippet).endsWith("word…").hasSizeLessThanOrEqualTo(161)
        }

        @Test
        fun `leaves out a description with no words`() {
            val preview = event(description = "  \n ").linkPreview(FRONTEND, API)

            assertThat(preview.text).isEqualTo("Sat 12 September 2026, 20:00-23:00")
        }
    }

    @Nested
    inner class TheLinkAndPicture {
        @Test
        fun `points at the event page, titled with the event`() {
            val preview = event().linkPreview(FRONTEND, API)

            assertThat(preview.title).isEqualTo("LAN party")
            assertThat(preview.url).isEqualTo("$FRONTEND/events/42")
        }

        @Test
        fun `falls back to the site banner`() {
            val preview = event().linkPreview(FRONTEND, API)

            assertThat(preview.image).isEqualTo(LinkPreviewImage("$FRONTEND/banner.webp", 3840, 2560))
        }

        @Test
        fun `shows the banner at an absolute url`() {
            val preview = event().apply { banner = bannerOf(this) }.linkPreview(FRONTEND, API)

            assertThat(preview.image).isEqualTo(LinkPreviewImage("$API/files/public/event-banners/art.webp", 1000, 500))
        }

        @Test
        fun `takes the widest copy a card needs`() {
            val copy = previewCopyOf(image(width = 2560, height = 1280, 640, 1280, 1920), API)

            assertThat(copy).isEqualTo(LinkPreviewImage("$API/copy-1280.webp", 1280, 640))
        }

        @Test
        fun `takes the master where no copy is narrow enough`() {
            val copy = previewCopyOf(image(width = 2560, height = null, 1920), API)

            assertThat(copy).isEqualTo(LinkPreviewImage("$API/master.webp", 2560, null))
        }

        @Test
        fun `leaves the height out of a copy of a master whose size is unknown`() {
            val copy = previewCopyOf(image(width = null, height = null, 1280), API)

            assertThat(copy).isEqualTo(LinkPreviewImage("$API/copy-1280.webp", 1280, null))
        }
    }

    private fun event(
        location: String? = null,
        description: String? = null,
        membersOnly: Boolean = false,
        end: Instant = Instant.parse("2026-09-12T21:00:00Z"),
    ) = Event(
        committee = null,
        title = "LAN party",
        description = description,
        location = location,
        startTime = Instant.parse("2026-09-12T18:00:00Z"),
        endTime = end,
        membersOnly = membersOnly,
    ).apply { id = 42 }

    private fun bannerOf(event: Event) =
        EventBanner(
            event = event,
            file =
                File(
                    name = "art.webp",
                    path = "event-banners/art.webp",
                    uploader = mock<User>(),
                    mediaType = "image/webp",
                    width = 1000,
                    height = 500,
                    type = FileType.EVENT_BANNER,
                ),
        )

    private fun image(
        width: Int?,
        height: Int?,
        vararg copies: Int,
    ) = Image(
        url = "/master.webp",
        path = "master.webp",
        width = width,
        height = height,
        renditions = copies.map { ImageRendition(url = "/copy-$it.webp", width = it) },
    )

    private companion object {
        const val FRONTEND = "https://site.example"
        const val API = "https://site.example/api"
    }
}
