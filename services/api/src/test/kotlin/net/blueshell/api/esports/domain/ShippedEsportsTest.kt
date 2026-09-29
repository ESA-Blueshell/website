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
import net.blueshell.api.shared.seed.SeedDatabase
import net.blueshell.api.testsupport.Entities
import net.blueshell.api.testsupport.EsportsSeedFixture
import net.blueshell.api.user.api.UserService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * The loader against repositories that keep what they are given, and the ledger on an in-memory
 * database. `EsportsSeedLoadIT` checks the same rows against the real schema.
 */
class ShippedEsportsTest {
    private val db = SeedDatabase()
    private var ids = 0L

    private val games = mutableMapOf<String, ShippedGame>()
    private val archived = mutableListOf<String>()
    private val seasons = mutableListOf<Season>()
    private val teams = mutableListOf<Team>()
    private val deletedTeams = mutableSetOf<String>()
    private val entered = mutableListOf<SeasonGame>()
    private val fieldings = mutableListOf<TeamSeason>()
    private val entries = mutableListOf<TeamRosterEntry>()
    private val accounts = mutableListOf<UserGameAccount>()

    private val shippedGames =
        mock<ShippedGames>().also { mock ->
            whenever(mock.everHeld(any())).thenAnswer { it.arguments[0] in games }
            whenever(mock.add(any())).thenAnswer { (it.arguments[0] as ShippedGame).let { game -> games[game.code] = game } }
            whenever(mock.archive(any())).thenAnswer { archived += it.arguments[0] as String }
        }

    private val seasonRepository =
        mock<SeasonRepository>().also { mock ->
            whenever(mock.save(any<Season>())).thenAnswer {
                (it.arguments[0] as Season).also { s ->
                    s.id = ++ids
                    seasons += s
                }
            }
            whenever(mock.countEverNamed(any())).thenAnswer { call -> seasons.count { it.name == call.arguments[0] }.toLong() }
            whenever(mock.findByNameIgnoreCase(any())).thenAnswer { call -> seasons.firstOrNull { it.name == call.arguments[0] } }
        }

    private val teamRepository =
        mock<TeamRepository>().also { mock ->
            whenever(mock.save(any<Team>())).thenAnswer {
                (it.arguments[0] as Team).also { t ->
                    t.id = ++ids
                    teams += t
                }
            }
            whenever(mock.countEverNamed(any())).thenAnswer { call -> teams.count { it.name == call.arguments[0] }.toLong() }
            whenever(mock.findByNameIgnoreCase(any())).thenAnswer { call ->
                teams.firstOrNull { it.name == call.arguments[0] && it.name !in deletedTeams }
            }
        }

    private val seasonGames =
        mock<SeasonGameRepository>().also { mock ->
            whenever(mock.save(any<SeasonGame>())).thenAnswer { (it.arguments[0] as SeasonGame).also { g -> entered += g } }
            whenever(mock.findBySeasonIdAndGame(any(), any())).thenAnswer { call ->
                entered.firstOrNull { it.season.id == call.arguments[0] && it.game == call.arguments[1] }
            }
        }

    private val fieldingRepository =
        mock<TeamSeasonRepository>().also { mock ->
            whenever(mock.save(any<TeamSeason>())).thenAnswer {
                (it.arguments[0] as TeamSeason).also { f ->
                    f.id = ++ids
                    fieldings += f
                }
            }
            whenever(mock.findByTeamIdAndGameAndSeasonId(any(), any(), any())).thenAnswer { call ->
                fieldings.firstOrNull {
                    it.team.id == call.arguments[0] && it.game == call.arguments[1] && it.season.id == call.arguments[2]
                }
            }
        }

    private val entryRepository =
        mock<TeamRosterEntryRepository>().also { mock ->
            whenever(mock.save(any<TeamRosterEntry>())).thenAnswer { call ->
                (call.arguments[0] as TeamRosterEntry).also { e ->
                    e.id = ++ids
                    entries += e
                }
            }
            whenever(mock.countEverPlaced(any(), any(), any(), any())).thenAnswer { call ->
                entries
                    .count {
                        it.teamSeason.team.id == call.arguments[0] &&
                            it.teamSeason.game == call.arguments[1] &&
                            it.teamSeason.season.id == call.arguments[2] &&
                            it.handle == call.arguments[3]
                    }.toLong()
            }
            whenever(mock.findAllAttached()).thenAnswer { entries.filter { it.userId != null } }
        }

    private val accountRepository =
        mock<UserGameAccountRepository>().also { mock ->
            whenever(mock.save(any<UserGameAccount>())).thenAnswer { (it.arguments[0] as UserGameAccount).also { a -> accounts += a } }
            whenever(mock.findByUserIdAndGame(any(), any())).thenAnswer { call ->
                accounts.firstOrNull { it.userId == call.arguments[0] && it.game == call.arguments[1] }
            }
        }

    private val users = mock<UserService>().also { whenever(it.findOnlyByWrittenName(anyOrNull())).thenReturn(null) }

    private val records =
        EsportsSeedRecords(
            shippedGames,
            seasonRepository,
            teamRepository,
            fieldingRepository,
            seasonGames,
            entryRepository,
            accountRepository,
            users,
        )

    private fun load() = ShippedEsports(records, db.dataSource, db.transactions).apply(EsportsSeedFixture.files)

    @Test
    fun `the first run writes every row and the second writes none`() {
        assertThat(load()).isEqualTo(
            ShippedEsports.Applied(
                EsportsSeedFixture.GAMES.size,
                EsportsSeedFixture.SEASONS,
                EsportsSeedFixture.TEAMS,
                EsportsSeedFixture.ROSTER_PLACES,
            ),
        )
        assertThat(load()).isEqualTo(ShippedEsports.Applied(0, 0, 0, 0))
        assertThat(entries).hasSize(EsportsSeedFixture.ROSTER_PLACES)
    }

    @Test
    fun `rows are written as the files have them, a team fielded where it played`() {
        load()

        assertThat(games.getValue("ALPHA")).isEqualTo(
            ShippedGame("ALPHA", "Alpha", "alpha", "#112233", 1, "Alpha, the game a fixture team is fielded in.", false),
        )
        assertThat(games.getValue("GAMMA").accent).isNull()
        val two = entries.single { it.handle == "two" }
        assertThat(two.teamRole).isEqualTo(TeamRole.SUBSTITUTE)
        assertThat(two.displayName).isNull()
        assertThat(two.sortIndex).isEqualTo(1)
        assertThat(fieldings.map { "${it.team.name}/${it.game}/${it.season.name}" }).containsExactlyInAnyOrder(
            "Nomads/ALPHA/First 2030",
            "Settlers/ALPHA/First 2030",
            "Nomads/BETA/Second 2031",
            "Drifters/GAMMA/First 2030",
            "Drifters/GAMMA/Second 2031",
        )
        assertThat(entered.map { "${it.game}/${it.season.name}" })
            .containsExactlyInAnyOrder("ALPHA/First 2030", "BETA/Second 2031", "GAMMA/First 2030", "GAMMA/Second 2031")
    }

    @Test
    fun `a game the files archive is archived once, the way it is written`() {
        load()
        assertThat(games.getValue("BETA").archived).isTrue()
        assertThat(archived).containsExactly("BETA")

        load()
        assertThat(archived).containsExactly("BETA")

        db.jdbc.update("DELETE FROM seed_applied WHERE record_key = 'game-archived|BETA'")
        load()
        assertThat(archived).containsExactly("BETA", "BETA")
    }

    @Test
    fun `a database seeded before the ledger records what it holds and writes none of it again`() {
        load()
        db.jdbc.update("DELETE FROM seed_applied")

        assertThat(load()).isEqualTo(ShippedEsports.Applied(0, 0, 0, 0))
    }

    @Test
    fun `a place new to the files is attached to its player, who takes up the handle they last played`() {
        val player = Entities.user(id = 41L)
        whenever(users.findOnlyByWrittenName("Player Four")).thenReturn(player)

        load()

        assertThat(entries.filter { it.userId == 41L }.map { it.handle }).containsExactlyInAnyOrder("four-then", "four")
        assertThat(accounts.map { Triple(it.userId, it.game, it.handle) }).containsExactly(Triple(41L, "GAMMA", "four"))
    }

    @Test
    fun `a handle a member already set is left alone`() {
        val player = Entities.user(id = 41L)
        whenever(users.findOnlyByWrittenName("Player Four")).thenReturn(player)
        accounts += UserGameAccount(userId = 41L, game = "GAMMA", handle = "chosen")

        load()

        assertThat(accounts.map { it.handle }).containsExactly("chosen")
    }

    @Test
    fun `loads the files it was built with where it is given none`() {
        val loader = ShippedEsports(records, db.dataSource, db.transactions, EsportsSeedFixture.files)

        assertThat(loader.apply().games).isEqualTo(EsportsSeedFixture.GAMES.size)
    }

    @Test
    fun `a deleted team leaves its line-up out`() {
        load()
        deletedTeams += "Drifters"
        entries.clear()
        db.jdbc.update("DELETE FROM seed_applied WHERE record_key LIKE 'entry|%'")

        assertThat(load()).isEqualTo(ShippedEsports.Applied(0, 0, 0, EsportsSeedFixture.ROSTER_PLACES - 2))
    }
}
