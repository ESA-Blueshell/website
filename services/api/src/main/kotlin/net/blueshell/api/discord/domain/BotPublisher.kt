package net.blueshell.api.discord.domain

import net.blueshell.api.sync.api.DiscordEmbed
import net.blueshell.api.sync.api.DiscordEventListing
import net.blueshell.api.sync.api.DiscordPost
import net.blueshell.api.sync.api.DiscordPublisher
import net.blueshell.clients.discord.api.DiscordApi
import net.blueshell.clients.discord.model.CreateGuildScheduledEventRequest
import net.blueshell.clients.discord.model.GuildScheduledEventEntityTypes
import net.blueshell.clients.discord.model.MessageAllowedMentionsRequest
import net.blueshell.clients.discord.model.MessageCreateRequest
import net.blueshell.clients.discord.model.MessageEditRequestPartial
import net.blueshell.clients.discord.model.RichEmbed
import net.blueshell.clients.discord.model.RichEmbedField
import net.blueshell.clients.discord.model.RichEmbedImage
import net.blueshell.clients.discord.model.UpdateGuildScheduledEventRequest
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.context.annotation.Profile
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClientResponseException
import java.net.URI
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset

/**
 * The bot's voice: messages through the generated REST client, channels found by name among the
 * text channels the gateway holds. Mentions notify only when a message is first posted; an edit
 * is sent with no mention allowed, so rewriting a post never pings anybody.
 */
@Component
@Profile("!test")
@ConditionalOnExpression(DISCORD_TOKEN_SET)
class BotPublisher(
    private val api: DiscordApi,
    private val doors: ObjectProvider<DoorSource>,
    @Value($$"${discord.guildId:}") private val guildId: String,
) : DiscordPublisher {
    override fun post(
        channel: String,
        post: DiscordPost,
    ): String =
        api
            .createMessage(
                channelIdOf(channel),
                MessageCreateRequest(
                    content = mentionsOf(post),
                    embeds = listOf(post.embed.asRichEmbed()),
                    allowedMentions = MessageAllowedMentionsRequest(parse = emptySet(), roles = post.pingedRoleIds.toSet()),
                ),
            ).id

    override fun edit(
        channel: String,
        messageId: String,
        post: DiscordPost,
    ) {
        api.updateMessage(
            channelIdOf(channel),
            messageId,
            MessageEditRequestPartial(
                content = mentionsOf(post) ?: "",
                embeds = listOf(post.embed.asRichEmbed()),
                allowedMentions = MessageAllowedMentionsRequest(parse = emptySet()),
            ),
        )
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

    private fun create(listing: DiscordEventListing): String =
        api
            .createGuildScheduledEvent(
                guildId,
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
            ).id

    private fun update(
        discordEventId: String,
        listing: DiscordEventListing,
    ) {
        api.updateGuildScheduledEvent(
            guildId,
            discordEventId,
            UpdateGuildScheduledEventRequest(
                entityMetadata = mapOf("location" to listing.location),
                name = listing.name,
                scheduledStartTime = listing.start.utc(),
                scheduledEndTime = listing.end.utc(),
                description = listing.description,
                image = listing.cover,
            ),
        )
    }

    private fun channelIdOf(channel: String): String =
        doors.ifAvailable
            ?.textRooms()
            ?.firstOrNull { plain(it.name) == plain(channel) }
            ?.id
            ?: error("No Discord text channel is called $channel")

    private companion object {
        /* The only privacy level Discord offers for a server's events. */
        const val GUILD_ONLY = 2
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
        image = imageUrl?.let { RichEmbedImage(url = URI(it)) },
    )

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
