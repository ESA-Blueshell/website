package net.blueshell.api.discord.domain

import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.JDABuilder
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.requests.GatewayIntent
import net.dv8tion.jda.api.utils.MemberCachePolicy
import net.dv8tion.jda.api.utils.cache.CacheFlag
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.context.SmartLifecycle
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

/**
 * The live voice state, held by a gateway connection. Discord's REST API cannot list who is in
 * voice, so this listens instead: the full state arrives when the connection opens, and every
 * join, leave and move after it.
 *
 * Only what the band draws is cached: channels (JDA always keeps those, with their permissions)
 * and the members in voice. Each api pod holds its own connection; the bot only reads, so two
 * during a rollout is harmless. Connecting happens off the startup thread, and a refused token or
 * an unreachable Discord leaves [server] null rather than failing the api.
 */
@Component
@Profile("!test")
@ConditionalOnExpression(DISCORD_TOKEN_SET)
class JdaVoiceServerSource(
    @Value($$"${discord.botToken:}") private val botToken: String,
    @Value($$"${discord.guildId:}") private val guildId: String,
) : VoiceServerSource,
    SmartLifecycle {
    @Volatile private var jda: JDA? = null

    override fun server(): VoiceServer? = jda?.getGuildById(guildId)?.let(::voiceServerOf)

    override fun start() {
        jda =
            runCatching {
                JDABuilder
                    .createLight(botToken, GatewayIntent.GUILD_VOICE_STATES)
                    .setMemberCachePolicy(MemberCachePolicy.VOICE)
                    .enableCache(CacheFlag.VOICE_STATE)
                    .build()
            }.onFailure { log.warn("Discord gateway did not start; the band falls back to the public widget", it) }
                .getOrNull()
    }

    override fun stop() {
        jda?.shutdown()
        jda = null
    }

    override fun isRunning(): Boolean = jda != null

    private companion object {
        val log = LoggerFactory.getLogger(JdaVoiceServerSource::class.java)
    }
}

/**
 * The voice rooms of [guild] everybody can see, with who is in each. The AFK room is left out:
 * nobody joins it to talk. Whether everybody may see or join a room is read off the @everyone
 * role, the permissions a visitor without any role has.
 */
internal fun voiceServerOf(guild: Guild): VoiceServer {
    val everyone = guild.publicRole
    val afk = guild.afkChannel?.id
    val rooms =
        guild.voiceChannels
            .filter { it.id != afk && everyone.hasPermission(it, Permission.VIEW_CHANNEL) }
            .map { channel ->
                VoiceRoom(
                    id = channel.id,
                    name = channel.name,
                    position = channel.positionRaw,
                    locked = !everyone.hasPermission(channel, Permission.VOICE_CONNECT),
                    people = channel.members.map { VoicePerson(it.effectiveName, it.effectiveAvatarUrl) },
                )
            }
    return VoiceServer(id = guild.id, name = guild.name, rooms = rooms)
}
