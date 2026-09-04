package net.blueshell.api.esports.domain

import net.blueshell.api.esports.persistence.GameRepository
import net.blueshell.api.esports.persistence.TeamRepository
import net.blueshell.api.esports.persistence.TeamSeasonRepository
import net.blueshell.api.file.api.FileService
import net.blueshell.api.file.persistence.File
import net.blueshell.api.shared.enums.FileType
import net.blueshell.api.user.api.UserService
import net.blueshell.api.user.persistence.User
import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionTemplate

/**
 * Puts the art the repository ships onto the games and teams the seed files name.
 *
 * `db/seed/esports/art` holds the pictures; `teams.csv`, `banners.csv` and `icons.csv` say which
 * record each belongs to. Only games ship an icon — a team gains one when somebody uploads it.
 *
 * Runs on start rather than in the migration that loads the same files: storing a picture needs
 * the volume and the converter a migration runner lacks. A filled slot is never overwritten —
 * that choice is later than this one. Every picture is stored on every start even when nothing
 * is waiting for it, which is what lets a lost storage volume repair itself.
 *
 * @see net.blueshell.api.board.domain.ShippedBoardArt the board twin, which inherits these rules.
 */
@Component
class ShippedArt(
    private val files: FileService,
    private val users: UserService,
    private val teams: TeamRepository,
    private val fielded: TeamSeasonRepository,
    private val games: GameRepository,
    private val transactions: TransactionTemplate,
) {
    /** The pictures a run put on records, which is none at all on every start after the first. */
    data class Applied(val teamPictures: Int, val gamePictures: Int)

    fun apply(): Applied {
        val owner = siteAccount() ?: return Applied(0, 0)
        // A team's picture and a game's, each as the record it belongs to and the art it names.
        val teamArt = EsportsSeed.files.rows(TEAMS)
            .mapNotNull { row ->
                row[BANNER]?.ifBlank { null }?.let { art -> Triple(row.getValue("game"), row.getValue("name"), art) }
            }
        val gameArt = EsportsSeed.files.rows(BANNERS)
            .map { row -> row.getValue("game") to row.getValue(BANNER) }
        val gameIcons = EsportsSeed.files.rows(ICONS)
            .map { row -> row.getValue("game") to row.getValue(ICON) }

        // Every picture first, so one that is waiting for nothing is still put back where it
        // was. One picture may belong to two records — a game fields more teams than it has
        // art — and the addresses remembered here are what stops it being stored twice.
        val stored = mutableMapOf<Pair<String, FileType>, String>()
        teamArt.forEach { (_, team, art) ->
            attempt("the picture for $team") { store(art, FileType.TEAM_BANNER, owner, stored); 0 }
        }
        gameArt.forEach { (game, art) ->
            attempt("the picture for $game") { store(art, FileType.GAME_BANNER, owner, stored); 0 }
        }
        gameIcons.forEach { (game, art) ->
            attempt("the icon for $game") { store(art, FileType.GAME_ICON, owner, stored); 0 }
        }

        val teamsDrawn = teamArt.sumOf { (game, team, art) ->
            attempt("the banner of $team") { teamBanner(game, team, art, owner, stored) }
        }
        val gamesDrawn = gameArt.sumOf { (game, art) ->
            attempt("the banner of $game") { gameBanner(game, art, owner, stored) }
        } + gameIcons.sumOf { (game, art) ->
            attempt("the icon of $game") { gameIcon(game, art, owner, stored) }
        }

        if (teamsDrawn > 0 || gamesDrawn > 0) {
            log.info(
                "[shipped-art] {} team and {} game pictures now come from the art that ships",
                teamsDrawn,
                gamesDrawn,
            )
        }
        return Applied(teamPictures = teamsDrawn, gamePictures = gamesDrawn)
    }

    /**
     * The team's banner in one game, on every season it played that game without one.
     *
     * The art belongs to the fielding, not the team, since one team plays several games and is
     * drawn with each game's art. The file names it once per team per game and every season of
     * that pairing takes it, so a season added later still gets the art. A team the file names
     * and the database does not is not an error: the seed leaves a removed team removed.
     */
    private fun teamBanner(
        game: String,
        name: String,
        art: String,
        owner: User,
        stored: MutableMap<Pair<String, FileType>, String>,
    ): Int = transactions.execute {
        val team = teams.findByNameIgnoreCase(name) ?: return@execute 0
        val bare = fielded.findAllByTeamId(team.id!!).filter { it.game == game && it.banner == null }
        if (bare.isEmpty()) return@execute 0
        val picture = store(art, FileType.TEAM_BANNER, owner, stored)
        bare.forEach { fielding ->
            fielding.banner = picture
            fielded.save(fielding)
        }
        1
    }

    /** The game's own banner, where the game has none. */
    private fun gameBanner(
        game: String,
        art: String,
        owner: User,
        stored: MutableMap<Pair<String, FileType>, String>,
    ): Int = transactions.execute {
        val record = games.findByCode(game) ?: return@execute 0
        if (record.banner != null) return@execute 0
        record.banner = store(art, FileType.GAME_BANNER, owner, stored)
        games.save(record)
        1
    }

    /** The game's own icon, where the game has none. */
    private fun gameIcon(
        game: String,
        art: String,
        owner: User,
        stored: MutableMap<Pair<String, FileType>, String>,
    ): Int = transactions.execute {
        val record = games.findByCode(game) ?: return@execute 0
        if (record.icon != null) return@execute 0
        record.icon = store(art, FileType.GAME_ICON, owner, stored)
        games.save(record)
        1
    }

    /**
     * One picture, stored the first time it is asked for.
     *
     * What is remembered between records is the address rather than the row, because each of
     * them is written in a transaction of its own and a row read in one is stale in the next.
     * The address is stable — it is the picture's own contents — so the second team to ask for
     * a picture reads back the row the first one wrote instead of storing the bytes again.
     */
    private fun store(
        art: String,
        kind: FileType,
        owner: User,
        stored: MutableMap<Pair<String, FileType>, String>,
    ): File {
        stored[art to kind]?.let { path -> files.findPublicImage(path, kind)?.let { return it } }
        val name = "$art.webp"
        val resource = "${EsportsSeed.files.directory}/art/$name"
        val bytes = javaClass.classLoader.getResourceAsStream(resource)
            ?: error("Shipped art $resource is missing")
        val file = files.store(bytes, name, WEBP, kind, owner)
        stored[art to kind] = file.path
        return file
    }

    /**
     * The account the shipped art is credited to.
     *
     * Absent only where the migration that writes it has not run, which is to say never in a
     * running application. Answering with null rather than throwing keeps that impossible case
     * from being the reason a start fails.
     */
    private fun siteAccount(): User? {
        val account = runCatching { users.findByUsername(SITE_ACCOUNT) }.getOrNull()
        if (account == null) {
            log.warn("[shipped-art] there is no '{}' account to credit the art to", SITE_ACCOUNT)
        }
        return account
    }

    /** One record's art, whose failure is its own rather than the rest of the run's. */
    private fun attempt(what: String, apply: () -> Int): Int =
        try {
            apply()
        } catch (e: Exception) {
            log.warn("[shipped-art] could not set {}: {}", what, e.message)
            0
        }

    private companion object {
        val log = LoggerFactory.getLogger(ShippedArt::class.java)
        const val TEAMS = "teams.csv"
        const val BANNERS = "banners.csv"
        const val ICONS = "icons.csv"
        const val BANNER = "banner"
        const val ICON = "icon"
        const val WEBP = "image/webp"
        const val SITE_ACCOUNT = "system"
    }
}

/**
 * Applies the shipped art once the application is up, and never stops it coming up.
 *
 * A separate bean, so the work is reached through the proxy as the file module's backfills are.
 * A failure is reported and swallowed: art that did not land leaves a record pointing where it
 * pointed yesterday, and refusing to start over one would take the whole site down.
 */
@Component
class ShippedArtOnStartup(
    private val art: ShippedArt,
) {
    @EventListener(ApplicationReadyEvent::class)
    fun onReady() {
        try {
            art.apply()
        } catch (e: Exception) {
            log.warn("[shipped-art] could not put the art that ships onto the records: {}", e.message)
        }
    }

    private companion object {
        val log = LoggerFactory.getLogger(ShippedArtOnStartup::class.java)
    }
}
