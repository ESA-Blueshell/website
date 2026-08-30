package net.blueshell.api.esports.persistence

import net.blueshell.api.shared.enums.TeamRole
import net.blueshell.api.testsupport.UserTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.time.LocalDate
import net.blueshell.api.esports.api.TeamRosterService
import net.blueshell.api.esports.domain.EsportsPageQueryService
import net.blueshell.api.esports.domain.TeamSeasonService

/**
 * Taking a season away hides everything recorded against it, so the offer to do so has to say
 * how much that is before it is taken up.
 */
@SpringBootTest
class SeasonContentsIT : UserTestSupport() {
    /** These fixtures all play one game; the fielding names it now. */
    private val GAME = "TRACKMANIA"

    @Autowired private lateinit var fielded: TeamSeasonService

    @Autowired private lateinit var rosters: TeamRosterService

    @Autowired private lateinit var seasons: SeasonRepository

    @Autowired private lateinit var teams: TeamRepository

    private var year = 2060

    private fun season(): Season {
        year += 1
        return seasons.save(
            Season(
                name = "Season $year ${System.nanoTime()}",
                startDate = LocalDate.of(year, 9, 1),
                endDate = LocalDate.of(year + 1, 1, 31),
            ),
        )
    }

    private fun team(): Team = teams.save(Team(name = "BS Count ${System.nanoTime()}"))

    @Test
    fun `a season says how many teams and players it holds`() {
        val season = season()
        val first = team()
        val second = team()
        rosters.add(first.id!!, GAME, season.id!!, "one", TeamRole.PLAYER, null, null)
        rosters.add(first.id!!, GAME, season.id!!, "two", TeamRole.SUBSTITUTE, null, null)
        rosters.add(second.id!!, GAME, season.id!!, "three", TeamRole.PLAYER, null, null)

        val (teamCount, playerCount) = fielded.contentsOf(season.id!!)

        assertThat(teamCount).isEqualTo(2)
        assertThat(playerCount).isEqualTo(3)
    }

    @Test
    fun `a season nothing was recorded against holds nothing`() {
        val season = season()

        assertThat(fielded.contentsOf(season.id!!)).isEqualTo(0L to 0L)
    }

    @Test
    fun `a team dropped from a season is no longer counted in it`() {
        val season = season()
        val team = team()
        fielded.field(team.id!!, GAME, season.id!!)

        fielded.unfield(team.id!!, GAME, season.id!!)

        assertThat(fielded.contentsOf(season.id!!).first).isEqualTo(0)
    }

    @Test
    fun `dropping a team from one season leaves the team and its other seasons alone`() {
        val kept = season()
        val dropped = season()
        val team = team()
        rosters.add(team.id!!, GAME, kept.id!!, "stays", TeamRole.PLAYER, null, null)
        rosters.add(team.id!!, GAME, dropped.id!!, "goes", TeamRole.PLAYER, null, null)

        fielded.unfield(team.id!!, GAME, dropped.id!!)

        // The team played the other season, and still did.
        assertThat(fielded.isFielded(team.id!!, GAME, kept.id!!)).isTrue()
        assertThat(teams.findById(team.id!!)).isPresent()
        assertThat(page(kept.id!!)).contains(team.name)
        assertThat(page(dropped.id!!)).doesNotContain(team.name)
    }

    @Autowired private lateinit var pages: EsportsPageQueryService

    private fun page(seasonId: Long) =
        pages.page("TRACKMANIA", seasonId).teams.map { it.name }
}
