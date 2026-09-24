package net.blueshell.api.shared.discord

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

/** An event as the server lists it: external, at [location], between [start] and [end]. */
data class DiscordListing(
    val name: String,
    val description: String,
    val location: String,
    val start: Instant,
    val end: Instant,
    /** Data URI of the cover; null for none. */
    val cover: String?,
)

/**
 * What the bot can say in the association's Discord server. The discord module holds the bot; the
 * module that knows what to say calls this, and neither depends on the other. Every call throws
 * when Discord cannot be reached or refuses, so the caller's job can retry it.
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

    /** Lists an event in the server; answers the Discord event's ID. */
    fun list(listing: DiscordListing): String

    /** Brings a Discord event up to date; [listing]'s cover is left as it was where null. */
    fun relist(
        discordEventId: String,
        listing: DiscordListing,
    )

    /** Ends a Discord event whose event is over. */
    fun end(discordEventId: String)

    /** Removes a Discord event; one already gone is no failure. */
    fun unlist(discordEventId: String)
}
