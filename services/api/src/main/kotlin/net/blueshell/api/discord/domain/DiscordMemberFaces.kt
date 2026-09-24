package net.blueshell.api.discord.domain

import net.blueshell.api.shared.discord.DiscordFace
import net.blueshell.api.shared.discord.DiscordFaces
import org.springframework.stereotype.Component

/** The server's members by username and avatar, from the member list the directory keeps. */
@Component
class DiscordMemberFaces(
    private val directory: DiscordMemberDirectory,
) : DiscordFaces {
    override fun of(ids: Collection<String>): Map<String, DiscordFace> {
        if (ids.isEmpty()) return emptyMap()
        val wanted = ids.toSet()
        return directory
            .everyoneKept()
            .orEmpty()
            .filter { it.id in wanted }
            .associate { it.id to DiscordFace(tag = it.username, avatar = it.avatar) }
    }
}
