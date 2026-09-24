package net.blueshell.api.game.persistence

import jakarta.persistence.Column
import jakarta.persistence.Embeddable

/** A Discord channel a game is talked about in, with the server it is in and its name as last known. */
@Embeddable
data class GameChannel(
    @Column(name = "channel_id", nullable = false, length = 32)
    val channelId: String = "",
    @Column(name = "guild_id", nullable = false, length = 32)
    val guildId: String = "",
    @Column(name = "channel_name", nullable = false, length = 100)
    val channelName: String = "",
)
