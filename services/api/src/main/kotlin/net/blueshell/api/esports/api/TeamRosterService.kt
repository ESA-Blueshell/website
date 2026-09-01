package net.blueshell.api.esports.api

import net.blueshell.api.esports.domain.RosterEntryNotFoundException
import net.blueshell.api.esports.persistence.Season
import net.blueshell.api.esports.persistence.Team
import net.blueshell.api.esports.persistence.TeamRosterEntry
import net.blueshell.api.esports.persistence.TeamRosterEntryRepository
import net.blueshell.api.esports.persistence.TeamSeason
import net.blueshell.api.file.api.StoredPictures
import net.blueshell.api.shared.enums.FileType
import net.blueshell.api.shared.enums.TeamRole
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import net.blueshell.api.esports.domain.SeasonService
import net.blueshell.api.esports.domain.SeasonGameService
import net.blueshell.api.esports.domain.TeamSeasonService
import net.blueshell.api.esports.domain.TeamService

/** A team fielded in a game in a season, with whatever line-up came across with it. */
data class FieldedTeam(
    val fielding: TeamSeason,
    val team: Team,
    val season: Season,
    val carried: List<TeamRosterEntry>,
)

@Service
class TeamRosterService(
    private val entries: TeamRosterEntryRepository,
    private val teams: TeamService,
    private val seasons: SeasonService,
    private val fielded: TeamSeasonService,
    private val entered: SeasonGameService,
    private val pictures: StoredPictures,
) {
    @Transactional(readOnly = true)
    fun findByTeamAndSeason(teamId: Long, game: String, seasonId: Long): List<TeamRosterEntry> =
        entries.findAllByTeamAndSeason(teamId, game, seasonId)

    @Transactional(readOnly = true)
    fun findByGameAndSeason(game: String, seasonId: Long): List<TeamRosterEntry> =
        entries.findAllByGameAndSeason(game, seasonId)

    @Transactional(readOnly = true)
    fun findSeasonIdsWithRosters(game: String): List<Long> = entries.findSeasonIdsWithRosters(game)

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
        game: String,
        seasonId: Long,
        handle: String,
        role: TeamRole,
        userId: Long?,
        displayName: String?,
        roleTitle: String? = null,
        description: String? = null,
        icon: String? = null,
    ): TeamRosterEntry {
        val trimmed = handle.trim()
        require(trimmed.isNotBlank()) { "A roster entry needs a handle" }
        // Naming somebody to a team in a season says the team is fielded there, whether or
        // not anybody said so first. The entry hangs off that fielding, so this is what it is
        // written against rather than something done alongside it.
        // Fielding a team in a game says that game ran that season, whether or not anybody
        // entered it first — and it stays entered when the last team is dropped again.
        entered.enter(seasonId, game)
        val fielding = fielded.field(teamId, game, seasonId)
        // Appended rather than inserted: a roster is read in the order it was written.
        val next = entries.findAllByTeamAndSeason(teamId, game, seasonId).size
        return entries.save(
            TeamRosterEntry(
                teamSeason = fielding,
                handle = trimmed,
                teamRole = role,
                userId = userId,
                displayName = displayName?.trim()?.ifBlank { null },
                roleTitle = roleTitle?.trim()?.ifBlank { null },
                description = description?.trim()?.ifBlank { null },
                sortIndex = next,
                icon = pictures.of(icon, FileType.ROSTER_ICON),
            ),
        )
    }

    /**
     * Fields a team in a season and, when asked, copies across the line-up it last had.
     *
     * A third of the recovered history is a roster that carried over unchanged, so copying is
     * the ordinary case and typing five handles again is the exception. Carrying is never
     * silent: the caller asks for it, and the answer says what came across. A season that
     * already holds a line-up for the team keeps it, since carrying into it would either
     * duplicate the roster or overwrite an edit somebody made on purpose.
     */
    /** One line-up of a team's: which game it was played in, and which season. */
    data class LineupSource(val game: String, val seasonId: Long)

    @Transactional
    @Suppress("LongParameterList")
    fun fieldWithLineup(
        teamId: Long,
        game: String,
        seasonId: Long,
        carryLineup: Boolean,
        banner: String? = null,
        carryFrom: LineupSource? = null,
    ): FieldedTeam {
        val team = teams.findById(teamId)
        val season = seasons.findById(seasonId)
        entered.enter(seasonId, game)
        val fielding = fielded.field(teamId, game, seasonId)
        if (banner != null) fielded.draw(fielding, banner)
        val asked = carryFrom != null || carryLineup
        if (!asked || entries.findAllByTeamAndSeason(teamId, game, seasonId).isNotEmpty()) {
            return FieldedTeam(fielding, team, season, emptyList())
        }
        // A named line-up is the one meant. Unnamed, it is the one this team last had in this
        // game -- a team that also plays another has a line-up there too, and it is not this one.
        val from = carryFrom
            ?: entries.findSeasonIdsWithLineup(teamId, game, seasonId).firstOrNull()
                ?.let { LineupSource(game, it) }
            ?: return FieldedTeam(fielding, team, season, emptyList())
        val carried = entries.findAllByTeamAndSeason(teamId, from.game, from.seasonId).map { previous ->
            entries.save(
                TeamRosterEntry(
                    teamSeason = fielding,
                    handle = previous.handle,
                    teamRole = previous.teamRole,
                    userId = previous.userId,
                    displayName = previous.displayName,
                    sortIndex = previous.sortIndex,
                    icon = previous.icon,
                ),
            )
        }
        return FieldedTeam(fielding, team, season, carried)
    }

    @Transactional
    fun update(
        id: Long,
        handle: String,
        role: TeamRole,
        displayName: String?,
        sortIndex: Int,
        roleTitle: String? = null,
        description: String? = null,
        icon: String? = null,
    ): TeamRosterEntry {
        val entry = findById(id)
        val trimmed = handle.trim()
        require(trimmed.isNotBlank()) { "A roster entry needs a handle" }
        entry.handle = trimmed
        entry.teamRole = role
        entry.displayName = displayName?.trim()?.ifBlank { null }
        entry.roleTitle = roleTitle?.trim()?.ifBlank { null }
        entry.description = description?.trim()?.ifBlank { null }
        entry.sortIndex = sortIndex
        // Part of the save rather than applied when it was chosen, so cancelling the line-up
        // leaves the person as they were. Naming no picture takes theirs away.
        entry.icon = pictures.of(icon, FileType.ROSTER_ICON)
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
