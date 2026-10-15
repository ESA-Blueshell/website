package net.blueshell.api.discord.domain

import net.blueshell.api.discord.api.DiscordChannelKeeper
import net.blueshell.api.discord.api.DiscordUnavailable
import net.blueshell.api.discord.api.KeptChannelKind
import net.blueshell.api.game.api.GameChannelKind
import net.blueshell.api.game.api.GameService
import org.springframework.stereotype.Service

/** The game a channel is kept for, and which of its channel lists holds it. */
data class ChannelGame(
    val code: String,
    val name: String,
    val kind: GameChannelKind,
)

/** A channel or category as the Discord page lists it. */
data class CataloguedChannel(
    val id: String,
    val name: String,
    val kind: KeptChannelKind,
    val category: String?,
    /** Whether @everyone is kept out of it. */
    val private: Boolean,
    /** The roles let in by an overwrite of their own. */
    val roleIds: List<String>,
    /** The name of each of [roleIds], any role the server has and not only those the site keeps. */
    val roleNames: Map<String, String>,
    val game: ChannelGame?,
)

/** Every channel in the server with who it is opened to and the game it belongs to; none without a bot. */
@Service
class DiscordCatalogue(
    private val channels: DiscordChannelKeeper,
    private val games: GameService,
) {
    fun channels(): List<CataloguedChannel> {
        if (!channels.available()) return emptyList()
        // The bot can leave between the check and the read; that reads as no channels too.
        val openings =
            try {
                channels.openings()
            } catch (_: DiscordUnavailable) {
                return emptyList()
            }
        val gameOf =
            games
                .findAll()
                .flatMap { game ->
                    game.channels.map { it.channelId to ChannelGame(game.code, game.name, GameChannelKind.CASUAL) } +
                        game.esportsChannels.map { it.channelId to ChannelGame(game.code, game.name, GameChannelKind.COMPETITION) }
                }.toMap()
        return openings.map { opening ->
            val channel = opening.channel
            CataloguedChannel(
                id = channel.id,
                name = channel.name,
                kind = channel.kind,
                category = channel.category,
                private = opening.private,
                roleIds = opening.roleIds,
                roleNames = opening.roleNames,
                game = gameOf[channel.id],
            )
        }
    }
}
