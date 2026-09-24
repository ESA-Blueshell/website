package net.blueshell.api.esports.domain

import net.blueshell.api.esports.persistence.TeamRosterEntryRepository
import net.blueshell.api.esports.persistence.TeamSeasonRepository
import net.blueshell.api.game.api.GameHoldings
import net.blueshell.api.game.api.GameService
import net.blueshell.api.game.api.GamesInCompetition
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * What competition holds against a game: the teams fielded in it and the people on their
 * line-ups. A game holding either keeps its history and cannot be removed. Also which games are
 * in competition now, which only this module can say.
 */
@Component
class FieldedGames(
    private val games: GameService,
    private val fielded: TeamSeasonRepository,
    private val entries: TeamRosterEntryRepository,
    private val seasons: TeamSeasonService,
) : GameHoldings,
    GamesInCompetition {
    /** Teams recorded in a game and the people on them, read so a removal can say what it would take. */
    @Transactional(readOnly = true)
    fun contentsOf(game: String): Pair<Long, Long> {
        val code = games.requireGame(game).code
        return fielded.countTeamsByGame(code) to entries.countByGame(code)
    }

    override fun refuseRemoval(code: String) {
        val (held, players) = contentsOf(code)
        if (held > 0) throw GameHoldsHistory(games.requireGame(code).name, held, players)
    }

    override fun currentlyFielded(): Set<String> = seasons.currentlyPlayed()
}
