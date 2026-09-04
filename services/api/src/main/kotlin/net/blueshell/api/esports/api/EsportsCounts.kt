package net.blueshell.api.esports.api

import net.blueshell.api.esports.domain.TeamSeasonService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * How much esports the association is doing, for a reader outside this module.
 *
 * Published here because the numbers are derived rather than stored: which games count as
 * played now, and which season "now" means, are this module's rules, and a caller reading the
 * rows itself would have to restate them.
 */
@Service
class EsportsCounts(private val fielded: TeamSeasonService) {

    /** The games with a team standing in them, by the same rule the game list marks current. */
    @Transactional(readOnly = true)
    fun gamesCurrentlyPlayed(): Long = fielded.currentlyPlayed().size.toLong()

    /** The seasons that had a team fielded in them, whichever game it played. */
    @Transactional(readOnly = true)
    fun seasonsPlayed(): Long = fielded.seasonsWithTeams().size.toLong()

    /** The teams standing this season. */
    @Transactional(readOnly = true)
    fun teamsFieldedNow(): Long = fielded.teamsFieldedNow()
}
