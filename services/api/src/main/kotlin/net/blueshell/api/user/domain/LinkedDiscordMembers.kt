package net.blueshell.api.user.domain

import net.blueshell.api.shared.discord.ClaimedDiscordMembers
import net.blueshell.api.user.persistence.UserRepository
import org.springframework.stereotype.Component

/** The Discord members linked to an account, as the Discord person picker leaves them out. */
@Component
class LinkedDiscordMembers(
    private val users: UserRepository,
) : ClaimedDiscordMembers {
    override fun claimedIds(): Set<String> = users.findLinkedDiscordIds().toSet()
}
