package net.blueshell.api.discord.domain

import net.blueshell.clients.discord.api.DiscordApi
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Duration
import java.time.Instant

/**
 * The server's member and online counts, from `GET /guilds/{id}?with_counts=true`. The counts move
 * slowly and a page view must never become a Discord call, so an answer is kept for [TTL]. A
 * refused or failed read keeps the last answer, or null where there was none.
 */
@Component
@Profile("!test")
@ConditionalOnExpression(DISCORD_TOKEN_SET)
class RestGuildCountsSource(
    private val discordApi: DiscordApi,
    @Value($$"${discord.guildId:}") private val guildId: String,
    internal var clock: Clock = Clock.systemUTC(),
) : GuildCountsSource {
    @Volatile private var kept: Pair<Instant, GuildCounts>? = null

    override fun counts(): GuildCounts? {
        val now = clock.instant()
        kept?.let { (at, counts) -> if (Duration.between(at, now) < TTL) return counts }
        val read =
            runCatching { discordApi.getGuild(guildId, true) }
                .onFailure { log.warn("Discord guild counts could not be read", it) }
                .getOrNull()
        val members = read?.approximateMemberCount
        val online = read?.approximatePresenceCount
        if (members == null || online == null) return kept?.second
        return GuildCounts(members = members, online = online).also { kept = now to it }
    }

    internal companion object {
        val TTL: Duration = Duration.ofMinutes(1)
        private val log = LoggerFactory.getLogger(RestGuildCountsSource::class.java)
    }
}
