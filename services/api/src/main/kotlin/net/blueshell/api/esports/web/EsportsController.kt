package net.blueshell.api.esports.web

import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.annotation.security.PermitAll
import jakarta.validation.Valid
import net.blueshell.api.esports.domain.EsportsPageQueryService
import net.blueshell.api.esports.domain.GamePageService
import net.blueshell.api.esports.domain.SeasonService
import net.blueshell.api.esports.api.TeamRosterService
import net.blueshell.api.file.api.asImage
import net.blueshell.api.esports.domain.SeasonGameService
import net.blueshell.api.esports.domain.TeamSeasonService
import net.blueshell.api.security.SecurityUtils
import net.blueshell.api.shared.enums.Role
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
    private val entered: SeasonGameService,
) {
    /**
     * Whether the caller may edit, which decides what a season's band answers with.
     *
     * The same authority the write routes are guarded by. Read rather than declared, because
     * this route answers everybody and answers them differently.
     */
    private fun mayEditEsports(): Boolean = SecurityUtils.hasAuthority(Role.BOARD)

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
    fun findGamePages(): List<GamePageResponse> {
        // Which games the association currently plays is derived rather than stored, so it is
        // read once for the whole list rather than asked of each row.
        val played = fielded.currentlyPlayed()
        return gamePages.findAll().map { it.asResponse(played.contains(it.game)) }
    }

    /** A game the association has started playing. Its page answers straight away. */
    @PreAuthorize("hasPermission('__NO_TARGET__', 'Team', 'write')")
    @PostMapping("/games")
    @ResponseStatus(HttpStatus.CREATED)
    fun createGame(@Valid @RequestBody request: CreateGameRequest): GamePageResponse =
        gamePages.create(
            name = request.name,
            slug = request.slug,
            intro = request.intro,
            accent = request.accent,
            banner = request.banner,
            icon = request.icon,
            sortIndex = request.sortIndex,
        ).asResponse(current = false)

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
            banner = request.banner,
            icon = request.icon,
            sortIndex = request.sortIndex,
        ).asResponse(fielded.currentlyPlayed().contains(game))

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

    /**
     * Every game that ran in one season, with what it fielded.
     *
     * One read for the band rather than one per game. A game entered with nobody fielded in it
     * is answered only to somebody who may edit, marked as not public — the rule turns on who
     * is asking, so it is applied here rather than in the pages.
     */
    @PermitAll
    @GetMapping("/seasons/{seasonId}/games")
    fun findSeasonGames(@PathVariable seasonId: Long): List<SeasonGameResponse> =
        page.gamesOf(seasonId, mayEditEsports()).map { it.asResponse() }

    /** Records that a game runs in a season, before anybody has been fielded in it. */
    @PreAuthorize("hasPermission('__NO_TARGET__', 'Team', 'write')")
    @PutMapping("/seasons/{seasonId}/games/{game}")
    fun enterGame(@PathVariable seasonId: Long, @PathVariable game: String): SeasonGameResponse {
        entered.enter(seasonId, game)
        return page.gamesOf(seasonId, mayEdit = true).first { it.game == game }.asResponse()
    }

    /** Takes a game out of a season, which is only possible while it holds no teams. */
    @PreAuthorize("hasPermission('__NO_TARGET__', 'Team', 'delete')")
    @DeleteMapping("/seasons/{seasonId}/games/{game}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun leaveGame(@PathVariable seasonId: Long, @PathVariable game: String) {
        entered.leave(seasonId, game)
    }

    @GetMapping("/seasons")
    @PermitAll
    fun findSeasons(): List<SeasonResponse> {
        // Which seasons had something fielded is read once for the whole list: a visitor's
        // strip carries those, and the board's carries every season, because a season has to
        // be reachable before a game can be entered in it.
        val played = fielded.seasonsWithTeams()
        return seasons.findAll().map { it.asResponse(played.contains(it.id)) }
    }

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

    /**
     * Every team the association has.
     *
     * Not scoped by game: the pool is shared, so a team that has only ever played one game is
     * still one the board can field in another.
     */
    @GetMapping("/teams")
    @PermitAll
    fun findTeams(): List<TeamResponse> = teams.pool().map { it.asResponse() }

    @PreAuthorize("hasPermission('__NO_TARGET__', 'Team', 'write')")
    @PostMapping("/teams")
    @ResponseStatus(HttpStatus.CREATED)
    fun createTeam(@Valid @RequestBody request: CreateTeamRequest): TeamResponse =
        teams.create(request.name, request.icon).asResponse()

    @PreAuthorize("hasPermission('__NO_TARGET__', 'Team', 'write')")
    @PutMapping("/teams/{id}")
    fun updateTeam(
        @PathVariable id: Long,
        @Valid @RequestBody request: UpdateTeamRequest,
    ): TeamResponse = teams.update(id, request.name, request.icon).asResponse()

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
        @Valid @RequestBody request: FieldTeamRequest,
    ): FieldedTeamResponse {
        val fieldedTeam = rosters.fieldWithLineup(teamId, request.game, seasonId, request.carryLineup, request.banner)
        return FieldedTeamResponse(
            team = fieldedTeam.team.asResponse(),
            game = fieldedTeam.fielding.game,
            season = fieldedTeam.season.asResponse(),
            banner = fieldedTeam.fielding.banner?.asImage(),
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
        @RequestParam game: String,
    ) {
        fielded.unfield(teamId, game, seasonId)
    }

    /**
     * The line-ups a team has, newest first: which game, which season. Public, as the pages
     * show it.
     *
     * Each is a fielding rather than a season, because a team that played two games in one
     * season has two of them, with a line-up in each.
     */
    @PermitAll
    @GetMapping("/teams/{teamId}/seasons")
    fun findTeamSeasons(@PathVariable teamId: Long): List<FieldingResponse> =
        fielded.seasonsOf(teamId).map { FieldingResponse(game = it.game, season = it.season.asResponse()) }

    /** The admin view of a roster: the same rows the page shows, with the names attached. */
    @PreAuthorize("hasPermission('__NO_TARGET__', 'Team', 'write')")
    @GetMapping("/teams/{teamId}/roster")
    fun findRoster(
        @PathVariable teamId: Long,
        @RequestParam game: String,
        @RequestParam seasonId: Long,
    ): List<RosterEntryResponse> =
        rosters.findByTeamAndSeason(teamId, game, seasonId).map { it.asResponse() }

    @PreAuthorize("hasPermission('__NO_TARGET__', 'Team', 'write')")
    @PostMapping("/teams/{teamId}/roster")
    @ResponseStatus(HttpStatus.CREATED)
    fun addRosterEntry(
        @PathVariable teamId: Long,
        @Valid @RequestBody request: AddRosterEntryRequest,
    ): RosterEntryResponse = rosters.add(
        teamId = teamId,
        game = request.game,
        seasonId = request.seasonId,
        handle = request.handle,
        role = request.role,
        userId = request.userId,
        displayName = request.displayName,
        roleTitle = request.roleTitle,
        description = request.description,
        icon = request.icon,
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
            icon = request.icon,
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
