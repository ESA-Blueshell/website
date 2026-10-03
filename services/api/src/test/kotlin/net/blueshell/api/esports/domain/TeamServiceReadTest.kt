package net.blueshell.api.esports.domain

import net.blueshell.api.esports.persistence.Team
import net.blueshell.api.esports.persistence.TeamRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.Optional

class TeamServiceReadTest {
    private val teams = mock<TeamRepository>()
    private val events = mock<org.springframework.context.ApplicationEventPublisher>()
    private val service = TeamService(teams, mock(), mock(), events)

    @Test
    fun `reads a team, and refuses one that is not there`() {
        val team = Team(name = "BS Nomads")
        whenever(teams.findById(1)).thenReturn(Optional.of(team))
        whenever(teams.findById(2)).thenReturn(Optional.empty())

        assertThat(service.findById(1)).isSameAs(team)
        assertThatThrownBy { service.findById(2) }.isInstanceOf(TeamNotFoundException::class.java)
    }

    @org.junit.jupiter.api.Test
    fun `archives a team once, and brings it back, telling the cohorts each time`() {
        val team =
            net.blueshell.api.esports.persistence
                .Team(name = "BS Draft")
                .also { it.id = 3 }
        whenever(teams.findById(3)).thenReturn(java.util.Optional.of(team))
        whenever(teams.save(team)).thenReturn(team)

        org.assertj.core.api.Assertions
            .assertThat(service.archive(3, true).archived)
            .isTrue()
        service.archive(3, true)
        service.archive(3, false)

        org.mockito.kotlin
            .verify(events)
            .publishEvent(
                net.blueshell.api.esports.api
                    .TeamArchiveChanged(3, true),
            )
        org.mockito.kotlin
            .verify(events)
            .publishEvent(
                net.blueshell.api.esports.api
                    .TeamArchiveChanged(3, false),
            )
    }
}
