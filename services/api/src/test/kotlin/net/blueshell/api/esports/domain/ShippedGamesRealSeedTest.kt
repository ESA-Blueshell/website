package net.blueshell.api.esports.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * What the association ships as its games, asserted against the file that says so.
 *
 * These claims used to sit in the integration tests, which read the rows Flyway wrote and
 * asserted on them directly — so adding a game to `games.csv` failed a test about foreign keys
 * or permissions, which had nothing to say about which games exist (#1068).
 *
 * They belong here instead: reading the seed needs no Spring, no database and no api, and a
 * failure names the file to edit. What the api does *with* a game is asserted where the api is.
 */
class ShippedGamesRealSeedTest {

    private val games = EsportsSeed.files.rows("games.csv")

    private fun game(code: String): Map<String, String> =
        games.singleOrNull { it["code"] == code } ?: error("The shipped seed lists no game $code")

    @Test
    fun `every shipped game is answered for, in the order they are shown`() {
        assertThat(games).describedAs("the games the site ships").isNotEmpty()
        assertThat(games.map { it["code"] })
            .describedAs("games.csv, in the order their sort_index puts them")
            .isEqualTo(games.sortedBy { it["sort_index"]?.toIntOrNull() ?: 0 }.map { it["code"] })
    }

    @Test
    fun `the addresses are the ones already answered to`() {
        // Every link anybody has ever shared keeps working, so these are not free to change.
        assertThat(game("CS2")["slug"]).isEqualTo("counter-strike-2")
        assertThat(game("ROCKET_LEAGUE")["slug"]).isEqualTo("rocketleague")
        assertThat(game("LEAGUE_OF_LEGENDS")["slug"]).isEqualTo("league-of-legends")
        assertThat(game("CSGO")["slug"]).isEqualTo("counter-strike-global-offensive")
    }

    @Test
    fun `no two shipped games answer to the same address`() {
        val slugs = games.mapNotNull { it["slug"] }
        assertThat(slugs).doesNotHaveDuplicates()
        assertThat(slugs).describedAs("the index's own address is not a game's").doesNotContain("competitive-scene")
    }

    @Test
    fun `a shipped game carries the name a reader sees`() {
        assertThat(game("LEAGUE_OF_LEGENDS")["name"]).isEqualTo("League of Legends")
        assertThat(game("CSGO")["name"]).isEqualTo("CS:GO")
        assertThat(game("SMASH")["name"]).isEqualTo("Super Smash Bros.")
        assertThat(games.map { it["name"] }).describedAs("a game nobody named").doesNotContainNull()
    }

    @Test
    fun `a game that was never routed to has an address and something to say`() {
        // Trackmania had a component with copy written for it and nothing routing to it.
        assertThat(game("TRACKMANIA")["slug"]).isEqualTo("trackmania")
        assertThat(game("TRACKMANIA")["intro"]).isNotBlank()
    }

    @Test
    fun `a game nobody has drawn is shipped without a colour rather than with a made-up one`() {
        // The island reads such a game on the association's own colour; it does not go missing.
        assertThat(game("VALORANT")["accent"]).isEqualTo("#ff4655")
        assertThat(game("TRACKMANIA")["accent"]).isEmpty()
    }
}
