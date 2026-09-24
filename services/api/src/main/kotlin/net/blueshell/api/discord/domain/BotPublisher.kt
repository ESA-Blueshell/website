package net.blueshell.api.discord.domain

import net.blueshell.api.sync.api.DiscordEmbed
import net.blueshell.api.sync.api.DiscordEventListing
import net.blueshell.api.sync.api.DiscordImage
import net.blueshell.api.sync.api.DiscordPost
import net.blueshell.api.sync.api.DiscordPublisher
import net.blueshell.clients.discord.api.DiscordApi
import net.blueshell.clients.discord.model.CreateGuildScheduledEventRequest
import net.blueshell.clients.discord.model.GuildScheduledEventEntityTypes
import net.blueshell.clients.discord.model.MessageAllowedMentionsRequest
import net.blueshell.clients.discord.model.MessageAttachmentRequest
import net.blueshell.clients.discord.model.MessageCreateRequest
import net.blueshell.clients.discord.model.MessageEditRequestPartial
import net.blueshell.clients.discord.model.RichEmbed
import net.blueshell.clients.discord.model.RichEmbedField
import net.blueshell.clients.discord.model.UpdateGuildScheduledEventRequest
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.context.annotation.Profile
import org.springframework.core.ParameterizedTypeReference
import org.springframework.core.io.ByteArrayResource
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.client.MultipartBodyBuilder
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientResponseException
import tools.jackson.databind.json.JsonMapper
import java.net.URI
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset

/**
 * The bot's voice: messages through the generated REST client, or as multipart where a banner
 * goes with them, channels found by name among the text channels the gateway holds. Mentions
 * notify only when a message is first posted; an edit is sent with no mention allowed, so
 * rewriting a post never pings anybody.
 */
@Component
@Profile("!test")
@ConditionalOnExpression(DISCORD_TOKEN_SET)
class BotPublisher(
    private val api: DiscordApi,
    private val discordRestClient: RestClient,
    private val jsonMapper: JsonMapper,
    private val doors: ObjectProvider<DoorSource>,
    @Value($$"${discord.guildId:}") private val guildId: String,
) : DiscordPublisher {
    override fun post(
        channel: String,
        post: DiscordPost,
    ): String {
        val channelId = channelIdOf(channel)
        return bannerOptional(post) { banner ->
            val request =
                MessageCreateRequest(
                    content = mentionsOf(post),
                    embeds = listOf(post.embed.asRichEmbed()),
                    allowedMentions = MessageAllowedMentionsRequest(parse = emptySet(), roles = post.pingedRoleIds.toSet()),
                    attachments = attachmentsOf(banner),
                )
            if (banner == null) {
                api.createMessage(channelId, request).id
            } else {
                withFile(HttpMethod.POST, "/channels/{channel}/messages", request, banner, channelId)
            }
        }
    }

    /* The attachments listed replace the message's own, so a banner taken off the event leaves the post. */
    override fun edit(
        channel: String,
        messageId: String,
        post: DiscordPost,
    ) {
        val channelId = channelIdOf(channel)
        bannerOptional(post) { banner ->
            val request =
                MessageEditRequestPartial(
                    content = mentionsOf(post) ?: "",
                    embeds = listOf(post.embed.asRichEmbed()),
                    allowedMentions = MessageAllowedMentionsRequest(parse = emptySet()),
                    attachments = attachmentsOf(banner),
                )
            if (banner == null) {
                api.updateMessage(channelId, messageId, request)
            } else {
                withFile(HttpMethod.PATCH, "/channels/{channel}/messages/{message}", request, banner, channelId, messageId)
            }
        }
    }

    /*
     * A message with a file goes as multipart. The banner is the message's attachment rather than
     * the embed's image: Discord draws an attachment above the embed, and an embed's image under it.
     */
    private fun withFile(
        method: HttpMethod,
        path: String,
        request: Any,
        banner: DiscordImage,
        vararg ids: String,
    ): String {
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
        messageId: String,
    ) = gone { api.deleteMessage(channelIdOf(channel), messageId) }

    override fun createDiscordEvent(listing: DiscordEventListing): String = coverOptional(listing) { create(it) }

    override fun updateDiscordEvent(
        discordEventId: String,
        listing: DiscordEventListing,
    ) = coverOptional(listing) { update(discordEventId, it) }

    override fun deleteDiscordEvent(discordEventId: String) = gone { api.deleteGuildScheduledEvent(guildId, discordEventId) }

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
                scheduledStartTime = listing.start.utc(),
                scheduledEndTime = listing.end.utc(),
                description = listing.description,
                image = listing.cover,
            ),
            guildId,
        )

    private fun update(
        discordEventId: String,
        listing: DiscordEventListing,
    ) {
        idOf(
            HttpMethod.PATCH,
            "/guilds/{guild}/scheduled-events/{event}",
            UpdateGuildScheduledEventRequest(
                entityMetadata = mapOf("location" to listing.location),
                name = listing.name,
                scheduledStartTime = listing.start.utc(),
                scheduledEndTime = listing.end.utc(),
                description = listing.description,
                image = listing.cover,
            ),
            guildId,
            discordEventId,
        )
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

    private fun channelIdOf(channel: String): String =
        doors.ifAvailable
            ?.textRooms()
            ?.firstOrNull { plain(it.name) == plain(channel) }
            ?.id
            ?: error("No Discord text channel is called $channel")

    private companion object {
        /* The only privacy level Discord offers for a server's events. */
        const val GUILD_ONLY = 2

        /* Only the ID is read back, so a field Discord adds or leaves null cannot break it. */
        val REPLY = object : ParameterizedTypeReference<Map<String, Any?>>() {}
    }
}

/* Mentions go in the message text: Discord notifies nobody named only inside an embed. */
private fun mentionsOf(post: DiscordPost): String? = post.pingedRoleIds.takeIf { it.isNotEmpty() }?.joinToString(" ") { "<@&$it>" }

private fun DiscordEmbed.asRichEmbed() =
    RichEmbed(
        title = title,
        url = URI(url),
        description = description,
        fields = fields.map { (name, value) -> RichEmbedField(name = name, value = value, inline = true) },
    )

private fun attachmentsOf(banner: DiscordImage?) = listOfNotNull(banner?.let { MessageAttachmentRequest(id = "0", filename = it.fileName) })

/* A banner Discord refuses, such as one over its size limit, leaves the post without one rather than unposted. */
private fun <T> bannerOptional(
    post: DiscordPost,
    send: (DiscordImage?) -> T,
): T =
    try {
        send(post.banner)
    } catch (refused: RestClientResponseException) {
        if (post.banner == null || !refused.statusCode.is4xxClientError) throw refused
        send(null)
    }

private fun Instant.utc(): OffsetDateTime = atOffset(ZoneOffset.UTC)

/* A cover Discord refuses, such as an animated banner, leaves the Discord event without one rather than unlisted. */
private fun <T> coverOptional(
    listing: DiscordEventListing,
    send: (DiscordEventListing) -> T,
): T =
    try {
        send(listing)
    } catch (refused: RestClientResponseException) {
        if (listing.cover == null || !refused.statusCode.is4xxClientError) throw refused
        send(listing.copy(cover = null))
    }

/* Removing what is already gone is done. */
private fun gone(call: () -> Unit) {
    try {
        call()
    } catch (refused: RestClientResponseException) {
        if (refused.statusCode.value() != HttpStatus.NOT_FOUND.value()) throw refused
    }
}
