package net.blueshell.api.domain.esports.web

import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.annotation.security.PermitAll
import jakarta.validation.Valid
import net.blueshell.api.domain.esports.command.DeleteSeasonCommand
import net.blueshell.api.domain.esports.command.DeleteTeamCommand
import net.blueshell.api.domain.esports.command.FindEsportsPageCommand
import net.blueshell.api.domain.esports.command.FindRosterCommand
import net.blueshell.api.domain.esports.command.FindSeasonsCommand
import net.blueshell.api.domain.esports.command.FindTeamsCommand
import net.blueshell.api.domain.esports.command.LinkRosterEntryCommand
import net.blueshell.api.domain.esports.command.RemoveRosterEntryCommand
import net.blueshell.api.domain.esports.web.dto.request.AddRosterEntryRequest
import net.blueshell.api.domain.esports.web.dto.request.CreateTeamRequest
import net.blueshell.api.domain.esports.web.dto.request.LinkRosterEntryRequest
import net.blueshell.api.domain.esports.web.dto.request.SeasonRequest
import net.blueshell.api.domain.esports.web.dto.request.UpdateRosterEntryRequest
import net.blueshell.api.domain.esports.web.dto.request.UpdateTeamRequest
import net.blueshell.api.domain.esports.web.dto.response.EsportsPageResponse
import net.blueshell.api.domain.esports.web.dto.response.RosterEntryResponse
import net.blueshell.api.domain.esports.web.dto.response.SeasonResponse
import net.blueshell.api.domain.esports.web.dto.response.TeamResponse
import net.blueshell.api.domain.esports.web.mapping.request.asCommand
import net.blueshell.api.domain.esports.web.mapping.request.asCreateCommand
import net.blueshell.api.domain.esports.web.mapping.request.asUpdateCommand
import net.blueshell.api.domain.esports.web.mapping.response.asResponse
import net.blueshell.api.shared.command.CommandBus
import net.blueshell.api.shared.enums.Game
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
    private val commandBus: CommandBus,
) {
    @GetMapping("/games/{game}")
    @PermitAll
    fun findEsportsPage(
        @PathVariable game: Game,
        @RequestParam(required = false) seasonId: Long?,
    ): EsportsPageResponse =
        commandBus.dispatch(FindEsportsPageCommand(game = game, seasonId = seasonId)).asResponse()

    @GetMapping("/seasons")
    @PermitAll
    fun findSeasons(): List<SeasonResponse> =
        commandBus.dispatch(FindSeasonsCommand()).map { it.asResponse() }

    @PreAuthorize("hasPermission('__NO_TARGET__', 'Team', 'write')")
    @PostMapping("/seasons")
    @ResponseStatus(HttpStatus.CREATED)
    fun createSeason(@Valid @RequestBody request: SeasonRequest): SeasonResponse =
        commandBus.dispatch(request.asCreateCommand()).asResponse()

    @PreAuthorize("hasPermission('__NO_TARGET__', 'Team', 'write')")
    @PutMapping("/seasons/{id}")
    fun updateSeason(
        @PathVariable id: Long,
        @Valid @RequestBody request: SeasonRequest,
    ): SeasonResponse = commandBus.dispatch(request.asUpdateCommand(id)).asResponse()

    @PreAuthorize("hasPermission('__NO_TARGET__', 'Team', 'delete')")
    @DeleteMapping("/seasons/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteSeason(@PathVariable id: Long) {
        commandBus.dispatch(DeleteSeasonCommand(id))
    }

    @GetMapping("/teams")
    @PermitAll
    fun findTeams(@RequestParam game: Game): List<TeamResponse> =
        commandBus.dispatch(FindTeamsCommand(game)).map { it.asResponse() }

    @PreAuthorize("hasPermission('__NO_TARGET__', 'Team', 'write')")
    @PostMapping("/teams")
    @ResponseStatus(HttpStatus.CREATED)
    fun createTeam(@Valid @RequestBody request: CreateTeamRequest): TeamResponse =
        commandBus.dispatch(request.asCommand()).asResponse()

    @PreAuthorize("hasPermission('__NO_TARGET__', 'Team', 'write')")
    @PutMapping("/teams/{id}")
    fun updateTeam(
        @PathVariable id: Long,
        @Valid @RequestBody request: UpdateTeamRequest,
    ): TeamResponse = commandBus.dispatch(request.asCommand(id)).asResponse()

    @PreAuthorize("hasPermission('__NO_TARGET__', 'Team', 'delete')")
    @DeleteMapping("/teams/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteTeam(@PathVariable id: Long) {
        commandBus.dispatch(DeleteTeamCommand(id))
    }

    /** The admin view of a roster: the same rows the page shows, with the names attached. */
    @PreAuthorize("hasPermission('__NO_TARGET__', 'Team', 'write')")
    @GetMapping("/teams/{teamId}/roster")
    fun findRoster(
        @PathVariable teamId: Long,
        @RequestParam seasonId: Long,
    ): List<RosterEntryResponse> =
        commandBus.dispatch(FindRosterCommand(teamId, seasonId)).map { it.asResponse() }

    @PreAuthorize("hasPermission('__NO_TARGET__', 'Team', 'write')")
    @PostMapping("/teams/{teamId}/roster")
    @ResponseStatus(HttpStatus.CREATED)
    fun addRosterEntry(
        @PathVariable teamId: Long,
        @Valid @RequestBody request: AddRosterEntryRequest,
    ): RosterEntryResponse = commandBus.dispatch(request.asCommand(teamId)).asResponse()

    @PreAuthorize("hasPermission('__NO_TARGET__', 'Team', 'write')")
    @PutMapping("/roster/{id}")
    fun updateRosterEntry(
        @PathVariable id: Long,
        @Valid @RequestBody request: UpdateRosterEntryRequest,
    ): RosterEntryResponse = commandBus.dispatch(request.asCommand(id)).asResponse()

    @PreAuthorize("hasPermission('__NO_TARGET__', 'Team', 'write')")
    @PutMapping("/roster/{id}/member")
    fun linkRosterEntry(
        @PathVariable id: Long,
        @RequestBody request: LinkRosterEntryRequest,
    ): RosterEntryResponse =
        commandBus.dispatch(LinkRosterEntryCommand(id = id, userId = request.userId)).asResponse()

    @PreAuthorize("hasPermission('__NO_TARGET__', 'Team', 'delete')")
    @DeleteMapping("/roster/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun removeRosterEntry(@PathVariable id: Long) {
        commandBus.dispatch(RemoveRosterEntryCommand(id))
    }
}
