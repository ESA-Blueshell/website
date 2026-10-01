package net.blueshell.api.esports.api

import net.blueshell.api.esports.domain.RosterEntryNotFoundException
import net.blueshell.api.esports.domain.SeasonGameService
import net.blueshell.api.esports.domain.SeasonService
import net.blueshell.api.esports.domain.TeamInput
import net.blueshell.api.esports.domain.TeamSeasonService
import net.blueshell.api.esports.domain.TeamService
import net.blueshell.api.esports.persistence.Season
import net.blueshell.api.esports.persistence.Team
import net.blueshell.api.esports.persistence.TeamRosterEntry
import net.blueshell.api.esports.persistence.TeamRosterEntryRepository
import net.blueshell.api.esports.persistence.TeamSeason
import net.blueshell.api.file.api.StoredPictures
import net.blueshell.api.shared.enums.FileType
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

/** One person on a line-up draft, as the editor writes it: an entry to keep, or somebody new. */
data class DraftEntry(
    /** The entry this stands for, or nothing where it is somebody being added. */
    val id: Long?,
    val entry: RosterEntryInput,
    val userId: Long?,
)

/** A team's whole line-up for a game and a season, as one Save in the editor sends it. */
data class LineupDraft(
    /** Nothing where the team does not exist yet. */
    val teamId: Long?,
    val name: String,
    val teamIcon: String?,
    val game: String,
    val seasonId: Long,
    /** The art of this season's fielding, which is why it is not the team's. */
    val banner: String?,
    /** Entries taken off. */
    val removed: List<Long>,
    /** Everybody on the line-up, in the order they are shown. */
    val entries: List<DraftEntry>,
)

/** A published line-up: the team as it now stands and its roster in order. */
data class PublishedLineup(
    val team: Team,
    val roster: List<TeamRosterEntry>,
)

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
    private val events: ApplicationEventPublisher,
) {
    /** A team's name by its id, for every team the association has. */
    @Transactional(readOnly = true)
    fun teamNames(): Map<Long, String> = teams.pool().associate { requireNotNull(it.id) to it.name }

    /**
     * The people on [teamId]'s line-up in the season fielded now. A season ending takes nobody off
     * anything; they simply stop being here.
     */
    @Transactional(readOnly = true)
    fun currentPlayersOf(teamId: Long): Set<Long> {
        if (teams.findById(teamId).archived) return emptySet()
        val seasonId = fielded.fieldedSeasonNow() ?: return emptySet()
        return fielded
            .seasonsOf(teamId)
            .filter { it.season.id == seasonId }
            .flatMap { entries.findAllByTeamAndSeason(teamId, it.game, seasonId) }
            .mapNotNullTo(mutableSetOf()) { it.userId }
    }

    @Transactional(readOnly = true)
    fun findByTeamAndSeason(
        teamId: Long,
        game: String,
        seasonId: Long,
    ): List<TeamRosterEntry> = entries.findAllByTeamAndSeason(teamId, game, seasonId)

    @Transactional(readOnly = true)
    fun findByGameAndSeason(
        game: String,
        seasonId: Long,
    ): List<TeamRosterEntry> = entries.findAllByGameAndSeason(game, seasonId)

    @Transactional(readOnly = true)
    fun playedBy(userId: Long): List<TeamRosterEntry> = entries.findAllByUserId(userId)

    @Transactional(readOnly = true)
    fun findSeasonIdsWithRosters(game: String): List<Long> = entries.findSeasonIdsWithRosters(game)

    /**
     * Whether a member held a roster spot in a season overlapping the window.
     *
     * This is the whole of what the association means by "active through play" for a stretch
     * of time, so it is a question asked of the roster rather than of the seasons.
     */
    @Transactional(readOnly = true)
    fun playedBetween(
        userId: Long,
        from: LocalDate,
        to: LocalDate,
    ): Boolean = entries.existsForUserInWindow(userId, from, to)

    @Transactional(readOnly = true)
    fun playersBetween(
        from: LocalDate,
        to: LocalDate,
    ): Set<Long> = entries.findUserIdsInWindow(from, to).toSet()

    @Transactional
    fun add(
        teamId: Long,
        game: String,
        seasonId: Long,
        input: RosterEntryInput,
        userId: Long?,
    ): TeamRosterEntry {
        val trimmed = input.handle.trim()
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
        userId?.let { events.publishEvent(RosterChanged(teamId, setOf(it))) }
        return entries.save(
            TeamRosterEntry(
                teamSeason = fielding,
                handle = trimmed,
                teamRole = input.role,
                userId = userId,
                displayName = input.displayName?.trim()?.ifBlank { null },
                roleTitle = input.roleTitle?.trim()?.ifBlank { null },
                description = input.description?.trim()?.ifBlank { null },
                sortIndex = next,
                icon = pictures.of(input.icon, FileType.ROSTER_ICON),
            ),
        )
    }

    /** One line-up of a team's: which game it was played in, and which season. */
    data class LineupSource(
        val game: String,
        val seasonId: Long,
    )

    /**
     * Fields a team in a season and, when asked, copies across the line-up it last had. Carrying
     * is never silent — the caller asks, and the answer says what came across — and a season
     * that already holds a line-up keeps it, since carrying in would duplicate the roster or
     * overwrite a deliberate edit.
     */
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
        val from =
            carryFrom
                ?: entries
                    .findSeasonIdsWithLineup(teamId, game, seasonId)
                    .firstOrNull()
                    ?.let { LineupSource(game, it) }
                ?: return FieldedTeam(fielding, team, season, emptyList())
        val carried =
            entries.findAllByTeamAndSeason(teamId, from.game, from.seasonId).map { previous ->
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
        events.publishEvent(RosterChanged(teamId, carried.mapNotNullTo(mutableSetOf()) { it.userId }))
        return FieldedTeam(fielding, team, season, carried)
    }

    /**
     * Writes a line-up draft as one transaction: the team, its fielding, the removals and every
     * entry in the order given. A refusal anywhere leaves the line-up as it was.
     */
    @Transactional
    fun publish(draft: LineupDraft): PublishedLineup {
        val input = TeamInput(draft.name, draft.teamIcon)
        val team = draft.teamId?.let { teams.update(it, input) } ?: teams.create(input)
        val teamId = team.id!!
        fieldWithLineup(teamId, draft.game, draft.seasonId, carryLineup = false, banner = draft.banner)
        draft.removed.forEach(::remove)
        val roster =
            draft.entries.mapIndexed { sortIndex, drafted ->
                if (drafted.id == null) {
                    add(teamId, draft.game, draft.seasonId, drafted.entry, drafted.userId).also { it.sortIndex = sortIndex }
                } else {
                    update(drafted.id, drafted.entry, sortIndex).also { entry ->
                        if (entry.userId != drafted.userId) {
                            events.publishEvent(RosterChanged(teamId, setOfNotNull(entry.userId, drafted.userId)))
                        }
                        entry.userId = drafted.userId
                    }
                }
            }
        return PublishedLineup(team, roster)
    }

    @Transactional
    fun update(
        id: Long,
        input: RosterEntryInput,
        sortIndex: Int,
    ): TeamRosterEntry {
        val entry = findById(id)
        val trimmed = input.handle.trim()
        require(trimmed.isNotBlank()) { "A roster entry needs a handle" }
        entry.handle = trimmed
        entry.teamRole = input.role
        entry.displayName = input.displayName?.trim()?.ifBlank { null }
        entry.roleTitle = input.roleTitle?.trim()?.ifBlank { null }
        entry.description = input.description?.trim()?.ifBlank { null }
        entry.sortIndex = sortIndex
        // Part of the save rather than applied when it was chosen, so cancelling the line-up
        // leaves the person as they were. Naming no picture takes theirs away.
        entry.icon = pictures.of(input.icon, FileType.ROSTER_ICON)
        return entries.save(entry)
    }

    /** Linking is separate from editing: it says who somebody is, not what they were called. */
    @Transactional
    fun link(
        id: Long,
        userId: Long?,
    ): TeamRosterEntry {
        val entry = findById(id)
        events.publishEvent(RosterChanged(requireNotNull(entry.teamSeason.team.id), setOfNotNull(entry.userId, userId)))
        entry.userId = userId
        return entries.save(entry)
    }

    @Transactional
    fun remove(id: Long) {
        val entry = findById(id)
        entries.delete(entry)
        entry.userId?.let { events.publishEvent(RosterChanged(requireNotNull(entry.teamSeason.team.id), setOf(it))) }
    }

    private fun findById(id: Long): TeamRosterEntry = entries.findById(id).orElseThrow { RosterEntryNotFoundException(id) }
}
