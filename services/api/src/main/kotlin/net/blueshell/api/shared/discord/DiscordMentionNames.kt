package net.blueshell.api.shared.discord

/** The IDs a description mentions, by kind. */
data class MentionIds(
    val users: Set<String>,
    val roles: Set<String>,
    val channels: Set<String>,
)

/** What the server calls what a description mentions; an ID it lacks is left out. */
data class MentionNames(
    val users: Map<String, String> = emptyMap(),
    val roles: Map<String, String> = emptyMap(),
    val channels: Map<String, String> = emptyMap(),
)

/**
 * Names a description's mentions for a place outside the site and Discord. The discord module
 * answers, and a module writing a description elsewhere asks, without depending on it. Null where
 * Discord cannot be asked.
 */
fun interface DiscordMentionNames {
    fun named(asked: MentionIds): MentionNames?
}
