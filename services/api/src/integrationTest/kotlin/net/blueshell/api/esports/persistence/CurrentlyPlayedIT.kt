package net.blueshell.api.esports.persistence

import net.blueshell.api.esports.domain.TeamSeasonService
import net.blueshell.api.testsupport.UserTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.time.LocalDate

// The suite wipes `season` and `team_season`, so every case builds its own.
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

        assertThat(fielded.currentlyPlayed(inside)).containsExactly("VALORANT")
    }

    @Test
    fun `a season with nothing fielded in it yet falls back to the one before`() {
        val last = season(LocalDate.of(2040, 2, 1))
        fieldOne("VALORANT", last)
        fieldOne("CS2", last)
        season(LocalDate.of(2040, 9, 1))

        assertThat(fielded.currentlyPlayed(inside)).contains("VALORANT", "CS2")
    }

    @Test
    fun `the season before stops answering as soon as one team is fielded in the newest`() {
        val last = season(LocalDate.of(2040, 2, 1))
        fieldOne("CS2", last)
        val now = season(LocalDate.of(2040, 9, 1))

        assertThat(fielded.currentlyPlayed(inside)).contains("CS2")

        fieldOne("VALORANT", now)

        assertThat(fielded.currentlyPlayed(inside)).containsExactly("VALORANT")
    }

    @Test
    fun `a game dropped this season is no longer current`() {
        val older = season(LocalDate.of(2040, 2, 1))
        fieldOne("TRACKMANIA", older)
        val now = season(LocalDate.of(2040, 9, 1))
        fieldOne("VALORANT", now)

        assertThat(fielded.currentlyPlayed(inside)).doesNotContain("TRACKMANIA")
    }

    @Test
    fun `a game entered but not yet fielded is not current`() {
        val now = season(LocalDate.of(2040, 9, 1))
        fieldOne("VALORANT", now)

        assertThat(fielded.currentlyPlayed(inside)).doesNotContain("GEOGUESSR")
    }

    @Test
    fun `a date in no season at all falls back to the most recent one that started`() {
        val older = season(LocalDate.of(2040, 2, 1))
        fieldOne("CS2", older)
        val now = season(LocalDate.of(2040, 9, 1))
        fieldOne("VALORANT", now)

        assertThat(fielded.currentlyPlayed(LocalDate.of(2040, 8, 15))).containsExactly("CS2")
    }

    @Test
    // Live from 1 September 2026: the seed's last season ends 31 August 2026 with nothing after.
    fun `a date after every season falls back to the last one that started`() {
        val springOf2025 = seasons.save(
            Season(
                name = "Spring 2025/26 ${System.nanoTime()}",
                startDate = LocalDate.of(2026, 2, 1),
                endDate = LocalDate.of(2026, 8, 31),
            ),
        )
        listOf("CS2", "GEOGUESSR", "LEAGUE_OF_LEGENDS", "ROCKET_LEAGUE", "TRACKMANIA", "VALORANT")
            .forEach { fieldOne(it, springOf2025) }

        assertThat(fielded.currentlyPlayed(LocalDate.of(2026, 8, 31)))
            .containsExactlyInAnyOrder(
                "CS2", "GEOGUESSR", "LEAGUE_OF_LEGENDS", "ROCKET_LEAGUE", "TRACKMANIA", "VALORANT",
            )
        assertThat(fielded.currentlyPlayed(LocalDate.of(2026, 9, 1)))
            .containsExactlyInAnyOrder(
                "CS2", "GEOGUESSR", "LEAGUE_OF_LEGENDS", "ROCKET_LEAGUE", "TRACKMANIA", "VALORANT",
            )
    }

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

        assertThat(fielded.currentlyPlayed(inside)).isEmpty()
    }
}
