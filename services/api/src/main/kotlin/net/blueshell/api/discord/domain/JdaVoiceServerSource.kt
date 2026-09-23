package net.blueshell.api.discord.domain

import net.blueshell.clients.discord.api.DiscordApi
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.JDABuilder
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.hooks.EventListener
import net.dv8tion.jda.api.requests.GatewayIntent
import net.dv8tion.jda.api.utils.MemberCachePolicy
import net.dv8tion.jda.api.utils.cache.CacheFlag
import net.dv8tion.jda.internal.entities.GuildImpl
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.context.SmartLifecycle
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import java.util.concurrent.CopyOnWriteArrayList

/**
 * The live server, held by a gateway connection. Discord's REST API cannot list who is in voice,
 * so this listens instead: the full state arrives when the connection opens, and every change
 * after it, which [onChange] passes on.
 *
 * Presence and Server Members are privileged, and a gateway asking for one the application lacks
 * is refused outright, so the application's flags are read first and only granted intents are
 * asked for. Only members in voice are cached. Each api pod holds its own connection; the bot only
 * reads, so two during a rollout is harmless. A refused token or an unreachable Discord leaves
 * [server] null rather than failing the api.
 */
@Component
@Profile("!test")
@ConditionalOnExpression(DISCORD_TOKEN_SET)
class JdaVoiceServerSource(
    @Value($$"${discord.botToken:}") private val botToken: String,
    @Value($$"${discord.guildId:}") private val guildId: String,
    private val discordApi: DiscordApi,
) : VoiceServerSource,
    SmartLifecycle {
    @Volatile private var jda: JDA? = null

    @Volatile private var granted: Set<GatewayIntent> = emptySet()

    private val listeners = CopyOnWriteArrayList<() -> Unit>()

    override fun server(): VoiceServer? =
        jda?.getGuildById(guildId)?.let {
            voiceServerOf(
                it,
                countsOnline = GatewayIntent.GUILD_PRESENCES in granted,
                countsMembers = GatewayIntent.GUILD_MEMBERS in granted,
            )
        }

    override fun onChange(listener: () -> Unit) {
        listeners += listener
    }

    override fun start() {
        granted =
            runCatching { privilegedIntentsOf(discordApi.getMyOauth2Application().flags) }
                .onFailure { log.warn("Discord application flags could not be read; connecting without privileged intents", it) }
                .getOrDefault(emptySet())
        val presences = GatewayIntent.GUILD_PRESENCES in granted
        jda =
            runCatching {
                JDABuilder
                    .createLight(botToken, granted + GatewayIntent.GUILD_VOICE_STATES)
                    .setMemberCachePolicy(MemberCachePolicy.VOICE)
                    .enableCache(listOfNotNull(CacheFlag.VOICE_STATE, CacheFlag.ONLINE_STATUS.takeIf { presences }))
                    // Raw events too: a presence change of a member not in the cache fires nothing else.
                    .setRawEventsEnabled(true)
                    .addEventListeners(EventListener { listeners.forEach { it() } })
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

/* The application flags that grant each privileged intent; the _LIMITED ones are how an unverified bot has it. */
private const val GATEWAY_PRESENCE = 1 shl 12
private const val GATEWAY_PRESENCE_LIMITED = 1 shl 13
private const val GATEWAY_GUILD_MEMBERS = 1 shl 14
private const val GATEWAY_GUILD_MEMBERS_LIMITED = 1 shl 15

/** The privileged intents an application with [flags] may ask for. */
internal fun privilegedIntentsOf(flags: Int): Set<GatewayIntent> =
    buildSet {
        if (flags and (GATEWAY_PRESENCE or GATEWAY_PRESENCE_LIMITED) != 0) add(GatewayIntent.GUILD_PRESENCES)
        if (flags and (GATEWAY_GUILD_MEMBERS or GATEWAY_GUILD_MEMBERS_LIMITED) != 0) add(GatewayIntent.GUILD_MEMBERS)
    }

/**
 * The voice rooms of [guild] everybody can see, with who is in each. The AFK room is left out:
 * nobody joins it to talk. Whether everybody may see or join a room is read off the @everyone
 * role, the permissions a visitor without any role has.
 *
 * The counts are only as current as the gateway keeps them: the member count follows joins and
 * leaves under Server Members, and presences are only sent under Presence.
 */
internal fun voiceServerOf(
    guild: Guild,
    countsOnline: Boolean = false,
    countsMembers: Boolean = false,
): VoiceServer {
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
    return VoiceServer(
        id = guild.id,
        name = guild.name,
        rooms = rooms,
        online = if (countsOnline) onlineIn(guild) else null,
        members = if (countsMembers) guild.memberCount else null,
    )
}

/*
 * JDA keeps every presence that is not offline, members it does not cache included, but only on
 * its internal GuildImpl: there is no public accessor. An upgrade that moves it breaks the build.
 */
private fun onlineIn(guild: Guild): Int? = (guild as? GuildImpl)?.presenceView?.size()?.toInt()
