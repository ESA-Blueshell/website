package net.blueshell.api.shared.discord

/** Which Discord member an account is linked to. The user module knows; the discord module asks. */
fun interface DiscordLinks {
    fun discordIdOf(userId: Long): String?
}
