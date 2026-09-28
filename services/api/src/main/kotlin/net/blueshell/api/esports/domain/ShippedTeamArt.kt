package net.blueshell.api.esports.domain

import net.blueshell.api.esports.persistence.TeamRepository
import net.blueshell.api.esports.persistence.TeamSeasonRepository
import net.blueshell.api.file.api.ShippedPictures
import net.blueshell.api.shared.enums.FileType
import net.blueshell.api.shared.seed.SeedCsv
import net.blueshell.api.shared.seed.SeedOrder
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component

/**
 * Puts the poster `teams.csv` names on each team, in each game it played, that has none.
 *
 * The art belongs to the fielding, not the team, since one team plays several games and is
 * drawn with each game's art. The file names it once per team per game and every season of
 * that pairing takes it, so a season added later still gets the art. A team the file names and
 * the database does not is skipped: the seed leaves a removed team removed. Only a team's
 * banner ships; it gains an icon when somebody uploads one.
 */
@Component
class ShippedTeamArt(
    private val pictures: ShippedPictures,
    private val teams: TeamRepository,
    private val fielded: TeamSeasonRepository,
    /** Spring has no bean for this, so the shipped seed is the default. Tests pass their own. */
    private val seed: SeedCsv = EsportsSeed.files,
) {
    @Order(SeedOrder.ART)
    @EventListener(ApplicationReadyEvent::class)
    fun onReady() {
        apply()
    }

    /** How many team-and-game pairings took a poster, which is none on every start after the first. */
    fun apply(): Int =
        pictures.ship(seed, "teams.csv", "banner", FileType.TEAM_BANNER) { row, picture ->
            val team = teams.findByNameIgnoreCase(row.getValue("name")) ?: return@ship false
            val bare = fielded.findAllByTeamId(team.id!!).filter { it.game == row.getValue("game") && it.banner == null }
            if (bare.isEmpty()) return@ship false
            val poster = picture()
            bare.forEach { fielding ->
                fielding.banner = poster
                fielded.save(fielding)
            }
            true
        }
}
