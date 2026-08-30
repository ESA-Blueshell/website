package net.blueshell.api.esports.persistence

import net.blueshell.api.esports.domain.TeamSeasonService
import net.blueshell.api.testsupport.UserTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.time.LocalDate

/**
 * Which games the association currently plays, derived from the seasons rather than declared.
 *
 * The union of the season we are in and the one before it is the whole point of this, and the
 * reason is the changeover: a season is set up a game at a time, so a list that followed only the
 * newest season would collapse to whichever game the board entered first and refill as they
 * worked. That is a half-finished season, in public, twice a year. The cost is a game the
 * association has stopped playing lingering for one more season, which is asserted here too so
 * that nobody mistakes it for a defect later.
 */
@SpringBootTest
class CurrentlyPlayedIT : UserTestSupport() {
    @Autowired private lateinit var fielded: TeamSeasonService

    @Autowired private lateinit var seasons: SeasonRepository

    @Autowired private lateinit var teams: TeamRepository

    private var counter = 0

    private fun season(from: LocalDate): Season {
        counter += 1
        return seasons.save(
            Season(name = "Season $counter ${System.nanoTime()}", startDate = from, endDate = from.plusMonths(5)),
        )
    }

    private fun fieldOne(game: String, season: Season) {
        val team = teams.save(Team(name = "Team ${System.nanoTime()}"))
        fielded.field(team.id!!, game, season.id!!)
    }

    /** A date inside the newer of two seasons this suite makes. */
    private val inside = LocalDate.of(2040, 10, 1)

    @Test
    fun `a season part-way through being set up does not shrink the list`() {
        val last = season(LocalDate.of(2040, 2, 1))
        fieldOne("VALORANT", last)
        fieldOne("CS2", last)
        fieldOne("LEAGUE_OF_LEGENDS", last)
        val now = season(LocalDate.of(2040, 9, 1))

        // The board has entered one game of the new season so far.
        fieldOne("VALORANT", now)

        // The failure this rule exists to prevent: following the newest season alone would answer
        // with Valorant and nothing else, and the site would say the association plays one game.
        assertThat(fielded.currentlyPlayed(inside))
            .contains("VALORANT", "CS2", "LEAGUE_OF_LEGENDS")
    }

    @Test
    fun `a game dropped this season is still current, and leaves the season after`() {
        val older = season(LocalDate.of(2040, 2, 1))
        fieldOne("TRACKMANIA", older)
        val now = season(LocalDate.of(2040, 9, 1))
        fieldOne("VALORANT", now)

        // The accepted cost, stated out loud: it was played six months ago, so it still counts.
        assertThat(fielded.currentlyPlayed(inside)).contains("TRACKMANIA")

        val next = season(LocalDate.of(2041, 2, 1))
        fieldOne("VALORANT", next)

        // A season later it is two seasons back, and it goes.
        assertThat(fielded.currentlyPlayed(LocalDate.of(2041, 3, 1))).doesNotContain("TRACKMANIA")
    }

    @Test
    fun `a game entered but not yet fielded is not current`() {
        val now = season(LocalDate.of(2040, 9, 1))
        fieldOne("VALORANT", now)

        // Entering a game is the board saying it intends to play it; a team playing it is what
        // makes it public, and this answers the same question the pages answer.
        assertThat(fielded.currentlyPlayed(inside)).doesNotContain("GEOGUESSR")
    }

    @Test
    fun `a date in no season at all falls back to the most recent one that started`() {
        val older = season(LocalDate.of(2040, 2, 1))
        fieldOne("CS2", older)
        val now = season(LocalDate.of(2040, 9, 1))
        fieldOne("VALORANT", now)

        // Seasons do not tile the calendar, and a gap between them is not a gap in what the
        // association plays.
        assertThat(fielded.currentlyPlayed(LocalDate.of(2040, 8, 15)))
            .contains("CS2")
    }
}
