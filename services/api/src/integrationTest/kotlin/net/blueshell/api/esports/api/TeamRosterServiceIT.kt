package net.blueshell.api.esports.api

import net.blueshell.api.esports.domain.TeamSeasonService
import net.blueshell.api.esports.persistence.Season
import net.blueshell.api.esports.persistence.Team
import net.blueshell.api.esports.persistence.TeamRosterEntry
import net.blueshell.api.esports.persistence.SeasonRepository
import net.blueshell.api.esports.persistence.TeamRepository
import net.blueshell.api.esports.persistence.TeamRosterEntryRepository
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.shared.enums.TeamRole
import net.blueshell.api.testsupport.UserTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.time.LocalDate

/**
 * "Was this member active through play in that stretch of time" — the question the cohort
 * engine will ask of a roster, and the reason a season carries its own dates.
 */
@SpringBootTest
class TeamRosterServiceIT : UserTestSupport() {
    @Autowired
    private lateinit var rosters: TeamRosterService

    @Autowired
    private lateinit var seasons: SeasonRepository

    @Autowired
    private lateinit var teams: TeamRepository

    @Autowired
    private lateinit var entries: TeamRosterEntryRepository

    @Autowired
    private lateinit var fielded: TeamSeasonService

    private fun season(from: LocalDate, to: LocalDate): Season =
        seasons.save(Season(name = "Season ${System.nanoTime()}", startDate = from, endDate = to))

    private fun rosterEntry(season: Season, userId: Long?, handle: String = "handle${System.nanoTime()}"): TeamRosterEntry {
        val team = teams.save(Team(name = "Team ${System.nanoTime()}"))
        return entries.save(
            TeamRosterEntry(
                teamSeason = fielded.field(team.id!!, "VALORANT", season.id!!),
                handle = handle,
                teamRole = TeamRole.PLAYER,
                userId = userId,
            ),
        )
    }

    @Test
    fun `a member who played a season overlapping the window was active in it`() {
        val member = createUserWithRole(Role.MEMBER)
        // Runs from the autumn into the new year, so it straddles a period boundary.
        rosterEntry(season(LocalDate.of(2025, 9, 1), LocalDate.of(2026, 1, 31)), member.id)

        assertThat(rosters.playedBetween(member.id!!, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)))
            .isTrue()
    }

    @Test
    fun `a season entirely outside the window does not count`() {
        val member = createUserWithRole(Role.MEMBER)
        rosterEntry(season(LocalDate.of(2021, 9, 1), LocalDate.of(2022, 1, 31)), member.id)

        assertThat(rosters.playedBetween(member.id!!, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)))
            .isFalse()
    }

    @Test
    fun `an entry nobody is linked to contributes nobody`() {
        val playing = season(LocalDate.of(2026, 2, 1), LocalDate.of(2026, 8, 31))
        rosterEntry(playing, userId = null, handle = "unattributed${System.nanoTime()}")

        val players = rosters.playersBetween(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31))

        assertThat(players).doesNotContainNull()
    }

    @Test
    fun `the same team fields a different roster each season`() {
        val team = teams.save(Team(name = "Team ${System.nanoTime()}"))
        val first = season(LocalDate.of(2024, 9, 1), LocalDate.of(2025, 1, 31))
        val second = season(LocalDate.of(2025, 9, 1), LocalDate.of(2026, 1, 31))
        entries.save(TeamRosterEntry(teamSeason = fielded.field(team.id!!, "CS2", first.id!!), handle = "veteran"))
        entries.save(TeamRosterEntry(teamSeason = fielded.field(team.id!!, "CS2", second.id!!), handle = "newcomer"))

        assertThat(rosters.findByTeamAndSeason(team.id!!, "CS2", first.id!!).map { it.handle })
            .containsExactly("veteran")
        assertThat(rosters.findByTeamAndSeason(team.id!!, "CS2", second.id!!).map { it.handle })
            .containsExactly("newcomer")
    }
}
