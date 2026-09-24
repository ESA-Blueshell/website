package net.blueshell.api.sync.api

import java.time.Instant

/** A message the bot writes: the roles it notifies, then [banner], then an embed. */
data class DiscordPost(
    val pingedRoleIds: List<String>,
    val embed: DiscordEmbed,
    val banner: DiscordImage? = null,
)

/** A Discord embed: a titled card linking [url], with [fields] as label and value. */
data class DiscordEmbed(
    val title: String,
    val url: String,
    val description: String,
    val fields: List<Pair<String, String>>,
)

/**
 * A picture sent with a message rather than linked, so Discord shows it whether or not it can
 * reach the site. Not a data class: its bytes would make every copy unequal.
 */
class DiscordImage(
    val fileName: String,
    val mediaType: String,
    val bytes: ByteArray,
)

/** A Discord event as the bot lists it: external, at [location], between [start] and [end]. */
data class DiscordEventListing(
    val name: String,
    val description: String,
    val location: String,
    /** Null on an update leaves the start as Discord has it: Discord refuses one in the past. */
    val start: Instant?,
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

    /**
     * Rewrites a message the bot posted. Nobody is notified, whatever roles it now names. Answers
     * false where the message is gone, removed by hand.
     */
    fun edit(
        channel: String,
        messageId: String,
        post: DiscordPost,
    ): Boolean

    /**
     * The bot's messages among the latest hundred in the channel called [channel] whose embed
     * links [url], newest first: what is already out, whether or not it was recorded.
     */
    fun findPosts(
        channel: String,
        url: String,
    ): List<String>

    /** The Discord events in the server with [line] as a line of their description. */
    fun findDiscordEvents(line: String): List<String>

    /** Removes a message the bot posted; one already gone is no failure. */
    fun delete(
        channel: String,
        messageId: String,
    )

    /** Answers the Discord event's ID. */
    fun createDiscordEvent(listing: DiscordEventListing): String

    /** Answers false where the Discord event is gone, removed by hand. */
    fun updateDiscordEvent(
        discordEventId: String,
        listing: DiscordEventListing,
    ): Boolean

    /** One already gone is no failure. */
    fun deleteDiscordEvent(discordEventId: String)
}
