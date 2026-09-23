package net.blueshell.api.discord.domain

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap

/** A place on the server the site links to, by the key its address carries. */
enum class DiscordDoor(
    val key: String,
) {
    WELCOME("welcome"),
    BOARD("board"),
    SUGGESTIONS("suggestions"),
    ;

    companion object {
        fun of(key: String): DiscordDoor? = entries.firstOrNull { it.key == key }
    }
}

/**
 * The text channel behind each door, by name, so the dev bot's test server works with channels of
 * the same names. [fallback] is where every door leads while the bot cannot say.
 */
@ConfigurationProperties(prefix = "discord.doors")
data class DiscordDoorsProperties(
    val welcome: String = "welcome",
    val board: String = "board-questions",
    val suggestions: String = "sitecie-suggestions",
    val fallback: String = "https://discord.gg/23YMFQy",
) {
    fun channelOf(door: DiscordDoor): String =
        when (door) {
            DiscordDoor.WELCOME -> welcome
            DiscordDoor.BOARD -> board
            DiscordDoor.SUGGESTIONS -> suggestions
        }
}

/** A text channel the gateway knows. */
data class TextRoom(
    val id: String,
    val guildId: String,
    val name: String,
)

/** The server's text channels, and invites into them. */
interface DoorSource {
    /** Empty until the gateway has the server. */
    fun textRooms(): List<TextRoom>

    /** A permanent invite into the channel, or null where the bot may not make one. */
    fun invite(channelId: String): String?
}

/**
 * Where each door leads: an invite the bot made into its channel, or the channel itself for a
 * member. A door whose channel is not found, or a bot that is not there, leads to the fallback
 * invite. A missing channel is logged once, with the channels there are, so a rename is easy to
 * follow in the config.
 */
@Service
@EnableConfigurationProperties(DiscordDoorsProperties::class)
class DiscordDoorService(
    private val source: ObjectProvider<DoorSource>,
    private val properties: DiscordDoorsProperties,
    @Value($$"${discord.baseUrl:https://discord.com/api/v10}") baseUrl: String,
) {
    private val appUrl = baseUrl.substringBefore("/api")
    private val reported = ConcurrentHashMap.newKeySet<String>()

    fun invite(door: DiscordDoor): String = roomOf(door)?.let { source.ifAvailable?.invite(it.id) } ?: properties.fallback

    fun channel(door: DiscordDoor): String = roomOf(door)?.let { "$appUrl/channels/${it.guildId}/${it.id}" } ?: properties.fallback

    private fun roomOf(door: DiscordDoor): TextRoom? {
        val rooms = source.ifAvailable?.textRooms().orEmpty()
        if (rooms.isEmpty()) return null
        val wanted = properties.channelOf(door)
        val found = rooms.firstOrNull { plain(it.name) == plain(wanted) }
        if (found == null && reported.add(wanted)) {
            log.warn(
                "No Discord text channel is called {} for the {} link, which falls back; the text channels are {}",
                wanted,
                door.key,
                rooms.joinToString { it.name },
            )
        }
        return found
    }

    private companion object {
        val log = LoggerFactory.getLogger(DiscordDoorService::class.java)
    }
}

/* A channel's name as compared: case, emoji and separators aside, since servers decorate names. */
internal fun plain(name: String): String = name.lowercase().filter { it.isLetterOrDigit() }
