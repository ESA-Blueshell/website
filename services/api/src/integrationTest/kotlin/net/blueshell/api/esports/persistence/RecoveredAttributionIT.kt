package net.blueshell.api.esports.persistence

import db.migration.R__Esports_seed
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.testsupport.EsportsSeedFixture
import net.blueshell.api.testsupport.UserTestSupport
import net.blueshell.api.user.persistence.User
import org.assertj.core.api.Assertions.assertThat
import org.flywaydb.core.api.migration.Context
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import java.sql.Connection
import javax.sql.DataSource

/**
 * The recovered line-ups are a handle and a real name read off years of the old site, and the
 * association's own accounts are matched to them as each place is written, so a member meets
 * their own history. Asserted here, including that the matching never runs twice.
 *
 * The files are [EsportsSeedFixture], so the name matched is a fixture player's rather than a
 * member's: whose history this is has nothing to do with how the matching works.
 */
@SpringBootTest
class RecoveredAttributionIT : UserTestSupport() {
    @Autowired private lateinit var jdbc: JdbcTemplate

    @Autowired private lateinit var dataSource: DataSource

    /** A name the seed files record a line-up place under, with its most recent season's handle. */
    private val recordedName = "Player Four"
    private val latestHandle = "four"

    private fun named(first: String, last: String): User {
        val user = createUserWithRole(Role.MEMBER)
        user.firstName = first
        user.lastName = last
        user.prefix = null
        return userRepository.save(user)
    }

    private fun placesNaming(userId: Long): Int =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM team_roster_entry WHERE user_id = ? AND deleted_at = '9999-12-31 23:59:59'",
            Int::class.java,
            userId,
        )!!

    @Test
    fun `a place recorded under a member's name is attributed to them as it is written`() {
        val user = named("Player", "Four")

        runLoader()

        assertThat(user.fullName).isEqualTo(recordedName)
        assertThat(placesNaming(user.id!!)).isGreaterThan(0)
    }

    @Test
    fun `an attributed member takes up the handle of the season they played most recently`() {
        val user = named("Player", "Four")

        runLoader()

        // A handle for a game is what every season of it renders them by, so it is the latest
        // one. What they were called in an earlier season is what that place already records.
        assertThat(
            jdbc.queryForObject(
                "SELECT handle FROM user_game_account WHERE user_id = ? AND game = 'GAMMA'",
                String::class.java,
                user.id,
            ),
        ).isEqualTo(latestHandle)
    }

    @Test
    fun `a name two members answer to leaves the place standing under its handle`() {
        val first = named("Player", "Four")
        val twin = named("Player", "Four")

        runLoader()

        // Guessing between two people is worse than leaving it for an admin to resolve.
        assertThat(twin.id).isNotEqualTo(first.id)
        assertThat(placesNaming(first.id!!)).isZero()
        assertThat(placesNaming(twin.id!!)).isZero()
    }

    @Test
    fun `a member detached from a place is not attached again by the next run`() {
        val user = named("Player", "Four")
        runLoader()
        val attributed = placesNaming(user.id!!)
        jdbc.update("UPDATE team_roster_entry SET user_id = NULL WHERE user_id = ?", user.id)

        runLoader()

        // Detaching says who somebody is not, and it is a later decision than the import. A run
        // that matched the name again would undo it every time the application came up.
        assertThat(attributed).isGreaterThan(0)
        assertThat(placesNaming(user.id!!)).isZero()
    }

    @Test
    fun `a member who joins after the history was loaded is not attached to it`() {
        runLoader()

        val late = named("Player", "Four")

        runLoader()

        // The place was written before they had an account, so nothing matched it then and
        // nothing re-matches it now. Attaching them is an admin's decision, made by hand.
        assertThat(placesNaming(late.id!!)).isZero()
    }

    private fun runLoader() {
        dataSource.connection.use { connection ->
            R__Esports_seed(EsportsSeedFixture.files).migrate(object : Context {
                override fun getConfiguration() = null

                override fun getConnection(): Connection = connection
            })
        }
    }

    @AfterEach
    fun forgetTheFixtureGames() {
        EsportsSeedFixture.forget(dataSource)
    }
}
