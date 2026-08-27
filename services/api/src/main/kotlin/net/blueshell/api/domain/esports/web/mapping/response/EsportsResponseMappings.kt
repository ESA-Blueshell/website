package net.blueshell.api.domain.esports.web.mapping.response

import net.blueshell.api.domain.esports.application.EsportsPageView
import net.blueshell.api.domain.esports.application.RosterMemberView
import net.blueshell.api.domain.esports.application.SeasonView
import net.blueshell.api.domain.esports.application.TeamView
import net.blueshell.api.domain.esports.persistence.Season
import net.blueshell.api.domain.esports.persistence.Team
import net.blueshell.api.domain.esports.persistence.TeamRosterEntry
import net.blueshell.api.domain.esports.persistence.UserGameAccount
import net.blueshell.api.domain.esports.web.dto.response.EsportsPageResponse
import net.blueshell.api.domain.esports.web.dto.response.GameAccountResponse
import net.blueshell.api.domain.esports.web.dto.response.RosterEntryResponse
import net.blueshell.api.domain.esports.web.dto.response.RosterMemberResponse
import net.blueshell.api.domain.esports.web.dto.response.SeasonResponse
import net.blueshell.api.domain.esports.web.dto.response.TeamResponse
import net.blueshell.api.domain.esports.web.dto.response.TeamRosterResponse

fun Season.asResponse() = SeasonResponse(
    id = id!!,
    name = name,
    startDate = startDate,
    endDate = endDate,
)

fun SeasonView.asResponse() = SeasonResponse(
    id = id,
    name = name,
    startDate = startDate,
    endDate = endDate,
)

fun Team.asResponse() = TeamResponse(
    id = id!!,
    game = game,
    name = name,
    image = image,
)

fun RosterMemberView.asResponse() = RosterMemberResponse(role = role, handle = handle, name = name)

fun TeamView.asResponse() = TeamRosterResponse(
    id = id,
    name = name,
    image = image,
    members = members.map { it.asResponse() },
)

fun EsportsPageView.asResponse() = EsportsPageResponse(
    game = game,
    season = season?.asResponse(),
    seasons = seasons.map { it.asResponse() },
    teams = teams.map { it.asResponse() },
)

/** The admin view of an entry, which unlike the public one carries the real name. */
fun TeamRosterEntry.asResponse() = RosterEntryResponse(
    id = id!!,
    teamId = team.id!!,
    seasonId = season.id!!,
    role = teamRole,
    handle = handle,
    displayName = displayName,
    userId = userId,
    sortIndex = sortIndex,
)

fun UserGameAccount.asResponse() = GameAccountResponse(
    id = id!!,
    userId = userId,
    game = game,
    handle = handle,
)
