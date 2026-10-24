package net.blueshell.api.discord.domain

import net.blueshell.api.shared.credentials.Credentials
import net.blueshell.api.shared.credentials.WhenCredentialsSet
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.ParameterizedTypeReference
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient
import java.time.Clock
import java.time.Duration

/** Where an emoji was uploaded: to the association's server, or to the bot's Discord application. */
enum class DiscordEmojiKind { SERVER, APPLICATION }

/**
 * A picture uploaded to Discord, which a description writes as `<:name:id>`. It is not
 * [available] while over the server's boost limit, and [roleIds] lists the roles it is locked to.
 */
data class DiscordEmoji(
    val id: String,
    val name: String,
    val animated: Boolean,
    val available: Boolean,
    val roleIds: List<String>,
    val kind: DiscordEmojiKind,
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
 * The server's own emoji and the bot's application emoji, every one of them, by name: role-locked
 * ones and those over the boost limit too, since a description only shows a picture. Each list is
 * kept five minutes; null without a bot, or where Discord answered neither and nothing was kept.
 */
@Service
class DiscordEmojiDirectory(
    private val source: ObjectProvider<DiscordEmojiSource>,
    clock: Clock = Clock.systemUTC(),
) {
    private val server = KeptRead<List<DiscordEmoji>>("Discord server emoji", KEPT_FOR, clock)
    private val application = KeptRead<List<DiscordEmoji>>("Discord application emoji", KEPT_FOR, clock)

    fun all(): List<DiscordEmoji>? {
        val listed = source.ifAvailable ?: return null
        val own = server.get { listed.server() }
        val bots = application.get { listed.application() }
        if (own == null && bots == null) return null
        return (own.orEmpty() + bots.orEmpty()).sortedBy { it.name.lowercase() }
    }

    internal companion object {
        val KEPT_FOR: Duration = Duration.ofMinutes(5)
    }
}
