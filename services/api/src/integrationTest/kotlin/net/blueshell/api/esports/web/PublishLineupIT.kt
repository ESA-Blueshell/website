package net.blueshell.api.esports.web

import net.blueshell.api.esports.api.TeamRosterService
import net.blueshell.api.esports.persistence.Season
import net.blueshell.api.esports.persistence.SeasonRepository
import net.blueshell.api.esports.persistence.Team
import net.blueshell.api.esports.persistence.TeamRepository
import net.blueshell.api.esports.persistence.TeamRosterEntryRepository
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

/** Saving a team's whole line-up for a season in one request, which lands whole or not at all. */
@SpringBootTest
class PublishLineupIT : UserTestSupport() {
    private val game = "TRACKMANIA"

    @Autowired private lateinit var rosters: TeamRosterService

    @Autowired private lateinit var seasons: SeasonRepository

    @Autowired private lateinit var teams: TeamRepository

    @Autowired private lateinit var entries: TeamRosterEntryRepository

    private fun season(): Season =
        seasons.save(
            Season(name = "Season ${System.nanoTime()}", startDate = LocalDate.of(2030, 9, 1), endDate = LocalDate.of(2031, 1, 31)),
        )

    private fun team(): Team = teams.save(Team(name = "BS Lineup ${System.nanoTime()}"))

    private fun handles(
        team: Team,
        season: Season,
    ): List<String> = entries.findAllByTeamAndSeason(team.id!!, game, season.id!!).sortedBy { it.sortIndex }.map { it.handle }

    private fun publish(
        season: Season,
        body: String,
    ) = mvc.perform(
        put("/esports/seasons/{seasonId}/lineup", season.id)
            .with(signedIn(createUserWithRole(Role.BOARD)))
            .contentType(MediaType.APPLICATION_JSON)
            .content(body),
    )

    @Test
    fun `a new team is made, fielded and named to in one request, in the order given`() {
        val season = season()

        publish(
            season,
            """
            {"name":"BS Fresh ${System.nanoTime()}","game":"$game","entries":[
              {"handle":"first","role":"PLAYER"},
              {"handle":"second","role":"SUBSTITUTE","displayName":"Sanne Kok"}
            ]}
            """.trimIndent(),
        ).andExpect(status().isOk)
            .andExpect(jsonPath("$.roster.length()").value(2))
            .andExpect(jsonPath("$.roster[0].handle").value("first"))
            .andExpect(jsonPath("$.roster[1].displayName").value("Sanne Kok"))
    }

    @Test
    fun `an existing line-up is renamed, reordered and trimmed in one request`() {
        val season = season()
        val team = team()
        val kept = rosters.add(team.id!!, game, season.id!!, "kept", TeamRole.PLAYER, null, null)
        val gone = rosters.add(team.id!!, game, season.id!!, "gone", TeamRole.PLAYER, null, null)

        publish(
            season,
            """
            {"teamId":${team.id},"name":"BS Renamed ${System.nanoTime()}","game":"$game","removed":[${gone.id}],"entries":[
              {"handle":"new","role":"COACH"},
              {"id":${kept.id},"handle":"kept-renamed","role":"PLAYER"}
            ]}
            """.trimIndent(),
        ).andExpect(status().isOk)

        assertThat(handles(team, season)).containsExactly("new", "kept-renamed")
        assertThat(teams.findById(team.id!!).orElseThrow().name).startsWith("BS Renamed")
    }

    @Test
    fun `a refusal part-way leaves the team and its line-up as they were`() {
        val season = season()
        val team = team()
        val before = team.name
        val kept = rosters.add(team.id!!, game, season.id!!, "kept", TeamRole.PLAYER, null, null)
        val gone = rosters.add(team.id!!, game, season.id!!, "gone", TeamRole.PLAYER, null, null)

        // The rename, the removal and the first edit all come before the entry that is not there.
        publish(
            season,
            """
            {"teamId":${team.id},"name":"BS Half ${System.nanoTime()}","game":"$game","removed":[${gone.id}],"entries":[
              {"id":${kept.id},"handle":"kept-edited","role":"PLAYER"},
              {"id":999999999,"handle":"missing","role":"PLAYER"}
            ]}
            """.trimIndent(),
        ).andExpect(status().isNotFound)

        assertThat(teams.findById(team.id!!).orElseThrow().name).isEqualTo(before)
        assertThat(handles(team, season)).containsExactly("kept", "gone")
    }

    @Test
    fun `a member may not save a line-up`() {
        val season = season()
        mvc
            .perform(
                put("/esports/seasons/{seasonId}/lineup", season.id)
                    .with(signedIn(createUserWithRole(Role.MEMBER)))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"name":"BS Nope","game":"$game"}"""),
            ).andExpect(status().isForbidden)
    }
}
