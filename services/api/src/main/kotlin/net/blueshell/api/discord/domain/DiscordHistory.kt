package net.blueshell.api.discord.domain

import net.blueshell.api.shared.credentials.Credentials
import net.blueshell.api.shared.credentials.WhenCredentialsSet
import net.dv8tion.jda.api.requests.GatewayIntent
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientResponseException
import java.time.OffsetDateTime

/** One page of a place's history: the emoji it shows used, where the next, older page starts, and how many messages it held. */
data class HistoryPage(
    val sightings: List<EmojiSighting>,
    val oldestMessageId: String?,
    val last: Boolean,
    val messages: Int = 0,
)

/** The association's server's message history, as the bot may read it. */
interface DiscordHistory {
    /** Whether Discord sends the bot message text: only with Message Content, over the gateway and REST alike. */
    fun readsText(): Boolean

    /** Every channel, thread and forum post the bot can see that holds messages. */
    fun places(): List<String>

    /** The page of [channelId] older than [before], or the newest; null where the bot may not read it. */
    fun page(
        channelId: String,
        before: String?,
    ): HistoryPage?
}

/**
 * Reads the history over REST, as maps, since the generated client has no models for most of it.
 * Archived public threads are listed per parent, active ones server-wide; a forum post is a thread.
 */
@Component
@WhenCredentialsSet(Credentials.DISCORD_BOT)
class RestDiscordHistory(
    private val discordRestClient: RestClient,
    @Value($$"${discord.guildId:}") private val guildId: String,
) : DiscordHistory {
    private val selfId: String by lazy { read("/users/@me")!!.getValue("id") as String }

    override fun readsText(): Boolean {
        val flags = (read("/applications/@me")?.get("flags") as? Number)?.toInt() ?: 0
        return GatewayIntent.MESSAGE_CONTENT in privilegedIntentsOf(flags)
    }

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
            val query = mapOf("limit" to MESSAGES_PER_PAGE, "before" to before)
            val reply = read("/channels/{channel}/threads/archived/public", parentId, query = query) ?: return found
            val threads = (reply["threads"] as? List<*>).orEmpty().map { it as Map<*, *> }
            found += threads.map { it["id"] as String }
            if (reply["has_more"] != true || threads.isEmpty()) return found
            // Discord writes the time with an offset; its plus would be sent as it is and read as a space.
            val archivedAt = (threads.last()["thread_metadata"] as Map<*, *>)["archive_timestamp"] as String
            before = OffsetDateTime.parse(archivedAt).toInstant().toString()
        }
    }

    override fun page(
        channelId: String,
        before: String?,
    ): HistoryPage? {
        val query = mapOf("limit" to MESSAGES_PER_PAGE, "before" to before)
        val messages = list("/channels/{channel}/messages", channelId, query = query) ?: return null
        return HistoryPage(
            sightings = messages.flatMap { sightingsIn(it, selfId) },
            oldestMessageId = messages.lastOrNull()?.get("id") as String? ?: before,
            last = messages.size < MESSAGES_PER_PAGE,
            messages = messages.size,
        )
    }

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
        const val MESSAGES_PER_PAGE = 100
        val REPLY = object : ParameterizedTypeReference<Map<String, Any?>>() {}
        val REPLIES = object : ParameterizedTypeReference<List<Map<String, Any?>>>() {}
        val UNREADABLE = setOf(HttpStatus.FORBIDDEN.value(), HttpStatus.NOT_FOUND.value())

        // Text, voice, announcement and stage channels hold messages; text, announcement, forum and media ones hold threads.
        val MESSAGE_CHANNELS = setOf(0, 2, 5, 13)
        val THREAD_PARENTS = setOf(0, 5, 15, 16)
    }
}

/** The server emoji one message shows used, in its text or as a reaction. The bot's own text is left out. */
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
