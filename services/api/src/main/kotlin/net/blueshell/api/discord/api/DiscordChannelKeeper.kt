package net.blueshell.api.discord.api

/** What a channel or category in the server is. */
enum class KeptChannelKind {
    TEXT,
    VOICE,
    CATEGORY,
}

/** A channel or category in the server as another module keeps it, with the category it is filed under. */
data class KeptChannel(
    val id: String,
    val name: String,
    val kind: KeptChannelKind,
    val category: String?,
)

/**
 * The server's channels as the site opens them to roles: a role is given a channel by a permission
 * overwrite on it, and a private channel is one @everyone cannot view. Every call needs the bot;
 * [available] says whether there is one in the server now, and the rest refuse with [DiscordUnavailable].
 */
interface DiscordChannelKeeper {
    fun available(): Boolean

    /** Every channel and category, in the server's order. */
    fun channels(): List<KeptChannel>

    /** The channels and categories [roleId] is let into by an overwrite of its own. */
    fun openedTo(roleId: String): List<KeptChannel>

    /** Makes a text channel under [category], made where it is missing, that only [roleId] can see. */
    fun createPrivate(
        name: String,
        category: String,
        roleId: String,
    ): KeptChannel

    /** Lets [roleId] into [channelId]; where [private], @everyone is kept out of it. */
    fun open(
        channelId: String,
        roleId: String,
        private: Boolean,
    )

    /** Takes [roleId]'s own overwrite off [channelId]; nothing else on the channel changes. */
    fun close(
        channelId: String,
        roleId: String,
    )

    /** Deletes [channelId] from the server, for a removal the board asked for. */
    fun delete(channelId: String)

    /** Moves each of [channelIds] into the archive category, remembering where it was; nothing is deleted. */
    fun archive(channelIds: Collection<String>)

    /** Moves each of [channelIds] back to the category it was archived from. */
    fun restore(channelIds: Collection<String>)
}
