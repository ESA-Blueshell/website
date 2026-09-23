package net.blueshell.api.discord.domain

import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

/** The server as the Discord band draws it: its rooms and who is in them, and the counts. */
data class DiscordLive(
    val server: String,
    val online: Int?,
    val members: Int?,
    val rooms: List<LiveRoom>,
)

/** A voice room with the address that opens it in Discord. */
data class LiveRoom(
    val room: VoiceRoom,
    val href: String,
)

/**
 * Puts the gateway's server and the REST counts together. The gateway's counts win where it keeps
 * them; REST is only asked for what it does not. Both sources are optional beans: without a bot
 * token neither exists, and until the gateway has the server there is nothing to say. Either way
 * the answer is null, and the band falls back to Discord's public widget.
 */
@Service
class DiscordLiveService(
    private val voice: ObjectProvider<VoiceServerSource>,
    private val counts: ObjectProvider<GuildCountsSource>,
    @Value($$"${discord.baseUrl:https://discord.com/api/v10}") baseUrl: String,
) {
    /* The app's own address for a channel, which is not the api's: https://discord.com/channels. */
    private val appUrl = baseUrl.substringBefore("/api")

    fun live(): DiscordLive? {
        val server = voice.ifAvailable?.server() ?: return null
        val counted = if (server.online != null && server.members != null) null else counts.ifAvailable?.counts()
        return DiscordLive(
            server = server.name,
            online = server.online ?: counted?.online,
            members = server.members ?: counted?.members,
            rooms =
                server.rooms
                    .sortedBy { it.position }
                    .map { LiveRoom(it, "$appUrl/channels/${server.id}/${it.id}") },
        )
    }
}
