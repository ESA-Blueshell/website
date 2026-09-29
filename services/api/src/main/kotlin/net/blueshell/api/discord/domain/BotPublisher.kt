package net.blueshell.api.discord.domain

import net.blueshell.api.shared.credentials.Credentials
import net.blueshell.api.shared.credentials.WhenCredentialsSet
import net.blueshell.api.shared.job.ExplainedJobFailure
import net.blueshell.api.sync.api.DiscordEventListing
import net.blueshell.api.sync.api.DiscordImage
import net.blueshell.api.sync.api.DiscordPost
import net.blueshell.api.sync.api.DiscordPublisher
import net.blueshell.clients.discord.api.DiscordApi
import net.blueshell.clients.discord.model.CreateGuildScheduledEventRequest
import net.blueshell.clients.discord.model.GuildScheduledEventEntityTypes
import net.blueshell.clients.discord.model.UpdateGuildScheduledEventRequest
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.ParameterizedTypeReference
import org.springframework.core.io.ByteArrayResource
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.client.MultipartBodyBuilder
import org.springframework.stereotype.Component
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientResponseException
import tools.jackson.databind.json.JsonMapper
import tools.jackson.databind.node.ObjectNode
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset

/**
 * The bot's voice: messages as JSON, or as multipart where a banner goes with them, channels found
 * by name among the text channels the gateway holds and a posted message by the channel its
 * reference carries. Mentions notify only when a message is first posted; an edit is sent with no
 * mention allowed, so rewriting a post never pings anybody.
 */
@Component
@WhenCredentialsSet(Credentials.DISCORD_BOT)
class BotPublisher(
    private val api: DiscordApi,
    private val discordRestClient: RestClient,
    private val jsonMapper: JsonMapper,
    private val doors: DoorSource,
    @Value($$"${discord.guildId:}") private val guildId: String,
) : DiscordPublisher {
    override fun post(
        channel: String,
        post: DiscordPost,
    ): String =
        explained(posting(channel)) {
            val channelId = channelIdOf(channel)
            val messageId =
                withoutImageIfRefused(post.banner != null) { withBanner ->
                    val banner = post.banner?.takeIf { withBanner }
                    sendRaw(HttpMethod.POST, "/channels/{channel}/messages", payloadOf(post, banner, post.pingedRoleIds), banner, channelId)
                }
            "$channelId/$messageId"
        }

    // The attachments listed replace the message's own, so a banner taken off the event leaves the post.
    override fun edit(
        channel: String,
        reference: String,
        post: DiscordPost,
    ): Boolean =
        explained(posting(channel)) {
            val (channelId, messageId) = messageOf(channel, reference)
            stillThere {
                withoutImageIfRefused(post.banner != null) { withBanner ->
                    val banner = post.banner?.takeIf { withBanner }
                    val path = "/channels/{channel}/messages/{message}"
                    sendRaw(HttpMethod.PATCH, path, payloadOf(post, banner, notified = emptyList()), banner, channelId, messageId)
                }
            }
        }

    /*
     * A message laid out in components, whose text reads as plain text and holds twice what plain
     * content does. The empty content and embeds let an edit turn a post made as an embed into
     * one: Discord refuses the flag beside either. Built as raw JSON, as the generated client's
     * message components hold only text displays, not a gallery or an action row of buttons.
     */
    private fun payloadOf(
        post: DiscordPost,
        banner: DiscordImage?,
        notified: List<String>,
    ): ObjectNode {
        val payload = jsonMapper.createObjectNode().put("flags", COMPONENTS_V2).put("content", "")
        payload.putArray("embeds")
        val mentions = payload.putObject("allowed_mentions")
        mentions.putArray("parse")
        val roles = mentions.putArray("roles")
        notified.forEach { roles.add(it) }
        val attachments = payload.putArray("attachments")
        val components = payload.putArray("components")
        if (banner != null) {
            attachments.addObject().put("id", "0").put("filename", banner.fileName)
            components
                .addObject()
                .put("type", MEDIA_GALLERY)
                .putArray("items")
                .addObject()
                .putObject("media")
                .put("url", "attachment://${banner.fileName}")
        }
        components.addObject().put("type", TEXT_DISPLAY).put("content", post.text)
        if (post.links.isNotEmpty()) {
            val buttons = components.addObject().put("type", ACTION_ROW).putArray("components")
            for (link in post.links) {
                buttons
                    .addObject()
                    .put("type", BUTTON)
                    .put("style", LINK_STYLE)
                    .put("label", link.label)
                    .put("url", link.url)
            }
        }
        return payload
    }

    // A message goes as JSON, or as multipart where a banner goes with it.
    private fun sendRaw(
        method: HttpMethod,
        path: String,
        request: ObjectNode,
        banner: DiscordImage?,
        vararg ids: String,
    ): String {
        if (banner == null) {
            return discordRestClient
                .method(method)
                .uri(path, *ids)
                .contentType(MediaType.APPLICATION_JSON)
                .body(jsonMapper.writeValueAsString(request))
                .retrieve()
                .body(REPLY)!!
                .getValue("id") as String
        }
        val parts = MultipartBodyBuilder()
        parts.part("payload_json", jsonMapper.writeValueAsString(request), MediaType.APPLICATION_JSON)
        parts
            .part("files[0]", ByteArrayResource(banner.bytes), MediaType.parseMediaType(banner.mediaType))
            .filename(banner.fileName)
        return discordRestClient
            .method(method)
            .uri(path, *ids)
            .contentType(MediaType.MULTIPART_FORM_DATA)
            .body(parts.build())
            .retrieve()
            .body(REPLY)!!
            .getValue("id") as String
    }

    override fun delete(
        channel: String,
        reference: String,
    ) = explained(removing(channel)) {
        val (channelId, messageId) = messageOf(channel, reference)
        gone { api.deleteMessage(channelId, messageId) }
    }

    override fun findPosts(
        channel: String,
        url: String,
    ): List<String> =
        explained(reading(channel)) {
            val channelId = channelIdOf(channel)
            readAll("/channels/{channel}/messages?limit=100", channelId)
                .filter { message -> url in linksOf(message) && (message["author"] as? Map<*, *>)?.get("id") == selfId }
                .map { "$channelId/${it.getValue("id")}" }
        }

    override fun stillPosted(
        channel: String,
        reference: String,
    ): String? =
        explained(reading(channel)) {
            val (channelId, messageId) = messageOf(channel, reference)
            "$channelId/$messageId".takeIf { found("/channels/{channel}/messages/{message}", channelId, messageId) }
        }

    override fun stillListed(discordEventId: String): Boolean =
        explained(LISTING) { found("/guilds/{guild}/scheduled-events/{event}", guildId, discordEventId) }

    private fun found(
        path: String,
        vararg ids: String,
    ): Boolean =
        stillThere {
            discordRestClient
                .get()
                .uri(path, *ids)
                .retrieve()
                .toBodilessEntity()
        }

    override fun findDiscordEvents(line: String): List<String> =
        explained(LISTING) {
            readAll("/guilds/{guild}/scheduled-events", guildId)
                .filter { (it["description"] as? String).orEmpty().lines().contains(line) }
                .map { it.getValue("id") as String }
        }

    // Read as maps, so a field the generated models get wrong cannot break a lookup.
    private fun readAll(
        path: String,
        id: String,
    ): List<Map<String, Any?>> =
        discordRestClient
            .get()
            .uri(path, id)
            .retrieve()
            .body(REPLIES)
            .orEmpty()

    override fun createDiscordEvent(listing: DiscordEventListing): String =
        explained(LISTING) {
            withoutImageIfRefused(listing.cover != null) { withCover -> create(if (withCover) listing else listing.copy(cover = null)) }
        }

    override fun updateDiscordEvent(
        discordEventId: String,
        listing: DiscordEventListing,
    ): Boolean =
        explained(LISTING) {
            stillThere {
                withoutImageIfRefused(listing.cover != null) { withCover ->
                    update(discordEventId, if (withCover) listing else listing.copy(cover = null))
                }
            }
        }

    override fun deleteDiscordEvent(discordEventId: String) =
        explained(LISTING) { gone { api.deleteGuildScheduledEvent(guildId, discordEventId) } }

    override fun linkOf(
        channel: String,
        reference: String,
    ): String? {
        val (channelId, messageId) = split(reference) ?: ((roomIdOf(channel) ?: return null) to reference)
        return "https://discord.com/channels/$guildId/$channelId/$messageId"
    }

    override fun linkOfDiscordEvent(discordEventId: String) = "https://discord.com/events/$guildId/$discordEventId"

    /*
     * Sent bare and only the ID read back: the generated response model wants a cover Discord
     * leaves null, and a create that throws after Discord made the event would make it again.
     */
    private fun create(listing: DiscordEventListing): String =
        idOf(
            HttpMethod.POST,
            "/guilds/{guild}/scheduled-events",
            CreateGuildScheduledEventRequest(
                entityMetadata = mapOf("location" to listing.location),
                entityType = GuildScheduledEventEntityTypes._3,
                name = listing.name,
                privacyLevel = GUILD_ONLY,
                scheduledStartTime = requireNotNull(listing.start) { "A Discord event is made only before it starts" }.utc(),
                scheduledEndTime = listing.end.utc(),
                description = listing.description,
                image = listing.cover,
            ),
            guildId,
        )

    // The shared mapper leaves nulls out, and Discord keeps a cover it is not told is gone.
    private fun update(
        discordEventId: String,
        listing: DiscordEventListing,
    ) {
        val request =
            UpdateGuildScheduledEventRequest(
                entityMetadata = mapOf("location" to listing.location),
                name = listing.name,
                scheduledStartTime = listing.start?.utc(),
                scheduledEndTime = listing.end.utc(),
                description = listing.description,
                image = listing.cover,
            )
        val body = jsonMapper.valueToTree<ObjectNode>(request).apply { if (listing.cover == null) putNull("image") }
        idOf(HttpMethod.PATCH, "/guilds/{guild}/scheduled-events/{event}", body, guildId, discordEventId)
    }

    private fun idOf(
        method: HttpMethod,
        path: String,
        request: Any,
        vararg ids: String,
    ): String =
        discordRestClient
            .method(method)
            .uri(path, *ids)
            .contentType(MediaType.APPLICATION_JSON)
            .body(request)
            .retrieve()
            .body(REPLY)!!
            .getValue("id") as String

    // The channel list comes from the gateway, which holds none while it is not connected.
    private fun channelIdOf(channel: String): String {
        if (doors.textRooms().isEmpty()) {
            throw ExplainedJobFailure("The Discord gateway is not connected, so no channel called #$channel can be found yet.")
        }
        return roomIdOf(channel) ?: throw ExplainedJobFailure("The Discord server has no text channel called #$channel.")
    }

    private fun roomIdOf(channel: String): String? =
        doors
            .textRooms()
            .firstOrNull { plain(it.name) == plain(channel) }
            ?.id

    // A bare message ID, recorded before references carried the channel, is in the channel of that name.
    private fun messageOf(
        channel: String,
        reference: String,
    ): Pair<String, String> = split(reference) ?: (channelIdOf(channel) to reference)

    // Asked once, and only once a message links the page: another bot's post may link it too.
    private val selfId: String by lazy {
        discordRestClient
            .get()
            .uri("/users/@me")
            .retrieve()
            .body(REPLY)!!
            .getValue("id") as String
    }

    private companion object {
        // The only privacy level Discord offers for a server's events.
        const val GUILD_ONLY = 2
        const val COMPONENTS_V2 = 1 shl 15
        const val ACTION_ROW = 1
        const val BUTTON = 2
        const val TEXT_DISPLAY = 10
        const val MEDIA_GALLERY = 12
        const val LINK_STYLE = 5

        // Only the ID is read back, so a field Discord adds or leaves null cannot break it.
        val REPLY = object : ParameterizedTypeReference<Map<String, Any?>>() {}
        val REPLIES = object : ParameterizedTypeReference<List<Map<String, Any?>>>() {}
    }
}

// Where a message's buttons and embeds lead, however deep its components nest: a post made as an embed links its page from there.
private fun linksOf(message: Map<*, *>): List<Any?> =
    (message["embeds"] as? List<*>).orEmpty().map { (it as? Map<*, *>)?.get("url") } +
        (message["components"] as? List<*>).orEmpty().flatMap { buttonLinksOf(it as? Map<*, *>) }

private fun buttonLinksOf(component: Map<*, *>?): List<Any?> =
    listOf(component?.get("url")) + (component?.get("components") as? List<*>).orEmpty().flatMap { buttonLinksOf(it as? Map<*, *>) }

// A reference is the channel's ID and the message's, split by a slash.
private fun split(reference: String): Pair<String, String>? =
    reference.split('/', limit = 2).takeIf { it.size == 2 }?.let { (channelId, messageId) -> channelId to messageId }

private fun Instant.utc(): OffsetDateTime = atOffset(ZoneOffset.UTC)

/*
 * An image Discord refuses, one too large or an animated cover, leaves the message or Discord
 * event without it rather than unsent. Only a refusal of the request counts: a rate limit or a
 * missing target is passed on.
 */
private fun <T> withoutImageIfRefused(
    hasImage: Boolean,
    send: (withImage: Boolean) -> T,
): T =
    try {
        send(hasImage)
    } catch (refused: RestClientResponseException) {
        if (!hasImage || refused.statusCode.value() !in IMAGE_REFUSED) throw refused
        send(false)
    }

private val IMAGE_REFUSED = setOf(HttpStatus.BAD_REQUEST.value(), HttpStatus.CONTENT_TOO_LARGE.value())

// Editing what somebody removed by hand answers false, so the caller can make it again.
private fun stillThere(call: () -> Unit): Boolean =
    try {
        call()
        true
    } catch (refused: RestClientResponseException) {
        if (refused.statusCode.value() != HttpStatus.NOT_FOUND.value()) throw refused
        false
    }

/**
 * What a call to Discord was for, so a refusal can say what the bot may not do and which
 * permission it lacks; Discord's own answer names neither.
 */
private class Doing(
    val thing: String,
    val lacking: String,
)

private fun posting(channel: String) =
    Doing(
        "the #$channel post",
        "The bot may not post in #$channel: it needs Send Messages, Embed Links, Attach Files and Mention All Roles there.",
    )

private fun reading(channel: String) =
    Doing("the #$channel post", "The bot may not read #$channel: it needs View Channel and Read Message History there.")

private fun removing(channel: String) =
    Doing("the #$channel post", "The bot may not remove its post in #$channel: it needs View Channel there.")

private val LISTING = Doing("the Discord event", "The bot may not keep the server's Events list: it needs Create Events.")

private const val UNAVAILABLE = "Discord is unavailable."

// What Discord says of a field over its limit, of one text display over it, and of a message's text over its total.
private val TOO_LONG = listOf("BASE_TYPE_MAX_LENGTH", "BASE_TYPE_BAD_LENGTH", "COMPONENT_DISPLAYABLE_TEXT_SIZE_EXCEEDED")

/*
 * The refusals the board can act on, in plain words; any other is passed on as it came. Wraps the
 * whole call, outside the handling of a message already gone or an image refused, which are answers.
 */
private fun <T> explained(
    doing: Doing,
    call: () -> T,
): T =
    try {
        call()
    } catch (refused: RestClientResponseException) {
        throw plainly(refused, doing) ?: refused
    } catch (unreachable: ResourceAccessException) {
        throw ExplainedJobFailure(UNAVAILABLE, unreachable)
    }

private fun plainly(
    refused: RestClientResponseException,
    doing: Doing,
): ExplainedJobFailure? {
    val status = refused.statusCode
    val sentence =
        when {
            status.value() == HttpStatus.FORBIDDEN.value() -> doing.lacking
            status.value() == HttpStatus.TOO_MANY_REQUESTS.value() -> "Discord is rate limiting the bot."
            status.is5xxServerError -> UNAVAILABLE
            status.value() == HttpStatus.BAD_REQUEST.value() && TOO_LONG.any { refused.responseBodyAsString.contains(it) } ->
                "Discord refuses ${doing.thing} as too long."
            else -> null
        }
    return sentence?.let { ExplainedJobFailure(it, refused) }
}

// Removing what is already gone is done.
private fun gone(call: () -> Unit) {
    try {
        call()
    } catch (refused: RestClientResponseException) {
        if (refused.statusCode.value() != HttpStatus.NOT_FOUND.value()) throw refused
    }
}
