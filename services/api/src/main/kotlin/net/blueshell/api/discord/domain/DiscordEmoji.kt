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

/**
 * A picture uploaded to the server, which a description writes as `<:name:id>`. It is not
 * [available] while over the server's boost limit, and [roleIds] lists the roles it is locked to.
 */
data class DiscordEmoji(
    val id: String,
    val name: String,
    val animated: Boolean,
    val available: Boolean,
    val roleIds: List<String>,
)

/** The server's own emoji, as Discord lists them. */
fun interface ServerEmojiSource {
    fun emoji(): List<DiscordEmoji>
}

/**
 * Reads Discord's list of the server's emoji rather than the whole server, so a server field the
 * generated models do not know cannot break it. Read as maps, since the client has no method for it.
 */
@Component
@WhenCredentialsSet(Credentials.DISCORD_BOT)
class RestServerEmojiSource(
    private val discordRestClient: RestClient,
    @Value($$"${discord.guildId:}") private val guildId: String,
) : ServerEmojiSource {
    override fun emoji(): List<DiscordEmoji> =
        discordRestClient
            .get()
            .uri("/guilds/{guild}/emojis", guildId)
            .retrieve()
            .body(LISTED)
            .orEmpty()
            .map(::emojiOf)

    private fun emojiOf(listed: Map<String, Any?>) =
        DiscordEmoji(
            id = listed.getValue("id") as String,
            name = listed.getValue("name") as String,
            animated = listed["animated"] == true,
            available = listed["available"] != false,
            roleIds = (listed["roles"] as? List<*>).orEmpty().map { it as String },
        )

    private companion object {
        val LISTED = object : ParameterizedTypeReference<List<Map<String, Any?>>>() {}
    }
}

/**
 * The server's own emoji, every one of them, by name: role-locked ones and those over the boost
 * limit too, since a description only shows a picture. Kept five minutes, and null without a bot
 * or where Discord did not answer and nothing was kept.
 */
@Service
class DiscordEmojiDirectory(
    private val source: ObjectProvider<ServerEmojiSource>,
    clock: Clock = Clock.systemUTC(),
) {
    private val kept = KeptRead<List<DiscordEmoji>>("Discord emoji", KEPT_FOR, clock)

    fun all(): List<DiscordEmoji>? {
        val listed = source.ifAvailable ?: return null
        return kept.get { listed.emoji().sortedBy { it.name.lowercase() } }
    }

    internal companion object {
        val KEPT_FOR: Duration = Duration.ofMinutes(5)
    }
}
