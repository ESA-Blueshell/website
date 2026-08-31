package net.blueshell.api.esports.web

import net.blueshell.api.esports.domain.GameRostersView
import net.blueshell.api.esports.domain.RosterMemberView
import net.blueshell.api.esports.domain.SeasonGameView
import net.blueshell.api.esports.domain.SeasonView
import net.blueshell.api.esports.domain.TeamView
import net.blueshell.api.esports.persistence.Game
import net.blueshell.api.esports.persistence.Season
import net.blueshell.api.esports.persistence.Team
import net.blueshell.api.esports.persistence.TeamRosterEntry
import net.blueshell.api.esports.persistence.UserGameAccount
import net.blueshell.api.file.api.asImage

fun Season.asResponse(played: Boolean = false) = SeasonResponse(
    id = id!!,
    name = name,
    startDate = startDate,
    endDate = endDate,
    played = played,
)

// A season named inside a game's own page is one that game played, so it says so.
fun SeasonView.asResponse() = SeasonResponse(
    id = id,
    name = name,
    startDate = startDate,
    endDate = endDate,
    played = true,
)

fun Team.asResponse() = TeamResponse(
    id = id!!,
    name = name,
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

fun GameRostersView.asResponse() = GameRostersResponse(
    game = game,
    season = season?.asResponse(),
    seasons = seasons.map { it.asResponse() },
    teams = teams.map { it.asResponse() },
)

/** The admin view of an entry, which unlike the public one carries the real name. */
fun TeamRosterEntry.asResponse() = RosterEntryResponse(
    id = id!!,
    teamId = teamId!!,
    seasonId = seasonId!!,
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

fun Game.asResponse(current: Boolean = false): GameResponse = GameResponse(
    code = code,
    name = name,
    slug = slug,
    accent = accent,
    banner = banner?.asImage(),
    icon = icon?.asImage(),
    intro = intro,
    sortIndex = sortIndex,
    current = current,
)


fun SeasonGameView.asResponse() = SeasonGameResponse(
    game = game,
    teams = teams.map { it.asResponse() },
    public = public,
)
