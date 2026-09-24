package net.blueshell.api.user.domain

import net.blueshell.api.shared.discord.ClaimedDiscordMembers
import net.blueshell.api.shared.discord.DiscordLinks
import net.blueshell.api.user.persistence.UserRepository
import org.springframework.stereotype.Component

/** The Discord members linked to accounts: all of them, for the picker to leave out, and one account's. */
@Component
class LinkedDiscordMembers(
    private val users: UserRepository,
) : ClaimedDiscordMembers,
    DiscordLinks {
    override fun claimedIds(): Set<String> = users.findLinkedDiscordIds().toSet()

    override fun discordIdOf(userId: Long): String? = users.findDiscordIdById(userId)
}
