package net.blueshell.api.sync.domain

import net.blueshell.api.event.api.EventPostData
import net.blueshell.api.sync.api.DISCORD_TEXT_MAX
import net.blueshell.api.sync.api.DiscordEventListing
import net.blueshell.api.sync.api.DiscordLink
import net.blueshell.api.sync.api.DiscordPost
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.temporal.ChronoField
import java.util.Locale

/**
 * What the bot says about an event. The posts give times in Amsterdam time as one piece of inline
 * code rather than as Discord timestamps: each timestamp is its own chip, and the dash between
 * two would stand out in another colour.
 */
object DiscordPostContent {
    // Discord's limit on a Discord event's whole description, links included.
    private const val LISTING_DESCRIPTION = 1000

    private const val PARAGRAPH = "\n\n"

    // About sixty mentions, so a full event leaves the description most of the post.
    private const val GOING_MAX = 1500

    // An event with no place is held in the server itself.
    private const val IN_THE_SERVER = "Discord"

    // Spelled out rather than left to the locale data, which abbreviates September as Sep or Sept by JDK.
    private val MONTHS =
        listOf("Jan", "Feb", "Mar", "Apr", "May", "June", "July", "Aug", "Sept", "Oct", "Nov", "Dec")
            .withIndex()
            .associate { (at, name) -> at + 1L to name }
    private val DAY_AND_TIME =
        DateTimeFormatterBuilder()
            .appendPattern("d ")
            .appendText(ChronoField.MONTH_OF_YEAR, MONTHS)
            .appendPattern(" yyyy - HH:mm")
            .toFormatter(Locale.ENGLISH)
    private val TIME = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH)

    fun postOf(
        event: EventPostData,
        site: String,
    ): DiscordPost {
        val page = pageOf(event.id, site)
        val details =
            buildList {
                add("When" to whenOf(event))
                event.location?.takeIf { it.isNotBlank() }?.let { add("Where" to it.trim()) }
                add("Price" to priceOf(event))
                if (event.membersOnly) add("Members only" to "Yes")
                if (event.signUp) add("Signed up" to signedUpOf(event))
                // A deadline at the start says nothing the When line does not.
                event.signUpDeadline?.takeIf { it != event.startTime }?.let {
                    add("Sign up before" to "`${DAY_AND_TIME.format(it.atZone(DiscordPostSchedule.ZONE))}`")
                }
            }.joinToString("\n") { (label, value) -> "**$label:** $value" }
        // Mentions last: Discord notifies the roles wherever the text names them.
        val mentions = event.pingedRoleIds.joinToString(" ") { "<@&$it>" }
        val heading = "## ${event.title}"
        val after = listOf(details, goingOf(event), mentions).filter { it.isNotEmpty() }
        // The description takes what the rest leaves: whole but for one near the cap.
        val room = DISCORD_TEXT_MAX - heading.length - after.sumOf { it.length } - PARAGRAPH.length * (after.size + 1)
        val description = cut(event.description.orEmpty(), room)
        return DiscordPost(
            pingedRoleIds = event.pingedRoleIds,
            text = (listOf(heading, description) + after).filter { it.isNotEmpty() }.joinToString(PARAGRAPH),
            links = linksOf(event, page),
        )
    }

    fun listingOf(
        event: EventPostData,
        site: String,
        cover: String?,
    ): DiscordEventListing {
        val links = listingLinksOf(event, site)
        return DiscordEventListing(
            name = event.title,
            description = "${cut(event.description.orEmpty(), LISTING_DESCRIPTION - links.length - "\n\n…".length)}\n\n$links",
            location = event.location?.takeIf { it.isNotBlank() }?.trim() ?: IN_THE_SERVER,
            start = event.startTime,
            end = event.endTime,
            cover = cover,
        )
    }

    /*
     * Who is going, by mention, first sign-up first; guests, accounts without Discord and whoever
     * does not fit in GOING_MAX are counted instead. Nobody named here is notified: the bot allows
     * only the pinged roles to be.
     */
    private fun goingOf(event: EventPostData): String {
        if (!event.signUp || event.goingDiscordIds.isEmpty()) return ""
        val named = mutableListOf<String>()
        var length = 0
        for (id in event.goingDiscordIds) {
            val mention = "<@$id>"
            if (length + mention.length + ", ".length > GOING_MAX) break
            named += mention
            length += mention.length + ", ".length
        }
        val others = event.signUpCount - named.size
        val rest =
            when {
                others <= 0 -> ""
                others == 1L -> " and 1 other"
                else -> " and $others others"
            }
        return "**Going:** ${named.joinToString(", ")}$rest"
    }

    private fun signedUpOf(event: EventPostData) = event.signUpLimit?.let { "${event.signUpCount}/$it" } ?: "${event.signUpCount}"

    // The sign-up panel carries the `signup` anchor on the event's page.
    private fun linksOf(
        event: EventPostData,
        page: String,
    ) = listOfNotNull(DiscordLink("More on the site", page), DiscordLink("Sign up", "$page#signup").takeIf { event.signUp })

    /** The event's page on the site, which every post links and so finds it by. */
    fun pageOf(
        eventId: Long,
        site: String,
    ) = "$site/events/$eventId"

    /** The line of a Discord event's description that names its event, and so finds it. */
    fun listingLineOf(
        eventId: Long,
        site: String,
    ) = "More on the site: ${pageOf(eventId, site)}"

    // A Discord event's description takes no markdown links, so the addresses stand bare.
    private fun listingLinksOf(
        event: EventPostData,
        site: String,
    ) = listingLineOf(event.id, site) + if (event.signUp) "\nSign up: ${pageOf(event.id, site)}#signup" else ""

    private fun whenOf(event: EventPostData): String {
        val start = event.startTime.atZone(DiscordPostSchedule.ZONE)
        val end = event.endTime.atZone(DiscordPostSchedule.ZONE)
        val span =
            if (start.toLocalDate() == end.toLocalDate()) {
                "${DAY_AND_TIME.format(start)}-${TIME.format(end)}"
            } else {
                "${DAY_AND_TIME.format(start)} to ${DAY_AND_TIME.format(end)}"
            }
        return "`$span`"
    }

    private fun priceOf(event: EventPostData): String {
        val members = event.memberPrice?.takeIf { it > 0 }
        val others = event.publicPrice?.takeIf { it > 0 && !event.membersOnly }
        return when {
            members == null && others == null -> "Free"
            others == null -> "${euro(members!!)} for members"
            members == null -> "Free for members, ${euro(others)} for others"
            else -> "${euro(members)} for members, ${euro(others)} for others"
        }
    }

    private fun euro(amount: Double) = "€" + String.format(Locale.ROOT, "%.2f", amount)

    /*
     * At a space or a line break, so a cut never splits a word, an emoji or a mention, none of
     * which holds either. A text with neither is cut before an emoji or mention it would split.
     */
    internal fun cut(
        text: String,
        limit: Int,
    ): String {
        val said = text.trim()
        if (said.length <= limit) return said
        // One short of the limit, which the ellipsis takes.
        val kept = said.take(limit - 1)
        val atWord = kept.substring(0, kept.indexOfLast { it.isWhitespace() }.coerceAtLeast(0)).trimEnd()
        if (atWord.isNotEmpty()) return "$atWord…"
        val open = kept.lastIndexOf('<')
        return "${if (open > 0 && kept.indexOf('>', open) == -1) kept.take(open) else kept}…"
    }
}
