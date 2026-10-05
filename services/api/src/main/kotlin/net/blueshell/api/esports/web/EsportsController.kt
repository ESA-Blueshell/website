package net.blueshell.api.esports.web

import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.annotation.security.PermitAll
import jakarta.validation.Valid
import net.blueshell.api.esports.api.TeamRosterService
import net.blueshell.api.esports.domain.EsportsQueryService
import net.blueshell.api.esports.domain.SeasonGameService
import net.blueshell.api.esports.domain.SeasonService
import net.blueshell.api.esports.domain.TeamSeasonService
import net.blueshell.api.esports.domain.TeamService
import net.blueshell.api.file.api.asImage
import net.blueshell.api.security.BoardOnly
import net.blueshell.api.security.SecurityUtils
import net.blueshell.api.shared.enums.Role
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/**
 * The esports read, and the admin surface behind it.
 *
 * Reading is public — this is what anybody may see — and every write is the board's.
 * The public read returns handles only: a roster's real names are held for identification,
 * and publishing one is the member's own decision.
 */
@RestController
@RequestMapping("/esports")
@Tag(name = "Esports", description = "Teams, seasons and rosters")
// Seasons, teams and rosters are three resources behind one prefix. Splitting
// the controller is a routing change, not a lint fix.
@Suppress("TooManyFunctions")
class EsportsController(
    private val views: EsportsQueryService,
    private val seasons: SeasonService,
    private val teams: TeamService,
    private val rosters: TeamRosterService,
    private val fielded: TeamSeasonService,
    private val entered: SeasonGameService,
) {
    /**
     * Whether the caller may edit, which decides what a season's games answer with.
     *
     * The same authority the write routes are guarded by. Read rather than declared, because
     * this route answers everybody and answers them differently.
     */
    private fun mayEditEsports(): Boolean = SecurityUtils.hasAuthority(Role.BOARD)

    @GetMapping("/games/{game}")
    @PermitAll
    fun findGame(
        @PathVariable game: String,
        @RequestParam(required = false) seasonId: Long?,
    ): GameRostersResponse = views.rostersOf(game, seasonId).asResponse()

    /**
     * Every game that ran in one season, with what it fielded.
     *
     * One read for the season rather than one per game. A game entered with nobody fielded in it
     * is answered only to somebody who may edit, marked as not public — the rule turns on who
     * is asking, so it is applied here rather than in the frontend.
     */
    @PermitAll
    @GetMapping("/seasons/{seasonId}/games")
    fun findSeasonGames(
        @PathVariable seasonId: Long,
    ): List<SeasonGameResponse> = views.gamesOf(seasonId, mayEditEsports()).map { it.asResponse() }

    /** Records that a game runs in a season, before anybody has been fielded in it. */
    @BoardOnly
    @PutMapping("/seasons/{seasonId}/games/{game}")
    fun enterGame(
        @PathVariable seasonId: Long,
        @PathVariable game: String,
    ): SeasonGameResponse {
        entered.enter(seasonId, game)
        return views.gamesOf(seasonId, mayEdit = true).first { it.game == game }.asResponse()
    }

    /** Takes a game out of a season, which is only possible while it holds no teams. */
    @BoardOnly
    @DeleteMapping("/seasons/{seasonId}/games/{game}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun leaveGame(
        @PathVariable seasonId: Long,
        @PathVariable game: String,
    ) {
        entered.leave(seasonId, game)
    }

    @GetMapping("/seasons")
    @PermitAll
    fun findSeasons(): List<SeasonResponse> {
        // Which seasons had something fielded is read once for the whole list: a visitor's
        // list carries those, and the board's carries every season, because a season has to
        // be reachable before a game can be entered in it.
        val played = fielded.seasonsWithTeams()
        return seasons.findAll().map { it.asResponse(played.contains(it.id)) }
    }

    @BoardOnly
    @PostMapping("/seasons")
    @ResponseStatus(HttpStatus.CREATED)
    fun createSeason(
        @Valid @RequestBody request: SeasonRequest,
    ): SeasonResponse = seasons.create(request.asInput()).asResponse()

    @BoardOnly
    @PutMapping("/seasons/{id}")
    fun updateSeason(
        @PathVariable id: Long,
        @Valid @RequestBody request: SeasonRequest,
    ): SeasonResponse = seasons.update(id, request.asInput()).asResponse()

    /** What a season holds, so the offer to remove it can say what goes with it. */
    @BoardOnly
    @GetMapping("/seasons/{id}/contents")
    fun findSeasonContents(
        @PathVariable id: Long,
    ): SeasonContentsResponse {
        val (teams, players) = fielded.contentsOf(id)
        return SeasonContentsResponse(teams = teams.toInt(), players = players.toInt())
    }

    @BoardOnly
    @DeleteMapping("/seasons/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteSeason(
        @PathVariable id: Long,
    ) {
        seasons.delete(id)
    }

    /**
     * Every team the association has.
     *
     * Not scoped by game: the pool is shared, so a team that has only ever played one game is
     * still one the board can field in another.
     */
    @GetMapping("/teams")
    @PermitAll
    fun findTeams(): List<TeamResponse> = teams.pool().map { it.asResponse() }

    @BoardOnly
    @PostMapping("/teams")
    @ResponseStatus(HttpStatus.CREATED)
    fun createTeam(
        @Valid @RequestBody request: TeamRequest,
    ): TeamResponse = teams.create(request.asInput()).asResponse()

    @BoardOnly
    @PutMapping("/teams/{id}")
    fun updateTeam(
        @PathVariable id: Long,
        @Valid @RequestBody request: TeamRequest,
    ): TeamResponse = teams.update(id, request.asInput()).asResponse()

    /** A team stopped playing, or plays again: its Discord role empties or refills, and its channel moves. */
    @BoardOnly
    @PutMapping("/teams/{id}/archived")
    fun archiveTeam(
        @PathVariable id: Long,
        @RequestBody request: ArchiveTeamRequest,
    ): TeamResponse = teams.archive(id, request.archived).asResponse()

    @BoardOnly
    @DeleteMapping("/teams/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteTeam(
        @PathVariable id: Long,
    ) {
        teams.delete(id)
    }

    /**
     * Records that a team is fielded in a season, before anybody has been named to it, and
     * optionally brings the line-up it last had across with it.
     *
     * Saying it twice says the same thing, so a repeat answers with the team rather than
     * refusing: an interface that has to check first would race itself.
     */
    @BoardOnly
    @PutMapping("/seasons/{seasonId}/teams/{teamId}")
    fun fieldTeam(
        @PathVariable seasonId: Long,
        @PathVariable teamId: Long,
        @Valid @RequestBody request: FieldTeamRequest,
    ): FieldedTeamResponse {
        val fieldedTeam =
            rosters.fieldWithLineup(
                teamId = teamId,
                game = request.game,
                seasonId = seasonId,
                carryLineup = request.carryLineup,
                banner = request.banner,
                carryFrom = request.carryFrom?.let { TeamRosterService.LineupSource(it.game, it.seasonId) },
            )
        return FieldedTeamResponse(
            team = fieldedTeam.team.asResponse(),
            game = fieldedTeam.fielding.game,
            season = fieldedTeam.season.asResponse(),
            banner = fieldedTeam.fielding.banner?.asImage(),
            carried = fieldedTeam.carried.map { it.asResponse() },
        )
    }

    /**
     * Saves a team's whole line-up for a game and a season in one transaction: the team, this
     * season's banner, the entries taken off and everybody on it in order. A refusal part-way
     * leaves the line-up as it was.
     */
    @BoardOnly
    @PutMapping("/seasons/{seasonId}/lineup")
    fun publishLineup(
        @PathVariable seasonId: Long,
        @Valid @RequestBody request: PublishLineupRequest,
    ): PublishedLineupResponse {
        val published = rosters.publish(request.asDraft(seasonId))
        return PublishedLineupResponse(published.team.asResponse(), published.roster.map { it.asResponse() })
    }

    /** Stops a team being fielded in a season. The team, and its other seasons, are untouched. */
    @BoardOnly
    @DeleteMapping("/seasons/{seasonId}/teams/{teamId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun unfieldTeam(
        @PathVariable seasonId: Long,
        @PathVariable teamId: Long,
        @RequestParam game: String,
    ) {
        fielded.unfield(teamId, game, seasonId)
    }

    /**
     * The line-ups a team has, newest first: which game, which season. Only the team and
     * line-up editors read it, so it needs a login.
     *
     * Each is a fielding rather than a season, because a team that played two games in one
     * season has two of them, with a line-up in each.
     */
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/teams/{teamId}/seasons")
    fun findTeamSeasons(
        @PathVariable teamId: Long,
    ): List<FieldingResponse> = fielded.seasonsOf(teamId).map { FieldingResponse(game = it.game, season = it.season.asResponse()) }

    /**
     * Every team's fieldings in one answer, newest first, for a page that lists every team. Asking
     * team by team is a request per team, and a list that draws before they all answer shows
     * every team as never fielded.
     */
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/fieldings")
    fun findFieldings(): List<TeamFieldingResponse> =
        fielded.everyFielding().map { TeamFieldingResponse(teamId = it.team.id!!, game = it.game, season = it.season.asResponse()) }

    /** The admin view of a roster: the same rows the public read has, with the names attached. */
    @BoardOnly
    @GetMapping("/teams/{teamId}/roster")
    fun findRoster(
        @PathVariable teamId: Long,
        @RequestParam game: String,
        @RequestParam seasonId: Long,
    ): List<RosterEntryResponse> = rosters.findByTeamAndSeason(teamId, game, seasonId).map { it.asResponse() }

    @BoardOnly
    @PostMapping("/teams/{teamId}/roster")
    @ResponseStatus(HttpStatus.CREATED)
    fun addRosterEntry(
        @PathVariable teamId: Long,
        @Valid @RequestBody request: AddRosterEntryRequest,
    ): RosterEntryResponse = rosters.add(teamId, request.game, request.seasonId, request.asInput(), request.userId).asResponse()

    @BoardOnly
    @PutMapping("/roster/{id}")
    fun updateRosterEntry(
        @PathVariable id: Long,
        @Valid @RequestBody request: UpdateRosterEntryRequest,
    ): RosterEntryResponse = rosters.update(id, request.asInput(), request.sortIndex).asResponse()

    /** A null user unlinks: an entry nobody can be attributed to is a roster spot all the same. */
    @BoardOnly
    @PutMapping("/roster/{id}/member")
    fun linkRosterEntry(
        @PathVariable id: Long,
        @RequestBody request: LinkRosterEntryRequest,
    ): RosterEntryResponse = rosters.link(id, request.userId).asResponse()

    @BoardOnly
    @DeleteMapping("/roster/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun removeRosterEntry(
        @PathVariable id: Long,
    ) {
        rosters.remove(id)
    }
}
