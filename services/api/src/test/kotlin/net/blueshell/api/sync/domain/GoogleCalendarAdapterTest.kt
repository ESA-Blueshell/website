package net.blueshell.api.sync.domain

import net.blueshell.api.event.api.CalendarEventData
import net.blueshell.api.shared.discord.DiscordMentionNames
import net.blueshell.api.shared.discord.MentionNames
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import java.time.Instant

class GoogleCalendarAdapterTest {
    private val client: GoogleCalendarClient =
        mock { on { addEvent(any(), anyOrNull(), anyOrNull(), any(), any()) } doReturn GoogleCalendarEventResult("g1", null) }

    private fun adapter(names: DiscordMentionNames?) = GoogleCalendarAdapter(client, names ?: DiscordMentionNames { null })

    private fun event(description: String?) =
        CalendarEventData(
            title = "LAN party",
            location = null,
            description = description,
            startTime = Instant.parse("2026-10-10T18:00:00Z"),
            endTime = Instant.parse("2026-10-10T21:00:00Z"),
        )

    @Test
    fun `hands the calendar a description in Discord's markdown as CommonMark, mentions named and no spoiler`() {
        val names = DiscordMentionNames { MentionNames(users = mapOf("123456789012345611" to "Anna")) }

        adapter(names).addEvent(1, event("Ask <@123456789012345611> __now__: ||he wins||"))

        val said = argumentCaptor<String>()
        verify(client).addEvent(eq("LAN party"), anyOrNull(), said.capture(), any(), any())
        assertThat(said.firstValue).isEqualTo("Ask @Anna <u>now</u>: (spoiler)")
    }

    @Test
    fun `names nothing where Discord cannot be asked, and passes on an event without a description`() {
        adapter(null).updateEvent(1, "g1", event("Ask <@123456789012345611>"))
        adapter(null).updateEvent(2, "g2", event(null))

        verify(client).updateEvent(eq("g1"), eq("LAN party"), anyOrNull(), eq("Ask @unknown-user"), any(), any())
        verify(client).updateEvent(eq("g2"), eq("LAN party"), anyOrNull(), eq(null), any(), any())
    }
}
