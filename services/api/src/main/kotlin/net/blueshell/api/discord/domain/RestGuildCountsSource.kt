package net.blueshell.api.discord.domain

import net.blueshell.clients.discord.api.DiscordApi
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Duration

/**
 * The server's member and online counts, from `GET /guilds/{id}?with_counts=true`. The counts move
 * slowly and a page view must never become a Discord call, so an answer is kept for [KEPT_FOR]. A
 * refused or failed read keeps the last answer, or null where there was none.
 */
@Component
@Profile("!test")
@ConditionalOnExpression(DISCORD_TOKEN_SET)
class RestGuildCountsSource(
    private val discordApi: DiscordApi,
    @Value($$"${discord.guildId:}") private val guildId: String,
    clock: Clock = Clock.systemUTC(),
) : GuildCountsSource {
    private val kept = KeptRead<GuildCounts>("Discord guild counts", KEPT_FOR, clock)

    override fun counts(): GuildCounts? =
        kept.get {
            val read = discordApi.getGuild(guildId, true)
            val members = read.approximateMemberCount
            val online = read.approximatePresenceCount
            if (members == null || online == null) null else GuildCounts(members = members, online = online)
        }

    internal companion object {
        val KEPT_FOR: Duration = Duration.ofMinutes(1)
    }
}
