package net.blueshell.api.shared.discord

/** A member of the server as others see them there: their username and their avatar. */
data class DiscordFace(
    val tag: String,
    val avatar: String,
)

/**
 * How the server shows its members, by Discord user ID. The discord module knows and a page that
 * names people by Discord asks, and neither depends on the other. A member the bot cannot see, or
 * any member while there is no bot, is simply absent.
 */
fun interface DiscordFaces {
    fun of(ids: Collection<String>): Map<String, DiscordFace>
}

private const val DEFAULT_AVATARS = 6
private const val TIMESTAMP_SHIFT = 22

/** Discord's own avatar for an account that never set one, which it picks by the account's ID. */
fun defaultAvatarOf(userId: String): String =
    "https://cdn.discordapp.com/embed/avatars/${(userId.toLong() shr TIMESTAMP_SHIFT) % DEFAULT_AVATARS}.png"
