package net.blueshell.api.domain.esports.command

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import net.blueshell.api.domain.esports.persistence.Season
import net.blueshell.api.domain.esports.persistence.Team
import net.blueshell.api.domain.esports.persistence.TeamRosterEntry
import net.blueshell.api.domain.esports.persistence.UserGameAccount
import net.blueshell.api.shared.command.Command
import net.blueshell.api.shared.enums.Game
import net.blueshell.api.shared.enums.TeamRole
import java.time.LocalDate

// ── Seasons ───────────────────────────────────────────────────────────────────

class FindSeasonsCommand : Command<List<Season>>

data class CreateSeasonCommand(
    @field:NotBlank(message = "Season name is required")
    @field:Size(min = 1, max = 64, message = "Name must be 1-64 characters")
    var name: String,

    @field:NotNull(message = "Start date is required")
    var startDate: LocalDate,

    @field:NotNull(message = "End date is required")
    var endDate: LocalDate,
) : Command<Season>

data class UpdateSeasonCommand(
    @field:NotNull(message = "Season id is required")
    var id: Long,

    @field:NotBlank(message = "Season name is required")
    @field:Size(min = 1, max = 64, message = "Name must be 1-64 characters")
    var name: String,

    @field:NotNull(message = "Start date is required")
    var startDate: LocalDate,

    @field:NotNull(message = "End date is required")
    var endDate: LocalDate,
) : Command<Season>

data class DeleteSeasonCommand(
    @field:NotNull(message = "Season id is required")
    var id: Long,
) : Command<Unit>

// ── Teams ─────────────────────────────────────────────────────────────────────

data class FindTeamsCommand(
    @field:NotNull(message = "Game is required")
    var game: Game,
) : Command<List<Team>>

data class CreateTeamCommand(
    @field:NotNull(message = "Game is required")
    var game: Game,

    @field:NotBlank(message = "Team name is required")
    @field:Size(min = 1, max = 128, message = "Name must be 1-128 characters")
    var name: String,

    @field:Size(max = 255, message = "Image must be at most 255 characters")
    var image: String?,
) : Command<Team>

data class UpdateTeamCommand(
    @field:NotNull(message = "Team id is required")
    var id: Long,

    @field:NotBlank(message = "Team name is required")
    @field:Size(min = 1, max = 128, message = "Name must be 1-128 characters")
    var name: String,

    @field:Size(max = 255, message = "Image must be at most 255 characters")
    var image: String?,
) : Command<Team>

data class DeleteTeamCommand(
    @field:NotNull(message = "Team id is required")
    var id: Long,
) : Command<Unit>

// ── Rosters ───────────────────────────────────────────────────────────────────

data class FindRosterCommand(
    @field:NotNull(message = "Team id is required")
    var teamId: Long,

    @field:NotNull(message = "Season id is required")
    var seasonId: Long,
) : Command<List<TeamRosterEntry>>

data class AddRosterEntryCommand(
    @field:NotNull(message = "Team id is required")
    var teamId: Long,

    @field:NotNull(message = "Season id is required")
    var seasonId: Long,

    @field:NotBlank(message = "Handle is required")
    @field:Size(min = 1, max = 128, message = "Handle must be 1-128 characters")
    var handle: String,

    @field:NotNull(message = "Role is required")
    var role: TeamRole,

    var userId: Long?,

    @field:Size(max = 128, message = "Name must be at most 128 characters")
    var displayName: String?,
) : Command<TeamRosterEntry>

data class UpdateRosterEntryCommand(
    @field:NotNull(message = "Roster entry id is required")
    var id: Long,

    @field:NotBlank(message = "Handle is required")
    @field:Size(min = 1, max = 128, message = "Handle must be 1-128 characters")
    var handle: String,

    @field:NotNull(message = "Role is required")
    var role: TeamRole,

    @field:Size(max = 128, message = "Name must be at most 128 characters")
    var displayName: String?,

    @field:NotNull(message = "Order is required")
    var sortIndex: Int,
) : Command<TeamRosterEntry>

/** A null user unlinks: an entry nobody can be attributed to is a roster spot all the same. */
data class LinkRosterEntryCommand(
    @field:NotNull(message = "Roster entry id is required")
    var id: Long,

    var userId: Long?,
) : Command<TeamRosterEntry>

data class RemoveRosterEntryCommand(
    @field:NotNull(message = "Roster entry id is required")
    var id: Long,
) : Command<Unit>

// ── Game handles ──────────────────────────────────────────────────────────────

data class FindGameAccountsCommand(
    @field:NotNull(message = "User id is required")
    var userId: Long,
) : Command<List<UserGameAccount>>

data class SetGameAccountCommand(
    @field:NotNull(message = "User id is required")
    var userId: Long,

    @field:NotNull(message = "Game is required")
    var game: Game,

    @field:NotBlank(message = "Handle is required")
    @field:Size(min = 1, max = 128, message = "Handle must be 1-128 characters")
    var handle: String,
) : Command<UserGameAccount>

data class ClearGameAccountCommand(
    @field:NotNull(message = "User id is required")
    var userId: Long,

    @field:NotNull(message = "Game is required")
    var game: Game,
) : Command<Unit>

// ── The public page ───────────────────────────────────────────────────────────

data class FindEsportsPageCommand(
    @field:NotNull(message = "Game is required")
    var game: Game,

    var seasonId: Long?,
) : Command<EsportsPageView>
