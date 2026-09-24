package net.blueshell.api.discord.domain

import net.blueshell.clients.discord.api.DiscordApi
import net.blueshell.clients.discord.model.GuildMemberResponse
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

/** A member of the server as a picker shows them: the name the server shows, their username and avatar. */
data class DiscordMember(
    val id: String,
    val name: String,
    val username: String,
    val avatar: String,
)

/**
 * The server's members, searched by the start of their username or server name through Discord's
 * member search. Null means there is no bot to ask, or Discord did not answer; the picker then
 * falls back to typing a name.
 */
@Service
class DiscordMemberDirectory(
    private val api: ObjectProvider<DiscordApi>,
    @Value($$"${discord.guildId:}") private val guildId: String,
) {
    fun search(query: String): List<DiscordMember>? {
        val client = api.ifAvailable ?: return null
        val wanted = query.trim()
        if (wanted.length < MIN_QUERY) return emptyList()
        return runCatching { client.searchGuildMembers(guildId, wanted, MAX_MEMBERS) }
            .onFailure { log.warn("Discord member search failed", it) }
            .getOrNull()
            ?.map { memberOf(it, guildId) }
    }

    internal companion object {
        const val MIN_QUERY = 2
        const val MAX_MEMBERS = 10
        private val log = LoggerFactory.getLogger(DiscordMemberDirectory::class.java)
    }
}

private const val CDN = "https://cdn.discordapp.com"

/* Discord's six default avatars, one per account by its ID, for somebody who never set one. */
private const val DEFAULT_AVATARS = 6
private const val TIMESTAMP_SHIFT = 22

/**
 * [member] as the server shows them: their server nickname, else their display name, else their
 * username; and their server avatar, else their own, else Discord's default for their account.
 */
internal fun memberOf(
    member: GuildMemberResponse,
    guildId: String,
): DiscordMember {
    val user = member.user
    val avatar =
        when {
            member.avatar != null -> "$CDN/guilds/$guildId/users/${user.id}/avatars/${member.avatar}.png?size=64"
            user.avatar != null -> "$CDN/avatars/${user.id}/${user.avatar}.png?size=64"
            else -> "$CDN/embed/avatars/${(user.id.toLong() shr TIMESTAMP_SHIFT) % DEFAULT_AVATARS}.png"
        }
    return DiscordMember(
        id = user.id,
        name = member.nick ?: user.globalName ?: user.username,
        username = user.username,
        avatar = avatar,
    )
}
