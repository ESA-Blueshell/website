package net.blueshell.api.esports.web

import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.annotation.security.PermitAll
import jakarta.validation.Valid
import net.blueshell.api.esports.domain.EsportsPageQueryService
import net.blueshell.api.esports.domain.GamePageService
import net.blueshell.api.esports.domain.SeasonService
import net.blueshell.api.esports.api.TeamRosterService
import net.blueshell.api.esports.domain.TeamSeasonService
import net.blueshell.api.esports.domain.TeamService
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
 * The esports pages and the admin surface behind them.
 *
 * Reading is public — these are the pages anybody can visit — and every write is the board's.
 * The public read returns handles only: a roster's real names are held for identification,
 * and publishing one is the member's own decision.
 */
@RestController
@RequestMapping("/esports")
@Tag(name = "Esports", description = "Teams, seasons and rosters")
class EsportsController(
    private val page: EsportsPageQueryService,
    private val seasons: SeasonService,
    private val teams: TeamService,
    private val gamePages: GamePageService,
    private val rosters: TeamRosterService,
    private val fielded: TeamSeasonService,
) {
    @GetMapping("/games/{game}")
    @PermitAll
    fun findEsportsPage(
        @PathVariable game: String,
        @RequestParam(required = false) seasonId: Long?,
    ): EsportsPageResponse = page.page(game, seasonId).asResponse()

    /**
     * Every game the association has fielded a team in, present or past, in the order they are
     * shown. Public: the pages are.
     */
    @PermitAll
    @GetMapping("/games")
    fun findGamePages(): List<GamePageResponse> = gamePages.findAll().map { it.asResponse() }

    /** A game the association has started playing. Its page answers straight away. */
    @PreAuthorize("hasPermission('__NO_TARGET__', 'Team', 'write')")
    @PostMapping("/games")
    @ResponseStatus(HttpStatus.CREATED)
    fun createGame(@Valid @RequestBody request: CreateGameRequest): GamePageResponse =
        gamePages.create(request.name, request.slug).asResponse()

    @PreAuthorize("hasPermission('__NO_TARGET__', 'Team', 'write')")
    @PutMapping("/games/{game}")
    fun updateGamePage(
        @PathVariable game: String,
        @Valid @RequestBody request: UpdateGamePageRequest,
    ): GamePageResponse =
        gamePages.update(
            game = game,
            name = request.name,
            slug = request.slug,
            intro = request.intro,
            accent = request.accent,
            mark = request.mark,
            banner = request.banner,
            sortIndex = request.sortIndex,
            fielded = request.fielded,
        ).asResponse()

    /** What a game holds, so the offer to remove it can say what goes with it. */
    @PreAuthorize("hasPermission('__NO_TARGET__', 'Team', 'write')")
    @GetMapping("/games/{game}/contents")
    fun findGameContents(@PathVariable game: String): GameContentsResponse {
        val (teams, players) = gamePages.contentsOf(game)
        return GameContentsResponse(teams = teams.toInt(), players = players.toInt())
    }

    @PreAuthorize("hasPermission('__NO_TARGET__', 'Team', 'delete')")
    @DeleteMapping("/games/{game}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteGame(@PathVariable game: String) {
        gamePages.delete(game)
    }

    @GetMapping("/seasons")
    @PermitAll
    fun findSeasons(): List<SeasonResponse> = seasons.findAll().map { it.asResponse() }

    @PreAuthorize("hasPermission('__NO_TARGET__', 'Team', 'write')")
    @PostMapping("/seasons")
    @ResponseStatus(HttpStatus.CREATED)
    fun createSeason(@Valid @RequestBody request: SeasonRequest): SeasonResponse =
        seasons.create(request.name, request.startDate, request.endDate).asResponse()

    @PreAuthorize("hasPermission('__NO_TARGET__', 'Team', 'write')")
    @PutMapping("/seasons/{id}")
    fun updateSeason(
        @PathVariable id: Long,
        @Valid @RequestBody request: SeasonRequest,
    ): SeasonResponse = seasons.update(id, request.name, request.startDate, request.endDate).asResponse()

    /** What a season holds, so the offer to remove it can say what goes with it. */
    @PreAuthorize("hasPermission('__NO_TARGET__', 'Team', 'write')")
    @GetMapping("/seasons/{id}/contents")
    fun findSeasonContents(@PathVariable id: Long): SeasonContentsResponse {
        val (teams, players) = fielded.contentsOf(id)
        return SeasonContentsResponse(teams = teams.toInt(), players = players.toInt())
    }

    @PreAuthorize("hasPermission('__NO_TARGET__', 'Team', 'delete')")
    @DeleteMapping("/seasons/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteSeason(@PathVariable id: Long) {
        seasons.delete(id)
    }

    @GetMapping("/teams")
    @PermitAll
    fun findTeams(@RequestParam game: String): List<TeamResponse> =
        teams.findAllByGame(game).map { it.asResponse() }

    @PreAuthorize("hasPermission('__NO_TARGET__', 'Team', 'write')")
    @PostMapping("/teams")
    @ResponseStatus(HttpStatus.CREATED)
    fun createTeam(@Valid @RequestBody request: CreateTeamRequest): TeamResponse =
        teams.create(request.game, request.name, request.image).asResponse()

    @PreAuthorize("hasPermission('__NO_TARGET__', 'Team', 'write')")
    @PutMapping("/teams/{id}")
    fun updateTeam(
        @PathVariable id: Long,
        @Valid @RequestBody request: UpdateTeamRequest,
    ): TeamResponse = teams.update(id, request.name, request.image).asResponse()

    @PreAuthorize("hasPermission('__NO_TARGET__', 'Team', 'delete')")
    @DeleteMapping("/teams/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteTeam(@PathVariable id: Long) {
        teams.delete(id)
    }

    /**
     * Records that a team is fielded in a season, before anybody has been named to it, and
     * optionally brings the line-up it last had across with it.
     *
     * Saying it twice says the same thing, so a repeat answers with the team rather than
     * refusing: an interface that has to check first would race itself.
     */
    @PreAuthorize("hasPermission('__NO_TARGET__', 'Team', 'write')")
    @PutMapping("/seasons/{seasonId}/teams/{teamId}")
    fun fieldTeam(
        @PathVariable seasonId: Long,
        @PathVariable teamId: Long,
        @RequestBody(required = false) request: FieldTeamRequest?,
    ): FieldedTeamResponse {
        val fieldedTeam = rosters.fieldWithLineup(teamId, seasonId, request?.carryLineup == true)
        return FieldedTeamResponse(
            team = fieldedTeam.team.asResponse(),
            season = fieldedTeam.season.asResponse(),
            carried = fieldedTeam.carried.map { it.asResponse() },
        )
    }

    /** Stops a team being fielded in a season. The team, and its other seasons, are untouched. */
    @PreAuthorize("hasPermission('__NO_TARGET__', 'Team', 'delete')")
    @DeleteMapping("/seasons/{seasonId}/teams/{teamId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun unfieldTeam(
        @PathVariable seasonId: Long,
        @PathVariable teamId: Long,
    ) {
        fielded.unfield(teamId, seasonId)
    }

    /** Which seasons a team was fielded in, newest first. Public, as the pages show it. */
    @PermitAll
    @GetMapping("/teams/{teamId}/seasons")
    fun findTeamSeasons(@PathVariable teamId: Long): List<SeasonResponse> =
        fielded.seasonsOf(teamId).map { it.season.asResponse() }

    /** The admin view of a roster: the same rows the page shows, with the names attached. */
    @PreAuthorize("hasPermission('__NO_TARGET__', 'Team', 'write')")
    @GetMapping("/teams/{teamId}/roster")
    fun findRoster(
        @PathVariable teamId: Long,
        @RequestParam seasonId: Long,
    ): List<RosterEntryResponse> =
        rosters.findByTeamAndSeason(teamId, seasonId).map { it.asResponse() }

    @PreAuthorize("hasPermission('__NO_TARGET__', 'Team', 'write')")
    @PostMapping("/teams/{teamId}/roster")
    @ResponseStatus(HttpStatus.CREATED)
    fun addRosterEntry(
        @PathVariable teamId: Long,
        @Valid @RequestBody request: AddRosterEntryRequest,
    ): RosterEntryResponse = rosters.add(
        teamId = teamId,
        seasonId = request.seasonId,
        handle = request.handle,
        role = request.role,
        userId = request.userId,
        displayName = request.displayName,
        roleTitle = request.roleTitle,
        description = request.description,
    ).asResponse()

    @PreAuthorize("hasPermission('__NO_TARGET__', 'Team', 'write')")
    @PutMapping("/roster/{id}")
    fun updateRosterEntry(
        @PathVariable id: Long,
        @Valid @RequestBody request: UpdateRosterEntryRequest,
    ): RosterEntryResponse =
        rosters.update(
            id = id,
            handle = request.handle,
            role = request.role,
            displayName = request.displayName,
            sortIndex = request.sortIndex,
            roleTitle = request.roleTitle,
            description = request.description,
        ).asResponse()

    /** A null user unlinks: an entry nobody can be attributed to is a roster spot all the same. */
    @PreAuthorize("hasPermission('__NO_TARGET__', 'Team', 'write')")
    @PutMapping("/roster/{id}/member")
    fun linkRosterEntry(
        @PathVariable id: Long,
        @RequestBody request: LinkRosterEntryRequest,
    ): RosterEntryResponse = rosters.link(id, request.userId).asResponse()

    @PreAuthorize("hasPermission('__NO_TARGET__', 'Team', 'delete')")
    @DeleteMapping("/roster/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun removeRosterEntry(@PathVariable id: Long) {
        rosters.remove(id)
    }
}
