package net.blueshell.api.domain.esports.web.mapping.request

import net.blueshell.api.domain.esports.command.AddRosterEntryCommand
import net.blueshell.api.domain.esports.command.CreateSeasonCommand
import net.blueshell.api.domain.esports.command.CreateTeamCommand
import net.blueshell.api.domain.esports.command.SetGameAccountCommand
import net.blueshell.api.domain.esports.command.UpdateRosterEntryCommand
import net.blueshell.api.domain.esports.command.UpdateSeasonCommand
import net.blueshell.api.domain.esports.command.UpdateTeamCommand
import net.blueshell.api.domain.esports.web.dto.request.AddRosterEntryRequest
import net.blueshell.api.domain.esports.web.dto.request.CreateTeamRequest
import net.blueshell.api.domain.esports.web.dto.request.GameAccountRequest
import net.blueshell.api.domain.esports.web.dto.request.SeasonRequest
import net.blueshell.api.domain.esports.web.dto.request.UpdateRosterEntryRequest
import net.blueshell.api.domain.esports.web.dto.request.UpdateTeamRequest
import net.blueshell.api.shared.enums.Game

fun SeasonRequest.asCreateCommand() = CreateSeasonCommand(
    name = name,
    startDate = startDate,
    endDate = endDate,
)

fun SeasonRequest.asUpdateCommand(id: Long) = UpdateSeasonCommand(
    id = id,
    name = name,
    startDate = startDate,
    endDate = endDate,
)

fun CreateTeamRequest.asCommand() = CreateTeamCommand(game = game, name = name, image = image)

fun UpdateTeamRequest.asCommand(id: Long) = UpdateTeamCommand(id = id, name = name, image = image)

fun AddRosterEntryRequest.asCommand(teamId: Long) = AddRosterEntryCommand(
    teamId = teamId,
    seasonId = seasonId,
    handle = handle,
    role = role,
    userId = userId,
    displayName = displayName,
)

fun UpdateRosterEntryRequest.asCommand(id: Long) = UpdateRosterEntryCommand(
    id = id,
    handle = handle,
    role = role,
    displayName = displayName,
    sortIndex = sortIndex,
)

fun GameAccountRequest.asCommand(userId: Long, game: Game) = SetGameAccountCommand(
    userId = userId,
    game = game,
    handle = handle,
)
