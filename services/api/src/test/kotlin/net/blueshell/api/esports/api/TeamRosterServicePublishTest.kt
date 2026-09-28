package net.blueshell.api.esports.api

import net.blueshell.api.esports.domain.SeasonGameService
import net.blueshell.api.esports.domain.SeasonService
import net.blueshell.api.esports.domain.TeamInput
import net.blueshell.api.esports.domain.TeamSeasonService
import net.blueshell.api.esports.domain.TeamService
import net.blueshell.api.esports.persistence.Season
import net.blueshell.api.esports.persistence.Team
import net.blueshell.api.esports.persistence.TeamRosterEntry
import net.blueshell.api.esports.persistence.TeamRosterEntryRepository
import net.blueshell.api.esports.persistence.TeamSeason
import net.blueshell.api.file.api.StoredPictures
import net.blueshell.api.shared.enums.TeamRole
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.LocalDate
import java.util.Optional

class TeamRosterServicePublishTest {
    private val team = Team(name = "BS Draft").also { it.id = 3 }
    private val season =
        Season(name = "Autumn 2030", startDate = LocalDate.of(2030, 9, 1), endDate = LocalDate.of(2031, 1, 31)).also {
            it.id =
                5
        }
    private val fielding = TeamSeason(team = team, game = "CS2", season = season)
    private val kept = TeamRosterEntry(teamSeason = fielding, handle = "kept", sortIndex = 4).also { it.id = 11 }
    private val gone = TeamRosterEntry(teamSeason = fielding, handle = "gone").also { it.id = 12 }

    private val entries =
        mock<TeamRosterEntryRepository>().also { mock ->
            whenever(mock.save(any<TeamRosterEntry>())).thenAnswer { it.arguments[0] }
            whenever(mock.findById(11)).thenReturn(Optional.of(kept))
            whenever(mock.findById(12)).thenReturn(Optional.of(gone))
            whenever(mock.findAllByTeamAndSeason(any(), any(), any())).thenReturn(emptyList())
        }
    private val teams =
        mock<TeamService>().also { mock ->
            whenever(mock.create(any())).thenReturn(team)
            whenever(mock.update(any(), any())).thenReturn(team)
            whenever(mock.findById(3)).thenReturn(team)
        }
    private val seasons = mock<SeasonService>().also { whenever(it.findById(5)).thenReturn(season) }
    private val fielded = mock<TeamSeasonService>().also { whenever(it.field(any(), any(), any())).thenReturn(fielding) }
    private val service = TeamRosterService(entries, teams, seasons, fielded, mock<SeasonGameService>(), mock<StoredPictures>())

    private fun draft(
        teamId: Long?,
        vararg entries: DraftEntry,
    ) = LineupDraft(teamId, "BS Draft", null, "CS2", 5, null, listOf(12), entries.toList())

    private fun entry(
        id: Long?,
        handle: String,
        userId: Long? = null,
    ) = DraftEntry(id, RosterEntryInput(handle, TeamRole.PLAYER), userId)

    @Test
    fun `writes the team, drops who came off and puts everybody else in the order given`() {
        val published = service.publish(draft(3, entry(null, "new"), entry(11, "kept-renamed", userId = 42)))

        verify(teams).update(3, TeamInput("BS Draft"))
        verify(teams, never()).create(any())
        verify(entries).delete(gone)
        assertThat(published.team).isSameAs(team)
        assertThat(published.roster.map { it.handle to it.sortIndex }).containsExactly("new" to 0, "kept-renamed" to 1)
        assertThat(kept.userId).isEqualTo(42)
    }

    @Test
    fun `a draft with no team makes it under the name given`() {
        service.publish(draft(null))

        verify(teams).create(TeamInput("BS Draft"))
    }
}
