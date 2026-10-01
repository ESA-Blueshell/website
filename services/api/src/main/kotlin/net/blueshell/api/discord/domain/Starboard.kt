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
import java.time.Instant
import java.time.OffsetDateTime

/** A message the server starred, as the starboard bot reposted it. */
data class StarredMessage(
    /** The starred message's own ID, not the repost's. */
    val id: String,
    /** The author's name across Discord. */
    val authorName: String,
    /** The author's name in this server, where they set one. */
    val authorNickname: String?,
    val avatar: String?,
    /** Discord markdown, mentions and server emoji included; null for a picture alone. */
    val text: String?,
    val image: String?,
    val stars: Int,
    val channelId: String,
    /** Opens the starred message in Discord. */
    val href: String,
    val postedAt: Instant,
)

/** A starred message, with its channel's name where everybody in the server can see that channel. */
data class StarboardEntry(
    val message: StarredMessage,
    val channel: String?,
)

/** The starboard channel and its recent reposts, newest first. */
data class Starboard(
    val channelId: String,
    val reposts: List<StarredMessage>,
)

/** The starboard as Discord has it; null where Discord could not be read. */
fun interface StarboardSource {
    fun recent(): Starboard?
}

private val STARS = Regex("""\*\*(\d+)\*\*""")
private val CHANNEL = Regex("""<#(\d+)>""")
private val JUMP = Regex("""https://discord\.com/channels/\d+/\d+/(\d+)""")

// The bot writes the author as "name  - nickname", and the name alone where there is no nickname.
private const val NICKNAME_AFTER = "  - "

/**
 * One repost read into the message it stars. The starboard bot writes `💫 **12** <#channel>` over
 * an embed holding the author, the text, a picture and a Source field with the jump link. Anything
 * else in the channel is not a repost and reads as null.
 */
internal fun starredMessageOf(repost: Map<String, Any?>): StarredMessage? {
    val content = repost["content"] as? String ?: return null
    val stars = STARS.find(content)?.let { it.groupValues[1].toIntOrNull() } ?: return null
    val channelId = CHANNEL.find(content)?.let { it.groupValues[1] } ?: return null
    val embed = (repost["embeds"] as? List<*>)?.firstOrNull() as? Map<*, *> ?: return null
    val jump =
        (embed["fields"] as? List<*>)
            .orEmpty()
            .mapNotNull { (it as? Map<*, *>)?.get("value") as? String }
            .firstNotNullOfOrNull { JUMP.find(it) } ?: return null
    val author = embed["author"] as? Map<*, *> ?: return null
    val named = author["name"] as? String ?: return null
    val postedAt = (embed["timestamp"] as? String)?.let { OffsetDateTime.parse(it).toInstant() } ?: return null
    return StarredMessage(
        id = jump.groupValues[1],
        authorName = named.substringBefore(NICKNAME_AFTER).trim(),
        authorNickname = named.substringAfter(NICKNAME_AFTER, "").trim().ifEmpty { null },
        avatar = author["icon_url"] as? String,
        text = (embed["description"] as? String)?.ifBlank { null },
        image = (embed["image"] as? Map<*, *>)?.get("url") as? String,
        stars = stars,
        channelId = channelId,
        href = jump.value,
        postedAt = postedAt,
    )
}

/**
 * Reads the starboard channel, found by name among the text channels the gateway holds, over REST.
 * The reposts are read as maps, so a field the generated models get wrong cannot break the feed.
 * Reading another bot's embeds takes the Message Content intent.
 */
@Component
@WhenCredentialsSet(Credentials.DISCORD_BOT)
class RestStarboardSource(
    private val discordRestClient: RestClient,
    private val doors: DoorSource,
    @Value($$"${discord.starboard.channel:starboard}") private val channel: String,
) : StarboardSource {
    override fun recent(): Starboard? {
        val channelId = doors.textRooms().firstOrNull { plain(it.name) == plain(channel) }?.id ?: return null
        val reposts =
            discordRestClient
                .get()
                .uri("/channels/{channel}/messages?limit=100", channelId)
                .retrieve()
                .body(REPOSTS)
                .orEmpty()
                .mapNotNull(::starredMessageOf)
        return Starboard(channelId, reposts)
    }

    private companion object {
        val REPOSTS = object : ParameterizedTypeReference<List<Map<String, Any?>>>() {}
    }
}

/**
 * The starboard as the home page shows it, most stars first. A message makes it with at least
 * [MIN_STARS] stars and at most [MAX_AGE] old, from any channel: the bot has already copied it
 * into the starboard, so the starboard is the room the public site quotes, and a starboard
 * everybody in the server cannot see shows nothing. A members-only channel is never named. Kept
 * for [KEPT_FOR], longer than the voice rooms, since stars move slowly. Null without a bot, or
 * while Discord has never answered.
 */
@Service
class StarboardService(
    private val source: ObjectProvider<StarboardSource>,
    private val channels: DiscordChannelDirectory,
    private val clock: Clock = Clock.systemUTC(),
) {
    private val kept = KeptRead<Starboard>("Discord starboard", KEPT_FOR, clock)

    fun entries(): List<StarboardEntry>? {
        val starboard = source.ifAvailable ?: return null
        val recent = kept.get { starboard.recent() } ?: return null
        val open = channels.open()?.associate { it.id to it.name } ?: return null
        if (recent.channelId !in open) return emptyList()
        val since = clock.instant().minus(MAX_AGE)
        return recent.reposts
            .filter { it.stars >= MIN_STARS && it.postedAt >= since }
            .map { StarboardEntry(it, open[it.channelId]) }
            .sortedWith(compareByDescending<StarboardEntry> { it.message.stars }.thenByDescending { it.message.postedAt })
            .take(SHOWN)
    }

    internal companion object {
        const val MIN_STARS = 3
        val MAX_AGE: Duration = Duration.ofDays(30)
        val KEPT_FOR: Duration = Duration.ofMinutes(10)
        const val SHOWN = 25
    }
}
