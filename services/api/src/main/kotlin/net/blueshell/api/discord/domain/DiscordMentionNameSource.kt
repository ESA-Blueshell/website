package net.blueshell.api.discord.domain

import net.blueshell.api.shared.discord.DiscordMentionNames
import net.blueshell.api.shared.discord.MentionIds
import net.blueshell.api.shared.discord.MentionNames
import org.springframework.stereotype.Component

/** What a description's mentions name on the server; an ID the server lacks is left out. */
data class Mentioned(
    val users: List<DiscordMember>,
    val roles: List<DiscordRoleName>,
    val channels: List<DiscordChannel>,
)

/** The server's names for what a description mentions, read from what the directories keep. */
@Component
class DiscordMentionNameSource(
    private val members: DiscordMemberDirectory,
    private val roles: DiscordRoleDirectory,
    private val channels: DiscordChannelDirectory,
) : DiscordMentionNames {
    /** Null without a bot, or where Discord did not answer for a kind asked about and nothing was kept. */
    fun mentioned(asked: MentionIds): Mentioned? {
        val people = if (asked.users.isEmpty()) emptyList() else members.named(asked.users) ?: return null
        val ranks = if (asked.roles.isEmpty()) emptyList() else roles.named(asked.roles) ?: return null
        val rooms = if (asked.channels.isEmpty()) emptyList() else channels.open() ?: return null
        return Mentioned(people, ranks, rooms.filter { it.id in asked.channels })
    }

    override fun named(asked: MentionIds): MentionNames? =
        mentioned(asked)?.let { found ->
            MentionNames(
                users = found.users.associate { it.id to it.name },
                roles = found.roles.associate { it.id to it.name },
                channels = found.channels.associate { it.id to it.name },
            )
        }
}
