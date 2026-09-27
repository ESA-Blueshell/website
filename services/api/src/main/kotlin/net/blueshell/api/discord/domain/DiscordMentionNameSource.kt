package net.blueshell.api.discord.domain

import net.blueshell.api.shared.discord.DiscordMentionNames
import net.blueshell.api.shared.discord.MentionIds
import net.blueshell.api.shared.discord.MentionNames
import org.springframework.stereotype.Component

/** The server's names for what a description mentions, read from what the directories keep. */
@Component
class DiscordMentionNameSource(
    private val members: DiscordMemberDirectory,
    private val roles: DiscordRoleDirectory,
    private val channels: DiscordChannelDirectory,
) : DiscordMentionNames {
    override fun named(asked: MentionIds): MentionNames? {
        val people = if (asked.users.isEmpty()) emptyList() else members.named(asked.users) ?: return null
        val ranks = if (asked.roles.isEmpty()) emptyList() else roles.named(asked.roles) ?: return null
        val rooms = if (asked.channels.isEmpty()) emptyList() else channels.open() ?: return null
        return MentionNames(
            users = people.associate { it.id to it.name },
            roles = ranks.associate { it.id to it.name },
            channels = rooms.filter { it.id in asked.channels }.associate { it.id to it.name },
        )
    }
}
