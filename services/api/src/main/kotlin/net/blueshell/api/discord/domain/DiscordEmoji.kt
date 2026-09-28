package net.blueshell.api.discord.domain

import net.blueshell.clients.discord.api.DiscordApi
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.Duration

/** A picture uploaded to the server, which a description writes as `<:name:id>`. */
data class DiscordEmoji(
    val id: String,
    val name: String,
    val animated: Boolean,
)

/**
 * The server's own emoji, every one of them, by name: role-locked ones and those over the boost
 * limit too, since a description only shows a picture. Read off the guild, kept five minutes, and
 * null without a bot or where Discord did not answer and nothing was kept.
 */
@Service
class DiscordEmojiDirectory(
    private val api: ObjectProvider<DiscordApi>,
    @Value($$"${discord.guildId:}") private val guildId: String,
    clock: Clock = Clock.systemUTC(),
) {
    private val kept = KeptRead<List<DiscordEmoji>>("Discord emoji", KEPT_FOR, clock)

    fun all(): List<DiscordEmoji>? {
        val client = api.ifAvailable ?: return null
        return kept.get {
            client
                .getGuild(guildId, false)
                .emojis
                ?.map { DiscordEmoji(it.id, it.name, it.animated) }
                ?.sortedBy { it.name.lowercase() }
        }
    }

    internal companion object {
        val KEPT_FOR: Duration = Duration.ofMinutes(5)
    }
}
