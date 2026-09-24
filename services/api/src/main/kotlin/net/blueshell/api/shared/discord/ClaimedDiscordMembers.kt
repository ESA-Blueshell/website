package net.blueshell.api.shared.discord

/**
 * The Discord members already linked to a website account, by user ID. The user module knows which
 * they are and the discord module asks, and neither depends on the other.
 */
fun interface ClaimedDiscordMembers {
    fun claimedIds(): Set<String>
}
