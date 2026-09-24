package net.blueshell.api.discord.domain

import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

/**
 * The channels a game may live in: the text channels filed under the server's games category,
 * in the server's order. Null without a bot, as every Discord read is.
 */
@Service
class DiscordGameChannels(
    private val source: ObjectProvider<DoorSource>,
    @Value($$"${discord.games-category:Games}") private val category: String,
) {
    fun offered(): List<TextRoom>? =
        source.ifAvailable
            ?.textRooms()
            ?.filter { it.category.equals(category, ignoreCase = true) }
}
