package net.blueshell.api.sync.domain

import net.blueshell.api.event.api.EventPostData
import net.blueshell.api.sync.api.DiscordEmbed
import net.blueshell.api.sync.api.DiscordEventListing
import net.blueshell.api.sync.api.DiscordPost
import java.util.Locale

/**
 * What the bot says about an event. Times are Discord timestamps, which every reader's app shows
 * in their own clock, so nobody converts from Amsterdam by hand.
 */
object DiscordPostContent {
    /* Under Discord's 4096 characters for an embed's description, which holds the links too. */
    private const val POST_DESCRIPTION = 3800
    private const val LISTING_DESCRIPTION = 900

    fun postOf(
        event: EventPostData,
        site: String,
    ): DiscordPost {
        val page = pageOf(event, site)
        val fields =
            buildList {
                add("When" to whenOf(event))
                event.location?.takeIf { it.isNotBlank() }?.let { add("Where" to it.trim()) }
                add("Price" to priceOf(event))
                if (event.membersOnly) add("Members only" to "Yes")
                if (event.signUp) add("Signed up" to signedUpOf(event))
                event.signUpDeadline?.let { add("Sign up before" to "<t:${it.epochSecond}:F>") }
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
    ) = DiscordEventListing(
        name = event.title,
        description = "${cut(event.description.orEmpty(), LISTING_DESCRIPTION)}\n\nMore on the site: ${pageOf(event, site)}",
        location = event.location?.takeIf { it.isNotBlank() }?.trim() ?: "Online",
        start = event.startTime,
        end = event.endTime,
        cover = cover,
    )

    private fun signedUpOf(event: EventPostData) =
        event.signUpLimit?.takeIf { it > 0 }?.let { "${event.signUpCount}/$it" } ?: "${event.signUpCount}"

    /* The sign-up panel carries the `signup` anchor on the event's page. */
    private fun linksOf(
        event: EventPostData,
        page: String,
    ) = if (event.signUp) "[More on the site]($page) · [Sign up]($page#signup)" else "[More on the site]($page)"

    private fun pageOf(
        event: EventPostData,
        site: String,
    ) = "$site/events/${event.id}"

    private fun whenOf(event: EventPostData): String {
        val zone = DiscordPostSchedule.ZONE
        val sameDay = event.startTime.atZone(zone).toLocalDate() == event.endTime.atZone(zone).toLocalDate()
        return "<t:${event.startTime.epochSecond}:F> until <t:${event.endTime.epochSecond}:${if (sameDay) "t" else "F"}>"
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
