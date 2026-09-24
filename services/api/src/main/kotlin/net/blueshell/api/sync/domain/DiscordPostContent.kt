package net.blueshell.api.sync.domain

import net.blueshell.api.event.api.EventPostData
import net.blueshell.api.sync.api.DiscordEmbed
import net.blueshell.api.sync.api.DiscordEventListing
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
    /* Under Discord's 4096 characters for an embed's description, which holds the links too. */
    private const val POST_DESCRIPTION = 3800
    /* Discord's limit on a Discord event's whole description, links included. */
    private const val LISTING_DESCRIPTION = 1000

    /* An event with no place is held in the server itself. */
    private const val IN_THE_SERVER = "Discord"
    /* Spelled out rather than left to the locale data, which abbreviates September as Sep or Sept by JDK. */
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
        val fields =
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
            }
        return DiscordPost(
            pingedRoleIds = event.pingedRoleIds,
            embed =
                DiscordEmbed(
                    title = event.title,
                    url = page,
                    description = "${cut(event.description.orEmpty(), POST_DESCRIPTION)}\n\n${linksOf(event, page)}",
                    fields = fields,
                ),
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

    private fun signedUpOf(event: EventPostData) =
        event.signUpLimit?.let { "${event.signUpCount}/$it" } ?: "${event.signUpCount}"

    /* The sign-up panel carries the `signup` anchor on the event's page. */
    private fun linksOf(
        event: EventPostData,
        page: String,
    ) = if (event.signUp) "[More on the site]($page) · [Sign up]($page#signup)" else "[More on the site]($page)"

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

    /* A Discord event's description takes no markdown links, so the addresses stand bare. */
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

    /* At a word, so a cut never splits one. */
    private fun cut(
        text: String,
        limit: Int,
    ): String {
        val said = text.trim()
        if (said.length <= limit) return said
        val atWord = said.take(limit).substringBeforeLast(' ').ifBlank { said.take(limit) }
        return "$atWord…"
    }
}
