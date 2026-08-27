package net.blueshell.api.domain.esports.application

import net.blueshell.api.domain.esports.application.exception.RosterEntryNotFoundException
import net.blueshell.api.domain.esports.persistence.TeamRosterEntry
import net.blueshell.api.domain.esports.persistence.repository.TeamRosterEntryRepository
import net.blueshell.api.shared.enums.Game
import net.blueshell.api.shared.enums.TeamRole
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

@Service
class TeamRosterService(
    private val entries: TeamRosterEntryRepository,
    private val teams: TeamService,
    private val seasons: SeasonService,
    private val fielded: TeamSeasonService,
) {
    @Transactional(readOnly = true)
    fun findByTeamAndSeason(teamId: Long, seasonId: Long): List<TeamRosterEntry> =
        entries.findAllByTeamAndSeason(teamId, seasonId)

    @Transactional(readOnly = true)
    fun findByGameAndSeason(game: Game, seasonId: Long): List<TeamRosterEntry> =
        entries.findAllByGameAndSeason(game, seasonId)

    @Transactional(readOnly = true)
    fun findSeasonIdsWithRosters(game: Game): List<Long> = entries.findSeasonIdsWithRosters(game)

    /**
     * Whether a member held a roster spot in a season overlapping the window.
     *
     * This is the whole of what the association means by "active through play" for a stretch
     * of time, so it is a question asked of the roster rather than of the seasons.
     */
    @Transactional(readOnly = true)
    fun playedBetween(userId: Long, from: LocalDate, to: LocalDate): Boolean =
        entries.existsForUserInWindow(userId, from, to)

    @Transactional(readOnly = true)
    fun playersBetween(from: LocalDate, to: LocalDate): Set<Long> =
        entries.findUserIdsInWindow(from, to).toSet()

    @Transactional
    fun add(
        teamId: Long,
        seasonId: Long,
        handle: String,
        role: TeamRole,
        userId: Long?,
        displayName: String?,
    ): TeamRosterEntry {
        val team = teams.findById(teamId)
        val season = seasons.findById(seasonId)
        val trimmed = handle.trim()
        require(trimmed.isNotBlank()) { "A roster entry needs a handle" }
        // Naming somebody to a team in a season says the team is fielded there, whether or
        // not anybody said so first.
        fielded.field(teamId, seasonId)
        // Appended rather than inserted: the page lists a roster in the order it was written.
        val next = entries.findAllByTeamAndSeason(teamId, seasonId).size
        return entries.save(
            TeamRosterEntry(
                team = team,
                season = season,
                handle = trimmed,
                teamRole = role,
                userId = userId,
                displayName = displayName?.trim()?.ifBlank { null },
                sortIndex = next,
            ),
        )
    }

    @Transactional
    fun update(
        id: Long,
        handle: String,
        role: TeamRole,
        displayName: String?,
        sortIndex: Int,
    ): TeamRosterEntry {
        val entry = findById(id)
        val trimmed = handle.trim()
        require(trimmed.isNotBlank()) { "A roster entry needs a handle" }
        entry.handle = trimmed
        entry.teamRole = role
        entry.displayName = displayName?.trim()?.ifBlank { null }
        entry.sortIndex = sortIndex
        return entries.save(entry)
    }

    /** Linking is separate from editing: it says who somebody is, not what they were called. */
    @Transactional
    fun link(id: Long, userId: Long?): TeamRosterEntry {
        val entry = findById(id)
        entry.userId = userId
        return entries.save(entry)
    }

    @Transactional
    fun remove(id: Long) = entries.delete(findById(id))

    private fun findById(id: Long): TeamRosterEntry =
        entries.findById(id).orElseThrow { RosterEntryNotFoundException(id) }
}
