package net.blueshell.api.sync.domain

import com.google.api.client.util.DateTime
import com.google.api.services.calendar.model.Event
import com.google.api.services.calendar.model.EventDateTime
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.parser.Parser
import org.commonmark.renderer.html.HtmlRenderer
import java.time.Instant
import java.time.temporal.ChronoUnit

private const val TZ_ID = "Europe/Amsterdam"
private val EXTENSIONS = listOf(TablesExtension.create(), StrikethroughExtension.create())
private val markdown: Parser = Parser.builder().extensions(EXTENSIONS).build()
private val html: HtmlRenderer = HtmlRenderer.builder().extensions(EXTENSIONS).build()

/** The Google Calendar event for one of ours, with its Markdown description rendered to HTML. */
internal fun toGoogleEvent(
    title: String,
    location: String?,
    description: String?,
    startTime: Instant,
    endTime: Instant,
): Event {
    val googleEvent = Event().setSummary(title).setLocation(location)
    // Google shows the description as one block, so the paragraph tags come out.
    description?.let { googleEvent.description = html.render(markdown.parse(it)).replace("<p>", "").replace("</p>", "") }
    googleEvent.start = EventDateTime().setDateTime(DateTime(startTime.wholeSeconds())).setTimeZone(TZ_ID)
    googleEvent.end = EventDateTime().setDateTime(DateTime(endTime.wholeSeconds())).setTimeZone(TZ_ID)
    return googleEvent
}

// Google is handed whole seconds, as it always was.
private fun Instant.wholeSeconds(): Long = truncatedTo(ChronoUnit.SECONDS).toEpochMilli()
