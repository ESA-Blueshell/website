package net.blueshell.api.esports.web

import net.blueshell.api.esports.domain.EsportsPageView
import net.blueshell.api.esports.domain.RosterMemberView
import net.blueshell.api.esports.domain.SeasonView
import net.blueshell.api.esports.domain.TeamView
import net.blueshell.api.esports.persistence.EsportsBanner
import net.blueshell.api.esports.persistence.GamePage
import net.blueshell.api.esports.persistence.Season
import net.blueshell.api.esports.persistence.Team
import net.blueshell.api.esports.persistence.TeamRosterEntry
import net.blueshell.api.esports.persistence.UserGameAccount
import net.blueshell.api.file.api.PublicFileUrls

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
    posterUrl = poster?.id?.let(PublicFileUrls::of),
)

fun RosterMemberView.asResponse() = RosterMemberResponse(
    role = role,
    handle = handle,
    name = name,
    roleTitle = roleTitle,
    description = description,
    iconUrl = iconFileId?.let(PublicFileUrls::of),
)

fun TeamView.asResponse() = TeamRosterResponse(
    id = id,
    name = name,
    image = image,
    members = members.map { it.asResponse() },
    posterUrl = posterFileId?.let(PublicFileUrls::of),
    bannerUrl = bannerFileId?.let(PublicFileUrls::of),
)

fun EsportsPageView.asResponse() = EsportsPageResponse(
    game = game,
    season = season?.asResponse(),
    seasons = seasons.map { it.asResponse() },
    teams = teams.map { it.asResponse() },
    bannerUrl = bannerFileId?.let(PublicFileUrls::of),
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
    iconUrl = icon?.id?.let(PublicFileUrls::of),
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
    mark = mark,
    banner = banner,
    intro = intro,
    sortIndex = sortIndex,
    fielded = fielded,
)

fun EsportsBanner.asResponse() = EsportsBannerResponse(
    id = id!!,
    game = game,
    seasonId = seasonId,
    teamId = teamId,
    url = PublicFileUrls.of(file.id!!),
)
