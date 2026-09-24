package net.blueshell.api.sync.api

import java.time.Instant

/** A message the bot writes: the roles it notifies, named above an embed. */
data class DiscordPost(
    val pingedRoleIds: List<String>,
    val embed: DiscordEmbed,
)

/** A Discord embed: a titled card linking [url], with [fields] as label and value, and [imageUrl] under them. */
data class DiscordEmbed(
    val title: String,
    val url: String,
    val description: String,
    val fields: List<Pair<String, String>>,
    val imageUrl: String?,
)

/** A Discord event as the bot lists it: external, at [location], between [start] and [end]. */
data class DiscordEventListing(
    val name: String,
    val description: String,
    val location: String,
    val start: Instant,
    val end: Instant,
    /** Data URI of the cover; null for none. */
    val cover: String?,
)

/**
 * What the bot can put in the association's Discord server, in this module's terms (ADR-019): the
 * discord module, which holds the bot, implements it. Every call throws when Discord cannot be
 * reached or refuses, so the caller's job can retry it.
 */
interface DiscordPublisher {
    /** Posts [post] in the channel called [channel], notifying its roles; answers the message's ID. */
    fun post(
        channel: String,
        post: DiscordPost,
    ): String

    /** Rewrites a message the bot posted. Nobody is notified, whatever roles it now names. */
    fun edit(
        channel: String,
        messageId: String,
        post: DiscordPost,
    )

    /** Removes a message the bot posted; one already gone is no failure. */
    fun delete(
        channel: String,
        messageId: String,
    )

    /** Answers the Discord event's ID. */
    fun createDiscordEvent(listing: DiscordEventListing): String

    fun updateDiscordEvent(
        discordEventId: String,
        listing: DiscordEventListing,
    )

    /** One already gone is no failure. */
    fun deleteDiscordEvent(discordEventId: String)
}
