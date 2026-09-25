package net.blueshell.api.discord.domain

import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

/**
 * The channels a game may live in: the text channels filed under the server's games category, or
 * under its esports category for the game's competition, in the server's order. Null without a
 * bot, as every Discord read is.
 */
@Service
class DiscordGameChannels(
    private val source: ObjectProvider<DoorSource>,
    @Value($$"${discord.games-category:Games}") private val gamesCategory: String,
    @Value($$"${discord.esports-category:Esports}") private val esportsCategory: String,
) {
    fun offered(category: GameChannelCategory): List<TextRoom>? {
        val named = if (category == GameChannelCategory.ESPORTS) esportsCategory else gamesCategory
        return source.ifAvailable
            ?.textRooms()
            ?.filter { it.category.equals(named, ignoreCase = true) }
    }
}
