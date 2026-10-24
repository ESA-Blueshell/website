package net.blueshell.api.discord.domain

import io.swagger.v3.oas.annotations.media.Schema
import net.blueshell.api.shared.credentials.Credentials
import net.blueshell.api.shared.credentials.WhenCredentialsSet
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.ParameterizedTypeReference
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient
import java.time.Clock
import java.time.Duration

/** Where an emoji comes from: the association's server, the bot's Discord application, or seen used in the server. */
@Schema(enumAsRef = true)
enum class DiscordEmojiKind { SERVER, APPLICATION, SEEN }

/**
 * A picture uploaded to Discord, which a description writes as `<:name:id>`. It is not
 * [available] while over the server's boost limit, and [roleIds] lists the roles it is locked to.
 * [uses] counts the messages a seen emoji was used in.
 */
data class DiscordEmoji(
    val id: String,
    val name: String,
    val animated: Boolean,
    val available: Boolean,
    val roleIds: List<String>,
    val kind: DiscordEmojiKind,
    val uses: Long? = null,
)

/** The emoji Discord lists for the server and for the bot's application. */
interface DiscordEmojiSource {
    fun server(): List<DiscordEmoji>

    fun application(): List<DiscordEmoji>
}

/**
 * Reads Discord's emoji lists as maps, since the client has no method for them, so a server field
 * the generated models do not know cannot break them.
 */
@Component
@WhenCredentialsSet(Credentials.DISCORD_BOT)
class RestDiscordEmojiSource(
    private val discordRestClient: RestClient,
    @Value($$"${discord.guildId:}") private val guildId: String,
) : DiscordEmojiSource {
    override fun server(): List<DiscordEmoji> =
        discordRestClient
            .get()
            .uri("/guilds/{guild}/emojis", guildId)
            .retrieve()
            .body(LISTED)
            .orEmpty()
            .map { emojiOf(it, DiscordEmojiKind.SERVER) }

    // A bot's application shares its user's ID.
    override fun application(): List<DiscordEmoji> =
        (read("/applications/{application}/emojis", selfId)["items"] as List<*>)
            .map { emojiOf(it as Map<*, *>, DiscordEmojiKind.APPLICATION) }

    private val selfId: String by lazy { read("/users/@me").getValue("id") as String }

    private fun read(
        path: String,
        vararg ids: String,
    ): Map<String, Any?> =
        discordRestClient
            .get()
            .uri(path, *ids)
            .retrieve()
            .body(REPLY)!!

    private fun emojiOf(
        listed: Map<*, *>,
        kind: DiscordEmojiKind,
    ) = DiscordEmoji(
        id = listed["id"] as String,
        name = listed["name"] as String,
        animated = listed["animated"] == true,
        available = listed["available"] != false,
        roleIds = (listed["roles"] as? List<*>).orEmpty().map { it as String },
        kind = kind,
    )

    private companion object {
        val REPLY = object : ParameterizedTypeReference<Map<String, Any?>>() {}
        val LISTED = object : ParameterizedTypeReference<List<Map<String, Any?>>>() {}
    }
}

/**
 * The server's own emoji, the bot's application emoji and the seen emoji, every one of them, by
 * name: role-locked ones and those over the boost limit too, since a description only shows a
 * picture. An emoji seen and also the server's or the application's is listed once, as the latter.
 * Discord's lists are kept five minutes; null without a bot, or where Discord answered neither and
 * nothing was kept.
 */
@Service
class DiscordEmojiDirectory(
    private val source: ObjectProvider<DiscordEmojiSource>,
    private val seen: SeenEmojiIndex,
    clock: Clock = Clock.systemUTC(),
) {
    private val server = KeptRead<List<DiscordEmoji>>("Discord server emoji", KEPT_FOR, clock)
    private val application = KeptRead<List<DiscordEmoji>>("Discord application emoji", KEPT_FOR, clock)

    fun all(): List<DiscordEmoji>? {
        val listed = source.ifAvailable ?: return null
        val own = server.get { listed.server() }
        val apps = application.get { listed.application() }
        if (own == null && apps == null) return null
        val uploaded = own.orEmpty() + apps.orEmpty()
        val ids = uploaded.mapTo(HashSet()) { it.id }
        // The seen emoji only add names to type, so a failed read leaves them out rather than the list.
        val seenOnly =
            runCatching { seen.seen() }
                .onFailure { log.warn("Seen emoji could not be read", it) }
                .getOrDefault(emptyList())
                .filter { it.id !in ids }
        return (uploaded + seenOnly).sortedBy { it.name.lowercase() }
    }

    internal companion object {
        val KEPT_FOR: Duration = Duration.ofMinutes(5)
        private val log = LoggerFactory.getLogger(DiscordEmojiDirectory::class.java)
    }
}
