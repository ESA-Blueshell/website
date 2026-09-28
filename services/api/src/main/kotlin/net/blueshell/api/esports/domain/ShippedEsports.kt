package net.blueshell.api.esports.domain

import net.blueshell.api.esports.persistence.Season
import net.blueshell.api.esports.persistence.SeasonGame
import net.blueshell.api.esports.persistence.SeasonGameRepository
import net.blueshell.api.esports.persistence.SeasonRepository
import net.blueshell.api.esports.persistence.Team
import net.blueshell.api.esports.persistence.TeamRepository
import net.blueshell.api.esports.persistence.TeamRosterEntry
import net.blueshell.api.esports.persistence.TeamRosterEntryRepository
import net.blueshell.api.esports.persistence.TeamSeason
import net.blueshell.api.esports.persistence.TeamSeasonRepository
import net.blueshell.api.esports.persistence.UserGameAccount
import net.blueshell.api.esports.persistence.UserGameAccountRepository
import net.blueshell.api.game.api.ShippedGame
import net.blueshell.api.game.api.ShippedGames
import net.blueshell.api.shared.enums.TeamRole
import net.blueshell.api.shared.seed.SeedCsv
import net.blueshell.api.shared.seed.SeedLedger
import net.blueshell.api.shared.seed.SeedOrder
import net.blueshell.api.user.api.UserService
import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.core.annotation.Order
import org.springframework.jdbc.datasource.DataSourceUtils
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionTemplate
import java.time.LocalDate
import javax.sql.DataSource

/** The repositories the esports seed writes through, gathered so the loader takes one of them. */
@Component
class EsportsSeedRecords(
    val games: ShippedGames,
    val seasons: SeasonRepository,
    val teams: TeamRepository,
    val fieldings: TeamSeasonRepository,
    val entered: SeasonGameRepository,
    val entries: TeamRosterEntryRepository,
    val accounts: UserGameAccountRepository,
    val users: UserService,
)

/**
 * Adds the esports history in `db/seed/esports` that the database has never had, and leaves
 * every row it has alone. Each row is written once, on the first start that finds it (see
 * [SeedLedger]), so an edit, rename or deletion made on the site outlives every later start.
 *
 * Rows are written through the repositories, and games through the game module's
 * [ShippedGames]. The ledger stays on the transaction's own connection.
 */
@Component
class ShippedEsports(
    private val records: EsportsSeedRecords,
    private val dataSource: DataSource,
    private val transactions: TransactionTemplate,
    private val seed: SeedCsv = EsportsSeed.files,
) {
    /** The rows a run wrote, which is none at all once the files have been applied. */
    data class Applied(
        val games: Int,
        val seasons: Int,
        val teams: Int,
        val entries: Int,
    )

    /** Loads [files], which tests point at a fixture set rather than the shipped one. */
    fun apply(files: SeedCsv = seed): Applied =
        transactions.execute {
            val connection = DataSourceUtils.getConnection(dataSource)
            try {
                load(files, SeedLedger(connection, SEED))
            } finally {
                DataSourceUtils.releaseConnection(connection, dataSource)
            }
        }

    private fun load(
        files: SeedCsv,
        ledger: SeedLedger,
    ): Applied {
        // The games come first: a team names one, and the database enforces that it exists.
        val games = files.rows("games.csv").count { row -> addGame(ledger, row) }
        val seasons = files.rows("seasons.csv")
        val seasonsAdded = seasons.count { row -> addSeason(ledger, row) }
        // By name alone: the file lists a team once per game it played, because the art is per
        // game, but those rows are one team.
        val teamNames = files.rows("teams.csv").map { row -> row.getValue("name") }.distinct()
        val teamsAdded = teamNames.count { name -> addTeam(ledger, name) }
        val standingSeasons = seasons.associate { row -> row.getValue("name").let { it to records.seasons.findByNameIgnoreCase(it) } }
        val standingTeams = teamNames.associateWith { name -> records.teams.findByNameIgnoreCase(name) }

        val entries =
            files.rows("roster.csv").count { row ->
                val team = standingTeams[row.getValue("team")]
                val season = standingSeasons[row.getValue("season")]
                // A team or season that is deleted or renamed leaves its line-up with nowhere to go.
                team != null && season != null && addEntry(ledger, team, season, row)
            }
        // Only where places were written: whoever was just attached to one is the only member
        // who can have a handle to take up.
        val handles = if (entries > 0) adoptHandlesPlayedUnder() else 0
        if (handles > 0) log.info("[esports-seed] {} members took up the handle they last played under", handles)
        val applied = Applied(games = games, seasons = seasonsAdded, teams = teamsAdded, entries = entries)
        if (applied != Applied(0, 0, 0, 0)) {
            log.info(
                "[esports-seed] {} games, {} seasons, {} teams and {} roster entries added",
                games,
                seasonsAdded,
                teamsAdded,
                entries,
            )
        }
        return applied
    }

    /**
     * A game, keyed on its code. Whether the association still fields it is derived from the
     * seasons; whether it is archived is the file's to say once, since the site edits it after.
     */
    private fun addGame(
        ledger: SeedLedger,
        row: Map<String, String>,
    ): Boolean {
        val code = row.getValue("code")
        val archived = row["archived"].toBoolean()
        // Its own key, so a game already standing when archiving shipped is archived once too.
        if (archived && ledger.toWrite("game-archived|$code") { false }) records.games.archive(code)
        if (!ledger.toWrite("game|$code") { records.games.everHeld(code) }) return false
        records.games.add(
            ShippedGame(
                code = code,
                name = row.getValue("name"),
                slug = row.getValue("slug"),
                accent = row.getValue("accent").ifBlank { null },
                sortIndex = row.getValue("sort_index").toInt(),
                intro = row.getValue("intro").ifBlank { null },
                archived = archived,
            ),
        )
        return true
    }

    private fun addSeason(
        ledger: SeedLedger,
        row: Map<String, String>,
    ): Boolean {
        val name = row.getValue("name")
        if (!ledger.toWrite("season|$name") { records.seasons.countEverNamed(name) > 0 }) return false
        records.seasons.save(
            Season(
                name = name,
                startDate = LocalDate.parse(row.getValue("start_date")),
                endDate = LocalDate.parse(row.getValue("end_date")),
            ),
        )
        return true
    }

    /**
     * A team, keyed on its name alone. The pool is the association's rather than a game's, so
     * BS HyperS listed once for CS:GO and once for CS2 is one team, drawn with each game's art.
     */
    private fun addTeam(
        ledger: SeedLedger,
        name: String,
    ): Boolean {
        if (!ledger.toWrite("team|$name") { records.teams.countEverNamed(name) > 0 }) return false
        records.teams.save(Team(name = name))
        return true
    }

    /** A line-up place, keyed on the team, game and season it was played in and the handle. */
    private fun addEntry(
        ledger: SeedLedger,
        team: Team,
        season: Season,
        row: Map<String, String>,
    ): Boolean {
        val game = row.getValue("game")
        val handle = row.getValue("handle")
        val key = listOf("entry", row.getValue("team"), game, row.getValue("season"), handle).joinToString("|")
        // Through whatever fielding holds it, dropped or not: looking only under a live
        // fielding would miss a dropped team's line-up and write the row a second time.
        if (!ledger.toWrite(key) { records.entries.countEverPlaced(team.id!!, game, season.id!!, handle) > 0 }) return false

        // Only now, on the path that actually writes somebody down. Fielding the team before
        // this point would field it on every run, undoing a board that dropped it.
        val displayName = row.getValue("display_name").ifBlank { null }
        records.entries.save(
            TeamRosterEntry(
                teamSeason = fieldTeam(team, game, season),
                handle = handle,
                teamRole = TeamRole.valueOf(row.getValue("role")),
                displayName = displayName,
                sortIndex = row.getValue("sort_index").toInt(),
                // Attached as the place is created, which is the only moment this can be settled
                // without overruling somebody. A name matching nobody, or more than one person,
                // leaves the place under its handle for an admin to resolve.
                userId = displayName?.let { records.users.findOnlyByWrittenName(it)?.id },
            ),
        )
        return true
    }

    /**
     * Gives each member attached to a place the handle they last played that game under.
     *
     * A member's handle for a game renders them in every season of it, and the most recent
     * season they played is the one that names them. A handle somebody has already set is left
     * alone. Runs only where places were written, so a start that changed nothing writes nothing.
     */
    private fun adoptHandlesPlayedUnder(): Int =
        records.entries
            .findAllAttached()
            .groupBy { it.userId!! to it.teamSeason.game }
            .mapValues { (_, places) -> places.maxWith(compareBy({ it.teamSeason.season.startDate }, { it.id })) }
            .count { (member, latest) ->
                val (userId, game) = member
                if (records.accounts.findByUserIdAndGame(userId, game) != null) return@count false
                records.accounts.save(UserGameAccount(userId = userId, game = game, handle = latest.handle))
                true
            }

    /** The fielding of a team in a game and season, made where there is none. */
    private fun fieldTeam(
        team: Team,
        game: String,
        season: Season,
    ): TeamSeason {
        // A team playing a game in a season says that game ran that season.
        if (records.entered.findBySeasonIdAndGame(season.id!!, game) == null) {
            records.entered.save(SeasonGame(season = season, game = game))
        }
        return records.fieldings.findByTeamIdAndGameAndSeasonId(team.id!!, game, season.id!!)
            ?: records.fieldings.save(TeamSeason(team = team, game = game, season = season))
    }

    private companion object {
        val log = LoggerFactory.getLogger(ShippedEsports::class.java)

        const val SEED = "esports"
    }
}

/**
 * Loads the esports history that ships once the application is up. A failure is logged, never thrown, so it
 * cannot block start-up.
 */
@Component
class ShippedEsportsOnStartup(
    private val esports: ShippedEsports,
) {
    @Order(SeedOrder.RECORDS)
    @EventListener(ApplicationReadyEvent::class)
    fun onReady() {
        try {
            esports.apply()
        } catch (e: Exception) {
            log.warn("[esports-seed] could not load the esports history that ships: {}", e.message)
        }
    }

    private companion object {
        val log = LoggerFactory.getLogger(ShippedEsportsOnStartup::class.java)
    }
}
