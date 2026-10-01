package net.blueshell.api.esports.domain

import net.blueshell.api.esports.persistence.Season
import net.blueshell.api.esports.persistence.SeasonRepository
import net.blueshell.api.esports.persistence.Team
import net.blueshell.api.esports.persistence.TeamRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.LocalDate
import java.util.Optional

/** Seasons and teams are written from one input each. */
class EsportsInputWritesTest {
    @Test
    fun `a season is made and edited from its input, trimmed`() {
        val repository = mock<SeasonRepository> { on { save(any<Season>()) } doAnswer { it.getArgument(0) } }
        whenever(repository.findAllOverlapping(any(), any())).thenReturn(emptyList())
        val existing = Season(name = "Old", startDate = LocalDate.of(2030, 9, 1), endDate = LocalDate.of(2031, 1, 31))
        whenever(repository.findById(4)).thenReturn(Optional.of(existing))
        val service = SeasonService(repository)
        val input = SeasonInput(" Autumn 2030 ", LocalDate.of(2030, 9, 1), LocalDate.of(2031, 1, 31))

        assertThat(service.create(input).name).isEqualTo("Autumn 2030")
        assertThat(service.update(4, input).name).isEqualTo("Autumn 2030")
    }

    @Test
    fun `a team is made and edited from its input, trimmed`() {
        val repository = mock<TeamRepository> { on { save(any<Team>()) } doAnswer { it.getArgument(0) } }
        whenever(repository.findById(3)).thenReturn(Optional.of(Team(name = "Old")))
        val service = TeamService(repository, mock(), mock(), mock())

        assertThat(service.create(TeamInput(" BS Nomads ")).name).isEqualTo("BS Nomads")
        assertThat(service.update(3, TeamInput(" BS Settlers ")).name).isEqualTo("BS Settlers")
    }
}
