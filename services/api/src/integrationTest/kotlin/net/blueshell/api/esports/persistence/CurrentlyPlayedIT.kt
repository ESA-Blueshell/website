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
 * The most recent season answers, and only where nothing is fielded in it yet does the season
 * before it answer instead. That fallback is the whole point: a season is set up a game at a
 * time, so a rule that followed the newest season unconditionally would empty the list at every
 * changeover and refill it as the board worked — a half-finished season, in public, twice a year.
 *
 * It replaced a permanent union of both seasons, whose cost was a game the association had
 * stopped playing staying listed for one more season. The fallback keeps the protection and
 * drops the cost: it stops contributing the moment one team is fielded in the newest season.
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
    fun `only the games fielded in the most recent season are current`() {
        val last = season(LocalDate.of(2040, 2, 1))
        fieldOne("VALORANT", last)
        fieldOne("CS2", last)
        fieldOne("LEAGUE_OF_LEGENDS", last)
        val now = season(LocalDate.of(2040, 9, 1))
        fieldOne("VALORANT", now)

        // The season that is running answers. CS2 and League were last season's, and the site
        // says what the association plays now rather than what it played six months ago.
        assertThat(fielded.currentlyPlayed(inside)).containsExactly("VALORANT")
    }

    @Test
    fun `a season with nothing fielded in it yet falls back to the one before`() {
        val last = season(LocalDate.of(2040, 2, 1))
        fieldOne("VALORANT", last)
        fieldOne("CS2", last)
        // Made but not filled in: the board has entered no teams yet.
        season(LocalDate.of(2040, 9, 1))

        // The failure the fallback exists to prevent: an empty list, in public, at changeover.
        assertThat(fielded.currentlyPlayed(inside)).contains("VALORANT", "CS2")
    }

    @Test
    fun `the season before stops answering as soon as one team is fielded in the newest`() {
        val last = season(LocalDate.of(2040, 2, 1))
        fieldOne("CS2", last)
        val now = season(LocalDate.of(2040, 9, 1))

        // While the new season is empty, last season carries the list.
        assertThat(fielded.currentlyPlayed(inside)).contains("CS2")

        fieldOne("VALORANT", now)

        // One team is enough: the fallback is conditional, not a union.
        assertThat(fielded.currentlyPlayed(inside)).containsExactly("VALORANT")
    }

    @Test
    fun `a game dropped this season is no longer current`() {
        val older = season(LocalDate.of(2040, 2, 1))
        fieldOne("TRACKMANIA", older)
        val now = season(LocalDate.of(2040, 9, 1))
        fieldOne("VALORANT", now)

        // It was played six months ago and is not played now, so the site stops saying it is.
        assertThat(fielded.currentlyPlayed(inside)).doesNotContain("TRACKMANIA")
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
        // association plays. On 15 August the newer season has not started, so the older one is
        // the one we are in for this purpose.
        assertThat(fielded.currentlyPlayed(LocalDate.of(2040, 8, 15))).containsExactly("CS2")
    }

    /**
     * The case that is live rather than hypothetical, on the dates that make it live.
     *
     * The seeded history's last season is Spring 2025/26, running to 31 August 2026, and nothing
     * follows it — so from 1 September 2026 every day is a day in no season and the fallback to
     * the most recent season that started is the only thing keeping the pages answering. The
     * seeded rows themselves cannot be leaned on here: the suite wipes `season` and
     * `team_season` between cases, so the season is rebuilt on its own dates.
     */
    @Test
    fun `a date after every season falls back to the last one that started`() {
        val springOf2025 = seasons.save(
            Season(
                name = "Spring 2025/26 ${System.nanoTime()}",
                startDate = LocalDate.of(2026, 2, 1),
                endDate = LocalDate.of(2026, 8, 31),
            ),
        )
        // The six games that season fields in `roster.csv`.
        listOf("CS2", "GEOGUESSR", "LEAGUE_OF_LEGENDS", "ROCKET_LEAGUE", "TRACKMANIA", "VALORANT")
            .forEach { fieldOne(it, springOf2025) }

        // The last day it covers, and the first day it does not.
        assertThat(fielded.currentlyPlayed(LocalDate.of(2026, 8, 31)))
            .containsExactlyInAnyOrder(
                "CS2", "GEOGUESSR", "LEAGUE_OF_LEGENDS", "ROCKET_LEAGUE", "TRACKMANIA", "VALORANT",
            )
        assertThat(fielded.currentlyPlayed(LocalDate.of(2026, 9, 1)))
            .containsExactlyInAnyOrder(
                "CS2", "GEOGUESSR", "LEAGUE_OF_LEGENDS", "ROCKET_LEAGUE", "TRACKMANIA", "VALORANT",
            )
    }

    /**
     * The shape the seeded history is in, and the difference this change makes to it.
     *
     * Spring 2025/26 fields six games in `roster.csv`, which is what both the union and this rule
     * answer today — so nothing on screen moves the day this ships. What moves is the case below:
     * a seventh game fielded only in the season before, which the union would still be listing.
     * That case is constructed rather than seeded — CS:GO was last fielded in Spring 2022/23 —
     * because the point is the rule, and the suite wipes the seeded fieldings anyway.
     */
    @Test
    fun `a game fielded only in the season before is dropped, where the union kept it`() {
        val autumn = season(LocalDate.of(2025, 9, 1))
        fieldOne("CSGO", autumn)
        val spring = seasons.save(
            Season(
                name = "Spring 2025/26 ${System.nanoTime()}",
                startDate = LocalDate.of(2026, 2, 1),
                endDate = LocalDate.of(2026, 8, 31),
            ),
        )
        listOf("CS2", "GEOGUESSR", "LEAGUE_OF_LEGENDS", "ROCKET_LEAGUE", "TRACKMANIA", "VALORANT")
            .forEach { fieldOne(it, spring) }

        // Six, not seven: CS:GO was fielded last season and the union would still be listing it.
        assertThat(fielded.currentlyPlayed(LocalDate.of(2026, 6, 1)))
            .containsExactlyInAnyOrder(
                "CS2", "GEOGUESSR", "LEAGUE_OF_LEGENDS", "ROCKET_LEAGUE", "TRACKMANIA", "VALORANT",
            )
    }

    @Test
    fun `a date before any season has started answers nothing`() {
        val now = season(LocalDate.of(2040, 9, 1))
        fieldOne("VALORANT", now)

        assertThat(fielded.currentlyPlayed(LocalDate.of(1990, 1, 1))).isEmpty()
    }

    @Test
    fun `two seasons with nothing fielded in either answer nothing`() {
        season(LocalDate.of(2040, 2, 1))
        season(LocalDate.of(2040, 9, 1))

        // The fallback goes back one season, not through the whole archive.
        assertThat(fielded.currentlyPlayed(inside)).isEmpty()
    }
}
