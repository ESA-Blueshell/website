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
 * Fielding a team and naming its players are two decisions taken weeks apart, and until the
 * link between a team and a season was its own fact, the first could not be recorded without
 * the second.
 */
@SpringBootTest
class TeamFieldedInSeasonIT : UserTestSupport() {
    @Autowired private lateinit var fielded: TeamSeasonService

    @Autowired private lateinit var rosters: TeamRosterService

    @Autowired private lateinit var page: EsportsPageQueryService

    @Autowired private lateinit var seasons: SeasonRepository

    @Autowired private lateinit var teams: TeamRepository

    private var counter = 0

    private fun season(from: LocalDate = LocalDate.of(2030, 9, 1)): Season {
        counter += 1
        return seasons.save(
            Season(name = "Season $counter ${System.nanoTime()}", startDate = from, endDate = from.plusMonths(5)),
        )
    }

    private fun team(name: String = "Team ${System.nanoTime()}"): Team =
        teams.save(Team(game = "TRACKMANIA", name = name))

    @Test
    fun `a team can be fielded before anybody is named to it, and shows with an empty roster`() {
        val season = season()
        val team = team("BS Nobody Yet")

        fielded.field(team.id!!, season.id!!)

        val view = page.page("TRACKMANIA", season.id)
        assertThat(view.teams).extracting<String> { it.name }.contains("BS Nobody Yet")
        assertThat(view.teams.single { it.name == "BS Nobody Yet" }.members).isEmpty()
    }

    @Test
    fun `naming somebody to a team in a season fields it there`() {
        val season = season()
        val team = team()

        rosters.add(team.id!!, season.id!!, "Handle", TeamRole.PLAYER, null, null)

        // Nobody said the team was being fielded; putting a player on it said it for them.
        assertThat(fielded.isFielded(team.id!!, season.id!!)).isTrue()
    }

    @Test
    fun `fielding a team twice says the same thing rather than saying it twice`() {
        val season = season()
        val team = team()

        val first = fielded.field(team.id!!, season.id!!)
        val again = fielded.field(team.id!!, season.id!!)

        assertThat(again.id).isEqualTo(first.id)
        assertThat(page.page("TRACKMANIA", season.id).teams).hasSize(1)
    }

    @Test
    fun `a team fielded in one season is absent from another`() {
        val played = season(LocalDate.of(2030, 9, 1))
        val other = season(LocalDate.of(2031, 9, 1))
        val team = team("BS One Season")
        fielded.field(team.id!!, played.id!!)
        // A team of its own, so the other season is one this game genuinely played in.
        fielded.field(team("BS The Other Lot").id!!, other.id!!)

        assertThat(page.page("TRACKMANIA", played.id).teams).extracting<String> { it.name }
            .contains("BS One Season")
        assertThat(page.page("TRACKMANIA", other.id).teams).extracting<String> { it.name }
            .doesNotContain("BS One Season")
    }

    @Test
    fun `a season asked for by name is answered about, even where this game fielded nobody`() {
        val played = season(LocalDate.of(2030, 9, 1))
        fielded.field(team("BS Somebody").id!!, played.id!!)
        val empty = season(LocalDate.of(2032, 9, 1))

        val view = page.page("TRACKMANIA", empty.id)

        // The answer for a season with no teams is that it had none, not another season's.
        assertThat(view.season?.id).isEqualTo(empty.id)
        assertThat(view.teams).isEmpty()
        // It is still not offered to a visitor, who has nothing to do with an empty season.
        assertThat(view.seasons).extracting<Long> { it.id }.doesNotContain(empty.id)
    }

    @Test
    fun `dropping a team from a season leaves the team and its other seasons alone`() {
        val kept = season(LocalDate.of(2030, 9, 1))
        val dropped = season(LocalDate.of(2031, 9, 1))
        val team = team("BS Two Seasons")
        fielded.field(team.id!!, kept.id!!)
        fielded.field(team.id!!, dropped.id!!)

        fielded.unfield(team.id!!, dropped.id!!)

        assertThat(fielded.isFielded(team.id!!, dropped.id!!)).isFalse()
        assertThat(fielded.isFielded(team.id!!, kept.id!!)).isTrue()
        assertThat(teams.findById(team.id!!)).isPresent()
    }

    @Test
    fun `a team fielded again in a season it was dropped from brings its line-up back`() {
        val season = season()
        val team = team("BS Dropped And Restored")
        rosters.add(team.id!!, season.id!!, "returns", TeamRole.PLAYER, null, null)
        fielded.unfield(team.id!!, season.id!!)

        fielded.field(team.id!!, season.id!!)

        // The line-up hangs off the fielding, so a second fielding would leave it attached to
        // the dropped one: present in the table, reachable by nothing, and silently gone from
        // the season it was played in.
        assertThat(rosters.findByTeamAndSeason(team.id!!, season.id!!).map { it.handle })
            .containsExactly("returns")
    }

    @Test
    fun `a season only offers itself once the game has a team in it`() {
        val season = season()
        val before = page.page("TRACKMANIA").seasons.map { it.id }
        assertThat(before).doesNotContain(season.id)

        fielded.field(team().id!!, season.id!!)

        assertThat(page.page("TRACKMANIA").seasons.map { it.id }).contains(season.id)
    }

    @Test
    fun `the history that was already recorded is fielded, without anybody saying so`() {
        // The migration wrote across every link the roster entries implied, so a page that
        // rendered before the change renders the same after it.
        val season = season()
        val team = team("BS Carried Across")
        rosters.add(team.id!!, season.id!!, "Handle", TeamRole.PLAYER, null, null)

        val view = page.page("TRACKMANIA", season.id)

        assertThat(view.teams).extracting<String> { it.name }.contains("BS Carried Across")
        assertThat(view.teams.single { it.name == "BS Carried Across" }.members).hasSize(1)
    }
}
