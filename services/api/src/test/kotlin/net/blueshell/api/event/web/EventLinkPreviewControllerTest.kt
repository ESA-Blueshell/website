package net.blueshell.api.event.web

import net.blueshell.api.event.api.EventService
import net.blueshell.api.event.persistence.Event
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.thymeleaf.spring6.SpringTemplateEngine
import org.thymeleaf.templatemode.TemplateMode
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver
import java.time.Instant

class EventLinkPreviewControllerTest {
    private val events: EventService = mock()
    private val templates =
        SpringTemplateEngine().apply {
            setTemplateResolver(
                ClassLoaderTemplateResolver().apply {
                    prefix = "templates/"
                    suffix = ".html"
                    templateMode = TemplateMode.HTML
                },
            )
        }
    private val controller = EventLinkPreviewController(events, templates, "https://site.example", "https://site.example/api")

    @Test
    fun `answers the event's tags`() {
        whenever(events.findById(7)).thenReturn(event("LAN party"))

        val tags = controller.findLinkPreview(7)

        assertThat(tags)
            .contains("<title>LAN party — Blueshell Esports</title>")
            .contains("<meta property=\"og:title\" content=\"LAN party\">")
            .contains("<meta property=\"og:url\" content=\"https://site.example/events/7\">")
            .contains("<meta property=\"og:image\" content=\"https://site.example/banner.webp\">")
            .contains("<meta property=\"og:image:width\" content=\"3840\">")
            .doesNotContain("<!--")
    }

    @Test
    fun `escapes whatever the event says`() {
        whenever(events.findById(7)).thenReturn(event("\"><script>alert(1)</script>"))

        val tags = controller.findLinkPreview(7)

        assertThat(tags)
            .doesNotContain("<script>")
            .contains("content=\"&quot;&gt;&lt;script&gt;alert(1)&lt;/script&gt;\"")
    }

    private fun event(title: String) =
        Event(
            committee = null,
            title = title,
            startTime = Instant.parse("2026-09-12T18:00:00Z"),
            endTime = Instant.parse("2026-09-12T21:00:00Z"),
        ).apply { id = 7 }
}
