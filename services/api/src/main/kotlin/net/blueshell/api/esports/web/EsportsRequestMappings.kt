package net.blueshell.api.esports.web

import net.blueshell.api.esports.api.DraftEntry
import net.blueshell.api.esports.api.LineupDraft
import net.blueshell.api.esports.api.RosterEntryInput
import net.blueshell.api.esports.domain.SeasonInput
import net.blueshell.api.esports.domain.TeamInput

fun SeasonRequest.asInput() = SeasonInput(name, startDate, endDate)

fun TeamRequest.asInput() = TeamInput(name, icon)

fun AddRosterEntryRequest.asInput() = RosterEntryInput(handle, role, displayName, roleTitle, description, icon)

fun UpdateRosterEntryRequest.asInput() = RosterEntryInput(handle, role, displayName, roleTitle, description, icon)

fun LineupEntryRequest.asDraft() = DraftEntry(id, RosterEntryInput(handle, role, displayName, roleTitle, description, icon), userId)

fun PublishLineupRequest.asDraft(seasonId: Long) =
    LineupDraft(teamId, name, icon, game, seasonId, banner, removed, entries.map { it.asDraft() })
