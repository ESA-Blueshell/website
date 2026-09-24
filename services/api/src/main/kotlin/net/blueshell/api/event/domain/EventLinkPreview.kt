package net.blueshell.api.event.domain

import net.blueshell.api.event.persistence.Event
import net.blueshell.api.file.api.Image
import net.blueshell.api.file.api.asImage
import org.commonmark.node.Block
import org.commonmark.node.Code
import org.commonmark.node.HardLineBreak
import org.commonmark.node.Node
import org.commonmark.node.SoftLineBreak
import org.commonmark.node.Text
import org.commonmark.parser.Parser
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

data class LinkPreviewImage(
    val url: String,
    val width: Int?,
    val height: Int?,
)

/** Composed here, not in the frontend: crawlers run no script. */
data class EventLinkPreview(
    val title: String,
    val text: String,
    val url: String,
    val image: LinkPreviewImage,
)

private const val PREVIEW_WIDTH = 1280

private const val SNIPPET_LENGTH = 160
private const val SITE_BANNER_WIDTH = 3840
private const val SITE_BANNER_HEIGHT = 2560

private val AMSTERDAM = ZoneId.of("Europe/Amsterdam")
private val DAY = DateTimeFormatter.ofPattern("EEE d MMMM yyyy", Locale.ENGLISH)
private val DAY_WITHOUT_YEAR = DateTimeFormatter.ofPattern("EEE d MMMM", Locale.ENGLISH)
private val HOURS = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH)

private val markdown = Parser.builder().build()

/** Without a banner, the site banner of the frontend's generic preview in `index.html`. */
fun Event.linkPreview(
    frontendUrl: String,
    apiUrl: String,
): EventLinkPreview =
    EventLinkPreview(
        title = title,
        text =
            listOfNotNull(
                whenOf(this),
                location?.takeIf { it.isNotBlank() },
                "Members only".takeIf { membersOnly },
                snippetOf(description),
            ).joinToString(" · "),
        url = "$frontendUrl/events/$id",
        image =
            banner?.file?.asImage()?.let { previewCopyOf(it, apiUrl) }
                ?: LinkPreviewImage("$frontendUrl/banner.webp", SITE_BANNER_WIDTH, SITE_BANNER_HEIGHT),
    )

/** Mirrors `whenOf` in the frontend's `eventFacts.ts`, plus the year. */
private fun whenOf(event: Event): String {
    val from = event.startTime.atZone(AMSTERDAM)
    val until = event.endTime.atZone(AMSTERDAM)
    val hours =
        if (from.toLocalDate() == until.toLocalDate()) {
            "${HOURS.format(from)}-${HOURS.format(until)}"
        } else {
            "${HOURS.format(from)} to ${DAY_WITHOUT_YEAR.format(until)}, ${HOURS.format(until)}"
        }
    return "${DAY.format(from)}, $hours"
}

private fun snippetOf(description: String?): String? {
    val text =
        description
            ?.let { StringBuilder().also { words -> wordsOf(markdown.parse(it), words) }.toString() }
            ?.replace(Regex("\\s+"), " ")
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: return null
    if (text.length <= SNIPPET_LENGTH) return text
    val cut = text.take(SNIPPET_LENGTH).substringBeforeLast(' ').trimEnd(',', '.', ';', ':')
    return "$cut…"
}

/** Visible words only: no link addresses, raw HTML or code blocks. */
private fun wordsOf(
    node: Node,
    into: StringBuilder,
) {
    when (node) {
        is Text -> into.append(node.literal)
        is Code -> into.append(node.literal)
        is SoftLineBreak, is HardLineBreak -> into.append(' ')
    }
    var child = node.firstChild
    while (child != null) {
        wordsOf(child, into)
        child = child.next
    }
    if (node is Block) into.append(' ')
}

internal fun previewCopyOf(
    image: Image,
    apiUrl: String,
): LinkPreviewImage {
    val masterWidth = image.width
    if (masterWidth != null && masterWidth <= PREVIEW_WIDTH) {
        return LinkPreviewImage("$apiUrl${image.url}", masterWidth, image.height)
    }
    val copy =
        image.renditions.filter { it.width <= PREVIEW_WIDTH }.maxByOrNull { it.width }
            ?: return LinkPreviewImage("$apiUrl${image.url}", masterWidth, image.height)
    val height = image.height?.let { height -> masterWidth?.let { height * copy.width / it } }
    return LinkPreviewImage("$apiUrl${copy.url}", copy.width, height)
}
