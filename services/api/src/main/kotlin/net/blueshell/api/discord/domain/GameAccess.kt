package net.blueshell.api.discord.domain

import net.blueshell.api.game.api.GameService
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException

/** One of a game's channels and its access, kept and on Discord. */
data class GameChannelAccess(
    val id: String,
    val name: String,
    val state: ChannelAccessState,
)

/** The access a game's channels share, and each channel as Discord has it. */
data class GameAccessState(
    val policy: AccessPolicy,
    val channels: List<GameChannelAccess>,
)

/**
 * Who reads a game's channels and who writes in them, set once for every games and esports channel
 * of the game. Each channel is written as its own policy is, so a channel changed on Discord by
 * hand reads as a difference and is never set back on its own.
 */
@Service
class GameAccess(
    private val games: GameService,
    private val policies: GameChannelPolicies,
) {
    fun read(code: String): GameAccessState {
        // A channel deleted on Discord is left out rather than failing the game; without a bot it all refuses.
        val channels =
            channelsOf(code).mapNotNull { (id, name) ->
                val state =
                    try {
                        policies.read(id)
                    } catch (gone: ResponseStatusException) {
                        if (gone.statusCode != HttpStatus.NOT_FOUND) throw gone
                        log.info("[discord] game {} names channel {}, which Discord no longer has", code, id)
                        null
                    }
                state?.let { GameChannelAccess(id, name, it) }
            }
        // The game's policy is what its channels keep; one kept by none reads as the default.
        val policy = channels.firstNotNullOfOrNull { it.state.kept } ?: AccessPolicy.DEFAULT
        return GameAccessState(policy, channels)
    }

    fun set(
        code: String,
        policy: AccessPolicy,
    ): GameAccessState {
        read(code).channels.forEach { policies.set(it.id, policy) }
        return read(code)
    }

    private fun channelsOf(code: String): List<Pair<String, String>> {
        val game = games.findByCode(code)
        return (game.channels + game.esportsChannels).map { it.channelId to it.channelName }.distinctBy { it.first }
    }

    private companion object {
        val log = LoggerFactory.getLogger(GameAccess::class.java)
    }
}
