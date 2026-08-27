package net.blueshell.api.domain.esports.application

import net.blueshell.api.domain.esports.persistence.Season
import net.blueshell.api.domain.esports.persistence.Team
import net.blueshell.api.domain.esports.persistence.repository.SeasonRepository
import net.blueshell.api.domain.esports.persistence.repository.TeamRepository
import net.blueshell.api.domain.esports.persistence.repository.TeamRosterEntryRepository
import net.blueshell.api.shared.enums.Game
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.shared.enums.TeamRole
import net.blueshell.api.testsupport.UserTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.LocalDate

/**
 * A roster usually carries over from one season to the next unchanged, so a team returning
 * for another season is the same five people far more often than it is five new ones. This
 * covers fielding a team somewhere it has not played, with or without the line-up it last had.
 */
@SpringBootTest
class FieldTeamWithLineupIT : UserTestSupport() {
    @Autowired private lateinit var rosters: TeamRosterService

    @Autowired private lateinit var fielded: TeamSeasonService

    @Autowired private lateinit var seasons: SeasonRepository

    @Autowired private lateinit var teams: TeamRepository

    @Autowired private lateinit var entries: TeamRosterEntryRepository

    private fun season(from: LocalDate): Season = seasons.save(
        Season(name = "Season ${System.nanoTime()}", startDate = from, endDate = from.plusMonths(5)),
    )

    private fun team(): Team = teams.save(Team(game = Game.TRACKMANIA, name = "BS Carry ${System.nanoTime()}"))

    @Test
    fun `an existing team is fielded in a season it was not in`() {
        val earlier = season(LocalDate.of(2030, 2, 1))
        val later = season(LocalDate.of(2030, 9, 1))
        val team = team()
        rosters.add(team.id!!, earlier.id!!, "veteran", TeamRole.PLAYER, null, null)

        val result = rosters.fieldWithLineup(team.id!!, later.id!!, carryLineup = false)

        assertThat(fielded.isFielded(team.id!!, later.id!!)).isTrue()
        // Nobody asked for the line-up, so the season is fielded and empty.
        assertThat(result.carried).isEmpty()
        assertThat(entries.findAllByTeamAndSeason(team.id!!, later.id!!)).isEmpty()
    }

    @Test
    fun `the line-up a team last had comes across when it is asked for`() {
        val earlier = season(LocalDate.of(2030, 2, 1))
        val later = season(LocalDate.of(2030, 9, 1))
        val team = team()
        rosters.add(team.id!!, earlier.id!!, "driver", TeamRole.PLAYER, null, "Sanne Kok")
        rosters.add(team.id!!, earlier.id!!, "reserve", TeamRole.SUBSTITUTE, null, null)

        val result = rosters.fieldWithLineup(team.id!!, later.id!!, carryLineup = true)

        assertThat(result.carried).extracting<String> { it.handle }.containsExactly("driver", "reserve")
        val landed = entries.findAllByTeamAndSeason(team.id!!, later.id!!)
        assertThat(landed).extracting<String> { it.handle }.containsExactly("driver", "reserve")
        // What was published about somebody comes across with them, role and name included.
        assertThat(landed.single { it.handle == "driver" }.displayName).isEqualTo("Sanne Kok")
        assertThat(landed.single { it.handle == "reserve" }.teamRole).isEqualTo(TeamRole.SUBSTITUTE)
        // The season it was copied from still has its own.
        assertThat(entries.findAllByTeamAndSeason(team.id!!, earlier.id!!)).hasSize(2)
    }

    @Test
    fun `the most recent line-up is the one carried, not the first`() {
        // Written down in the wrong order on purpose: the newest line-up is the one that ran
        // most recently, not the row that was entered last.
        val middle = season(LocalDate.of(2029, 9, 1))
        val oldest = season(LocalDate.of(2029, 2, 1))
        val target = season(LocalDate.of(2030, 9, 1))
        val team = team()
        rosters.add(team.id!!, oldest.id!!, "long-gone", TeamRole.PLAYER, null, null)
        rosters.add(team.id!!, middle.id!!, "current", TeamRole.PLAYER, null, null)

        val result = rosters.fieldWithLineup(team.id!!, target.id!!, carryLineup = true)

        assertThat(result.carried).extracting<String> { it.handle }.containsExactly("current")
    }

    @Test
    fun `asking to carry a line-up for a team that never had one carries nothing`() {
        val target = season(LocalDate.of(2030, 9, 1))
        val team = team()

        val result = rosters.fieldWithLineup(team.id!!, target.id!!, carryLineup = true)

        assertThat(result.carried).isEmpty()
        assertThat(fielded.isFielded(team.id!!, target.id!!)).isTrue()
    }

    @Test
    fun `fielding a team where it already plays changes nothing and duplicates nobody`() {
        val earlier = season(LocalDate.of(2030, 2, 1))
        val later = season(LocalDate.of(2030, 9, 1))
        val team = team()
        rosters.add(team.id!!, earlier.id!!, "driver", TeamRole.PLAYER, null, null)
        rosters.fieldWithLineup(team.id!!, later.id!!, carryLineup = true)

        val again = rosters.fieldWithLineup(team.id!!, later.id!!, carryLineup = true)

        // The season already holds the line-up, so a second ask leaves it alone.
        assertThat(again.carried).isEmpty()
        assertThat(entries.findAllByTeamAndSeason(team.id!!, later.id!!)).hasSize(1)
        assertThat(fielded.seasonsOf(team.id!!).count { it.season.id == later.id }).isEqualTo(1)
    }

    @Test
    fun `the answer names who came across, so it can be shown before anything is published`() {
        val board = createUserWithRole(Role.BOARD)
        val earlier = season(LocalDate.of(2030, 2, 1))
        val later = season(LocalDate.of(2030, 9, 1))
        val team = team()
        rosters.add(team.id!!, earlier.id!!, "driver", TeamRole.PLAYER, null, "Sanne Kok")

        mvc.perform(
            put("/esports/seasons/{seasonId}/teams/{teamId}", later.id, team.id)
                .with(bearer(board))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"carryLineup":true}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.team.id").value(team.id!!.toInt()))
            .andExpect(jsonPath("$.season.id").value(later.id!!.toInt()))
            .andExpect(jsonPath("$.carried.length()").value(1))
            .andExpect(jsonPath("$.carried[0].handle").value("driver"))
            .andExpect(jsonPath("$.carried[0].displayName").value("Sanne Kok"))
            .andExpect(jsonPath("$.carried[0].seasonId").value(later.id!!.toInt()))
    }

    @Test
    fun `a request with no body fields the team and carries nothing`() {
        val board = createUserWithRole(Role.BOARD)
        val earlier = season(LocalDate.of(2030, 2, 1))
        val later = season(LocalDate.of(2030, 9, 1))
        val team = team()
        rosters.add(team.id!!, earlier.id!!, "driver", TeamRole.PLAYER, null, null)

        mvc.perform(put("/esports/seasons/{seasonId}/teams/{teamId}", later.id, team.id).with(bearer(board)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.carried.length()").value(0))

        assertThat(fielded.isFielded(team.id!!, later.id!!)).isTrue()
    }

    @Test
    fun `a member may not field a team`() {
        val member = createUserWithRole(Role.MEMBER)
        val later = season(LocalDate.of(2030, 9, 1))
        val team = team()

        mvc.perform(
            put("/esports/seasons/{seasonId}/teams/{teamId}", later.id, team.id)
                .with(bearer(member))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"carryLineup":true}"""),
        ).andExpect(status().isForbidden)

        assertThat(fielded.isFielded(team.id!!, later.id!!)).isFalse()
    }
}
