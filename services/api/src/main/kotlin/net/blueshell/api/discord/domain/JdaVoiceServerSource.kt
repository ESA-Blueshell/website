package net.blueshell.api.discord.domain

import net.blueshell.api.shared.credentials.Credentials
import net.blueshell.api.shared.credentials.RotatingSecret
import net.blueshell.api.shared.credentials.WhenCredentialsSet
import net.blueshell.clients.discord.api.DiscordApi
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.JDABuilder
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.events.RawGatewayEvent
import net.dv8tion.jda.api.events.guild.GuildReadyEvent
import net.dv8tion.jda.api.events.session.SessionRecreateEvent
import net.dv8tion.jda.api.events.session.ShutdownEvent
import net.dv8tion.jda.api.hooks.EventListener
import net.dv8tion.jda.api.requests.GatewayIntent
import net.dv8tion.jda.api.utils.MemberCachePolicy
import net.dv8tion.jda.api.utils.cache.CacheFlag
import net.dv8tion.jda.api.utils.data.DataObject
import net.dv8tion.jda.internal.entities.GuildImpl
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.cloud.context.environment.EnvironmentChangeEvent
import org.springframework.context.SmartLifecycle
import org.springframework.core.env.Environment
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * The live server, held by a gateway connection. Discord's REST API cannot list who is in voice,
 * so this listens instead: the full state arrives when the connection opens, and every change
 * after it, which [onChange] passes on.
 *
 * Presence and Server Members are privileged, and a gateway asking for one the application lacks
 * is refused outright, so the application's flags are read first and only granted intents are
 * asked for. Only members in voice are cached. Each api pod holds its own connection; the bot only
 * reads, so two during a rollout is harmless. JDA resumes a dropped session itself, but a start
 * that fails, or a session Discord ends for good, leaves [server] null and is tried again from
 * scratch, waiting longer each time.
 */
@Component
@WhenCredentialsSet(Credentials.DISCORD_BOT)
class JdaVoiceServerSource(
    environment: Environment,
    @Value($$"${discord.guildId:}") private val guildId: String,
    private val discordApi: DiscordApi,
    private val clock: Clock = Clock.systemUTC(),
) : VoiceServerSource,
    DoorSource,
    MemberEvents,
    RoomAccess,
    GatewayGuild,
    SmartLifecycle {
    @Volatile private var jda: JDA? = null

    private val token = RotatingSecret(environment, Credentials.DISCORD_BOT)

    @Volatile private var connectedWith = ""

    /** The token the gateway last connected with. */
    internal val tokenInUse: String get() = connectedWith

    @Volatile private var granted: Set<GatewayIntent> = emptySet()

    private val listeners = CopyOnWriteArrayList<() -> Unit>()
    private val named = CopyOnWriteArrayList<(String, String) -> Unit>()
    private val connected = CopyOnWriteArrayList<() -> Unit>()

    private val invites = ConcurrentHashMap<String, String>()
    private val access = ConcurrentHashMap<String, KeptRead<Set<String>>>()

    internal val relay =
        EventListener { event ->
            listeners.forEach { it() }
            when (event) {
                is RawGatewayEvent ->
                    if (event.type in MEMBER_CHANGES) {
                        namedIn(event.payload, guildId)?.let { (id, name) ->
                            // Their roles may have changed with it, so what they may join is read again.
                            access.remove(id)
                            named.forEach { it(id, name) }
                        }
                    }
                // A resumed session has Discord replay what it missed; a recreated one does not.
                is GuildReadyEvent, is SessionRecreateEvent -> connected.forEach { it() }
                is ShutdownEvent -> ended(event.jda)
            }
        }

    // Settable so a test can hand over a JDA rather than connect; nothing else changes it.
    internal var connect: (JDABuilder) -> JDA = JDABuilder::build

    @Volatile private var running = false

    @Volatile private var failures = 0

    private val retries =
        lazy {
            Executors.newSingleThreadScheduledExecutor { Thread(it, "discord-gateway").apply { isDaemon = true } }
        }

    // Settable so a test can run the next attempt itself; nothing else changes it.
    internal var later: (Duration, () -> Unit) -> Unit = { wait, attempt ->
        retries.value.schedule(attempt, wait.toMillis(), TimeUnit.MILLISECONDS)
    }

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

    override fun onMemberNamed(listener: (String, String) -> Unit) {
        named += listener
    }

    override fun onConnected(listener: () -> Unit) {
        connected += listener
    }

    /*
     * Discord's own answer from the member's roles and every override, rather than a guess from
     * role names. Read from Discord when the member is not cached, and kept for [ACCESS_KEPT_FOR].
     */
    override fun joinableBy(memberId: String): Set<String>? {
        val guild = jda?.getGuildById(guildId) ?: return null
        return access.computeIfAbsent(memberId) { KeptRead("Discord voice access", ACCESS_KEPT_FOR, clock) }.get {
            val member =
                guild.getMemberById(memberId)
                    ?: runCatching { guild.retrieveMemberById(memberId).complete() }.getOrNull()
            member
                ?.let { one ->
                    guild.voiceChannels
                        .filter { one.hasPermission(it, Permission.VIEW_CHANNEL, Permission.VOICE_CONNECT) }
                        .map { it.id }
                        .toSet()
                }.orEmpty()
        }
    }

    override fun guild(): Guild? = jda?.getGuildById(guildId)

    // JDA keeps announcement channels apart from text channels; both take posts and invites.
    override fun textRooms(): List<TextRoom> =
        jda
            ?.getGuildById(guildId)
            ?.let { it.textChannels + it.newsChannels }
            ?.map { TextRoom(it.id, guildId, it.name, it.parentCategory?.name) }
            .orEmpty()

    // Made once per channel and kept: unique=false has Discord hand back the same invite anyway.
    override fun invite(channelId: String): String? =
        invites[channelId] ?: runCatching {
            jda
                ?.let { it.getTextChannelById(channelId) ?: it.getNewsChannelById(channelId) }
                ?.createInvite()
                ?.setMaxAge(0)
                ?.setUnique(false)
                ?.complete()
                ?.url
        }.onFailure { log.warn("Discord would not make an invite into channel {}", channelId, it) }
            .getOrNull()
            ?.also { invites[channelId] = it }

    override fun start() {
        running = true
        failures = 0
        attempt()
    }

    @Synchronized
    private fun attempt() {
        // A retry queued before a reconnect finds the new session up and leaves it alone.
        if (!running || jda != null) return
        granted =
            runCatching { privilegedIntentsOf(discordApi.getMyOauth2Application().flags) }
                .onFailure { log.warn("Discord application flags could not be read; connecting without privileged intents", it) }
                .getOrDefault(emptySet())
        connectedWith = token.current()
        jda =
            runCatching { connect(gatewayOf(connectedWith, granted, relay)) }
                .onSuccess { failures = 0 }
                .onFailure { log.warn("Discord gateway did not start; the band falls back to the public widget until it does", it) }
                .getOrNull()
        if (jda == null) tryAgain()
    }

    // Discord closed the session with a code JDA will not resume from, such as intents it no longer grants.
    @Synchronized
    private fun ended(ended: JDA) {
        if (!running || ended !== jda) return
        log.warn("Discord ended the gateway session for good; connecting again from scratch")
        jda = null
        tryAgain()
    }

    private fun tryAgain() {
        val wait = FIRST_RETRY.multipliedBy(1L shl minOf(failures++, DOUBLINGS)).coerceAtMost(LAST_RETRY)
        log.info("Trying the Discord gateway again in {}", wait)
        later(wait, ::attempt)
    }

    /**
     * Reconnects the gateway under a token rotated in Vault while the api runs. Reads fall back
     * as when Discord is unreachable until the new session is up.
     */
    @org.springframework.context.event.EventListener(EnvironmentChangeEvent::class)
    @Synchronized
    fun onTokenRotated(event: EnvironmentChangeEvent) {
        if (Credentials.DISCORD_BOT !in event.keys || !running || token.current() == connectedWith) return
        log.info("Discord bot token rotated; connecting the gateway again")
        val ended = jda
        jda = null
        ended?.shutdown()
        failures = 0
        attempt()
    }

    override fun stop() {
        running = false
        jda?.shutdown()
        jda = null
        if (retries.isInitialized()) retries.value.shutdownNow()
    }

    override fun isRunning(): Boolean = running

    internal companion object {
        val ACCESS_KEPT_FOR: Duration = Duration.ofMinutes(1)
        private val FIRST_RETRY: Duration = Duration.ofSeconds(30)
        private val LAST_RETRY: Duration = Duration.ofMinutes(30)
        private const val DOUBLINGS = 6
        private val MEMBER_CHANGES = setOf("GUILD_MEMBER_UPDATE", "GUILD_MEMBER_ADD")
        private val log = LoggerFactory.getLogger(JdaVoiceServerSource::class.java)
    }
}

// The application flags that grant each privileged intent; the _LIMITED ones are how an unverified bot has it.
private const val GATEWAY_PRESENCE = 1 shl 12
private const val GATEWAY_PRESENCE_LIMITED = 1 shl 13
private const val GATEWAY_GUILD_MEMBERS = 1 shl 14
private const val GATEWAY_GUILD_MEMBERS_LIMITED = 1 shl 15

/** The connection as [JdaVoiceServerSource] wants it, unbuilt: [granted] privileged intents, voice members cached. */
internal fun gatewayOf(
    token: String,
    granted: Set<GatewayIntent>,
    listener: EventListener,
): JDABuilder =
    JDABuilder
        .createLight(token, granted + GatewayIntent.GUILD_VOICE_STATES)
        .setMemberCachePolicy(MemberCachePolicy.VOICE)
        .enableCache(listOfNotNull(CacheFlag.VOICE_STATE, CacheFlag.ONLINE_STATUS.takeIf { GatewayIntent.GUILD_PRESENCES in granted }))
        // Raw events too: a presence change of a member not in the cache fires nothing else.
        .setRawEventsEnabled(true)
        .addEventListeners(listener)

/**
 * The member a raw GUILD_MEMBER_UPDATE or GUILD_MEMBER_ADD is about, in [guildId], with the name
 * the server shows for them: nickname, else display name, else username.
 */
internal fun namedIn(
    payload: DataObject,
    guildId: String,
): Pair<String, String>? {
    if (payload.getString("guild_id", null) != guildId) return null
    val user = payload.optObject("user").orElse(null) ?: return null
    val id = user.getString("id", null) ?: return null
    val name =
        payload.getString("nick", null)
            ?: user.getString("global_name", null)
            ?: user.getString("username", null)
            ?: return null
    return id to name
}

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
