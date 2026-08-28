package net.blueshell.api.esports.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class BannerResolutionTest {

    /** A banner stripped to what the resolution reads: how narrowly it was set, and a name to assert on. */
    private data class Banner(
        override val seasonId: Long?,
        override val teamId: Long?,
        val name: String,
    ) : BannerLevel

    private val gameWide = Banner(seasonId = null, teamId = null, name = "game")
    private val season5 = Banner(seasonId = 5, teamId = null, name = "season-5")
    private val teamA = Banner(seasonId = null, teamId = 1, name = "team-a")
    private val teamAInSeason5 = Banner(seasonId = 5, teamId = 1, name = "team-a-season-5")

    @Test
    fun `nothing set anywhere resolves to nothing`() {
        assertThat(mostSpecificBanner(emptyList<Banner>(), seasonId = 5, teamId = 1)).isNull()
    }

    @Test
    fun `a banner set for the game alone carries every team and season`() {
        assertThat(mostSpecificBanner(listOf(gameWide), seasonId = 5, teamId = 1)).isEqualTo(gameWide)
    }

    @Test
    fun `a season's banner beats the game's`() {
        assertThat(mostSpecificBanner(listOf(gameWide, season5), seasonId = 5, teamId = 1)).isEqualTo(season5)
    }

    @Test
    fun `a team's banner beats the season's`() {
        val found = mostSpecificBanner(listOf(gameWide, season5, teamA), seasonId = 5, teamId = 1)
        assertThat(found).isEqualTo(teamA)
    }

    @Test
    fun `a banner set for a team in a season beats the team's own`() {
        val found = mostSpecificBanner(listOf(gameWide, season5, teamA, teamAInSeason5), seasonId = 5, teamId = 1)
        assertThat(found).isEqualTo(teamAInSeason5)
    }

    /**
     * The case a specificity count alone gets wrong: the team matches, so a naive ranking
     * picks the narrowest banner mentioning it, in a season it was not set for.
     */
    @Test
    fun `a team's banner for one season does not carry into another`() {
        val found = mostSpecificBanner(listOf(gameWide, teamA, teamAInSeason5), seasonId = 6, teamId = 1)
        assertThat(found).isEqualTo(teamA)
    }

    @Test
    fun `another team's banner is never used`() {
        assertThat(mostSpecificBanner(listOf(gameWide, teamA), seasonId = 5, teamId = 2)).isEqualTo(gameWide)
    }

    @Test
    fun `another season's banner is never used`() {
        assertThat(mostSpecificBanner(listOf(gameWide, season5), seasonId = 6, teamId = 1)).isEqualTo(gameWide)
    }

    @Test
    fun `asking about no team in particular ignores the banners set for one`() {
        val found = mostSpecificBanner(listOf(gameWide, season5, teamA, teamAInSeason5), seasonId = 5, teamId = null)
        assertThat(found).isEqualTo(season5)
    }

    @Test
    fun `asking about no season in particular ignores the banners set for one`() {
        val found = mostSpecificBanner(listOf(gameWide, season5, teamA, teamAInSeason5), seasonId = null, teamId = 1)
        assertThat(found).isEqualTo(teamA)
    }
}
