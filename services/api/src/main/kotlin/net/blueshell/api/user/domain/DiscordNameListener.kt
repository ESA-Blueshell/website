package net.blueshell.api.user.domain

import net.blueshell.api.shared.discord.DiscordMemberNamed
import net.blueshell.api.user.persistence.UserRepository
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * Renames the account linked to a Discord member when the server calls them something new. Off
 * the publisher's thread, which is the gateway's, and outside any transaction of its: the event
 * comes from Discord, not from a write here.
 */
@Component
class DiscordNameListener(
    private val users: UserRepository,
) {
    @Async
    @EventListener
    @Transactional
    fun on(event: DiscordMemberNamed) {
        users.renameDiscordMember(event.discordId, event.name)
    }
}
