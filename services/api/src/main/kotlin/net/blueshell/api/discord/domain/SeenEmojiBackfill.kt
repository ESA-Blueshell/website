package net.blueshell.api.discord.domain

import net.blueshell.api.discord.persistence.SeenEmojiBackfillPlaceRepository
import net.blueshell.api.shared.credentials.Credentials
import net.blueshell.api.shared.credentials.WhenCredentialsSet
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientResponseException
import java.time.OffsetDateTime

/** The association's server's message history, as the bot may read it. */
interface DiscordHistory {
    /** The bot's own user ID. */
    val selfId: String

    /** Every channel, thread and forum post the bot can see that holds messages. */
    fun places(): List<String>

    /** Up to [PAGE] messages of [channelId] older than [before], newest first; null where the bot may not read it. */
    fun page(
        channelId: String,
        before: String?,
    ): List<Map<String, Any?>>?
}

internal const val PAGE = 100

/**
 * Reads the history over REST, as maps, since the generated client has no models for most of it.
 * Text and announcement channels hold messages; they, forums and media channels hold threads, and a
 * forum post is a thread. Archived public threads are listed per parent, active ones server-wide.
 */
@Component
@WhenCredentialsSet(Credentials.DISCORD_BOT)
class RestDiscordHistory(
    private val discordRestClient: RestClient,
    @Value($$"${discord.guildId:}") private val guildId: String,
) : DiscordHistory {
    override val selfId: String by lazy { read("/users/@me")!!.getValue("id") as String }

    override fun places(): List<String> {
        val channels = list("/guilds/{guild}/channels", guildId).orEmpty()
        val holdingMessages = channels.filter { it["type"] in MESSAGE_CHANNELS }.map { it.getValue("id") as String }
        val holdingThreads = channels.filter { it["type"] in THREAD_PARENTS }.map { it.getValue("id") as String }
        val active = idsOf(read("/guilds/{guild}/threads/active", guildId)?.get("threads"))
        return (holdingMessages + active + holdingThreads.flatMap(::archivedThreads)).distinct()
    }

    private fun archivedThreads(parentId: String): List<String> {
        val found = mutableListOf<String>()
        var before: String? = null
        while (true) {
            val query = mapOf("limit" to PAGE, "before" to before)
            val reply = read("/channels/{channel}/threads/archived/public", parentId, query = query) ?: return found
            val threads = (reply["threads"] as? List<*>).orEmpty().map { it as Map<*, *> }
            found += threads.map { it["id"] as String }
            if (reply["has_more"] != true || threads.isEmpty()) return found
            before = (threads.last()["thread_metadata"] as Map<*, *>)["archive_timestamp"] as String
        }
    }

    override fun page(
        channelId: String,
        before: String?,
    ): List<Map<String, Any?>>? = list("/channels/{channel}/messages", channelId, query = mapOf("limit" to PAGE, "before" to before))

    private fun read(
        path: String,
        vararg ids: String,
        query: Map<String, Any?> = emptyMap(),
    ): Map<String, Any?>? = readable { request(path, ids, query).body(REPLY) }

    private fun list(
        path: String,
        vararg ids: String,
        query: Map<String, Any?> = emptyMap(),
    ): List<Map<String, Any?>>? = readable { request(path, ids, query).body(REPLIES) }

    private fun request(
        path: String,
        ids: Array<out String>,
        query: Map<String, Any?>,
    ) = discordRestClient
        .get()
        .uri { builder ->
            builder.path(path)
            query.forEach { (name, value) -> if (value != null) builder.queryParam(name, value) }
            builder.build(*ids)
        }.retrieve()

    // A channel the bot may not read, or one deleted meanwhile, holds nothing it can count.
    private fun <T> readable(read: () -> T): T? =
        try {
            read()
        } catch (refused: RestClientResponseException) {
            if (refused.statusCode.value() in UNREADABLE) null else throw refused
        }

    private fun idsOf(threads: Any?): List<String> = (threads as? List<*>).orEmpty().map { (it as Map<*, *>)["id"] as String }

    private companion object {
        val REPLY = object : ParameterizedTypeReference<Map<String, Any?>>() {}
        val REPLIES = object : ParameterizedTypeReference<List<Map<String, Any?>>>() {}
        val UNREADABLE = setOf(HttpStatus.FORBIDDEN.value(), HttpStatus.NOT_FOUND.value())

        // Discord's channel types: text and announcement hold messages; they, forum and media hold threads.
        val MESSAGE_CHANNELS = setOf(0, 5)
        val THREAD_PARENTS = setOf(0, 5, 15, 16)
    }
}

/** The server emoji one message of the history shows used, in its text or as a reaction. The bot's own text is left out. */
internal fun sightingsIn(
    message: Map<String, Any?>,
    selfId: String,
): List<EmojiSighting> {
    val messageId = message["id"] as? String ?: return emptyList()
    val at = (message["timestamp"] as? String)?.let { OffsetDateTime.parse(it).toInstant() } ?: return emptyList()
    val author = (message["author"] as? Map<*, *>)?.get("id")
    val inText = if (author == selfId) emptyList() else emojiIn(message["content"] as? String ?: "", messageId, at)
    val reacted =
        (message["reactions"] as? List<*>).orEmpty().mapNotNull { reaction ->
            reaction as Map<*, *>
            // Only the bot reacted with it.
            if (reaction["me"] == true && reaction["count"] == 1) return@mapNotNull null
            val emoji = reaction["emoji"] as? Map<*, *> ?: return@mapNotNull null
            val id = emoji["id"] as? String ?: return@mapNotNull null
            EmojiSighting(id, emoji["name"] as? String ?: return@mapNotNull null, emoji["animated"] == true, messageId, at)
        }
    return inText + reacted
}

/**
 * Backfills the seen emoji from the server's history, one bounded run at a time: each place keeps
 * where it was, so a run that stops resumes there, and counting is per message, so a rerun or a
 * message also seen live counts once.
 */
@Service
class SeenEmojiBackfill(
    private val history: ObjectProvider<DiscordHistory>,
    private val places: SeenEmojiBackfillPlaceRepository,
    private val index: SeenEmojiIndex,
) {
    /** Adds every place the bot can see now; false without a bot. */
    fun discover(): Boolean {
        val source = history.ifAvailable ?: return false
        source.places().forEach(places::add)
        return true
    }

    /** Reads up to [pages] pages, place by place; true while a place has more to read. */
    fun readSome(pages: Int): Boolean {
        val source = history.ifAvailable ?: return false
        repeat(pages) {
            val place = places.findFirstByDoneFalseOrderByIdAsc() ?: return false
            val page = source.page(place.id, place.beforeMessageId)
            if (page == null) {
                places.move(place.id, place.beforeMessageId, done = true)
            } else {
                page.forEach { message -> sightingsIn(message, source.selfId).forEach(index::saw) }
                places.move(place.id, page.lastOrNull()?.get("id") as String? ?: place.beforeMessageId, done = page.size < PAGE)
            }
        }
        return places.findFirstByDoneFalseOrderByIdAsc() != null
    }
}
