package net.blueshell.api.discord.domain

import net.blueshell.api.shared.discord.ClaimedDiscordMembers
import net.blueshell.clients.discord.api.DiscordApi
import net.blueshell.clients.discord.model.GuildMemberResponse
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.Duration
import java.time.Instant

/** A member of the server as a picker shows them: the name the server shows, their username and avatar. */
data class DiscordMember(
    val id: String,
    val name: String,
    val username: String,
    val avatar: String,
)

/**
 * The server's people, bots left out. Null means there is no bot to ask, or Discord did not answer;
 * the picker then falls back to typing a name.
 *
 * [search] asks Discord each time, so somebody who has just joined is found. [unclaimed] reads the
 * whole member list, which Discord pages at a thousand, so it is kept for [KEPT_FOR].
 */
@Service
class DiscordMemberDirectory(
    private val api: ObjectProvider<DiscordApi>,
    private val claimed: ObjectProvider<ClaimedDiscordMembers>,
    @Value($$"${discord.guildId:}") private val guildId: String,
) {
    @Volatile private var kept: Pair<Instant, List<DiscordMember>>? = null

    /* Settable so a test can move time rather than wait for it; nothing else changes it. */
    internal var clock: Clock = Clock.systemUTC()

    /** Members whose username or server name starts with [query], ten at most. */
    fun search(query: String): List<DiscordMember>? {
        val client = api.ifAvailable ?: return null
        val wanted = query.trim()
        if (wanted.length < MIN_QUERY) return emptyList()
        return runCatching { client.searchGuildMembers(guildId, wanted, MAX_MEMBERS) }
            .onFailure { log.warn("Discord member search failed", it) }
            .getOrNull()
            ?.filterNot { it.user.bot == true }
            ?.map { memberOf(it, guildId) }
    }

    /** Everybody no website account has linked yet, by name. */
    fun unclaimed(): List<DiscordMember>? {
        val taken = claimed.ifAvailable?.claimedIds().orEmpty()
        return everyone()?.filterNot { it.id in taken }?.sortedBy { it.name.lowercase() }
    }

    private fun everyone(): List<DiscordMember>? {
        val client = api.ifAvailable ?: return null
        val now = clock.instant()
        kept?.let { (at, members) -> if (Duration.between(at, now) < KEPT_FOR) return members }
        return runCatching { readAll(client) }
            .onFailure { log.warn("Discord member list could not be read", it) }
            .getOrNull()
            ?.also { kept = now to it }
            ?: kept?.second
    }

    private fun readAll(client: DiscordApi): List<DiscordMember> {
        val members = mutableListOf<DiscordMember>()
        var after: String? = null
        repeat(MAX_PAGES) {
            val page = client.listGuildMembers(guildId, PAGE, after)
            page.filterNot { it.user.bot == true }.mapTo(members) { memberOf(it, guildId) }
            if (page.size < PAGE) return members
            after = page.last().user.id
        }
        return members
    }

    internal companion object {
        const val MIN_QUERY = 2
        const val MAX_MEMBERS = 10
        const val PAGE = 1000

        /* Ten thousand members, which this server is far from; past it the list is cut, not endless. */
        const val MAX_PAGES = 10
        val KEPT_FOR: Duration = Duration.ofMinutes(5)
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
