package net.blueshell.api.esports.web

import net.blueshell.api.esports.domain.EsportsPageView
import net.blueshell.api.esports.domain.RosterMemberView
import net.blueshell.api.esports.domain.SeasonView
import net.blueshell.api.esports.domain.TeamView
import net.blueshell.api.esports.persistence.GamePage
import net.blueshell.api.esports.persistence.Season
import net.blueshell.api.esports.persistence.Team
import net.blueshell.api.esports.persistence.TeamRosterEntry
import net.blueshell.api.esports.persistence.UserGameAccount
import net.blueshell.api.file.api.asImage

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
    banner = banner?.asImage(),
    icon = icon?.asImage(),
)

fun RosterMemberView.asResponse() = RosterMemberResponse(
    role = role,
    handle = handle,
    name = name,
    roleTitle = roleTitle,
    description = description,
    icon = icon,
)

fun TeamView.asResponse() = TeamRosterResponse(
    id = id,
    name = name,
    members = members.map { it.asResponse() },
    banner = banner,
    icon = icon,
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
    roleTitle = roleTitle,
    description = description,
    icon = icon?.asImage(),
)

fun UserGameAccount.asResponse() = GameAccountResponse(
    id = id!!,
    userId = userId,
    game = game,
    handle = handle,
)

fun GamePage.asResponse(): GamePageResponse = GamePageResponse(
    game = game,
    name = name,
    slug = slug,
    accent = accent,
    banner = banner?.asImage(),
    icon = icon?.asImage(),
    intro = intro,
    sortIndex = sortIndex,
    fielded = fielded,
)

