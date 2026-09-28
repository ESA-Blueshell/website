package net.blueshell.api.committee.persistence

import net.blueshell.api.shared.model.addressOf
import net.blueshell.api.testsupport.UserTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate

/** The committee-page changeset backfills every committee's address the way [addressOf] makes one. */
@SpringBootTest
class CommitteeAddressBackfillIT : UserTestSupport() {
    @Autowired private lateinit var jdbc: JdbcTemplate

    private val backfill =
        javaClass
            .getResource("/db/changelog/changes/2026-09-26-committee-page.yaml")!!
            .readText()
            .let { Regex("SET slug = (.+)").find(it)!!.groupValues[1] }

    @ParameterizedTest
    @ValueSource(strings = ["  Pokémon Fan Club!  ", "Counter-Strike 2", "--LAN / Cie--", "Ελληνική Λέσχη"])
    fun `a committee's backfilled address is the one its name makes`(name: String) {
        val backfilled = jdbc.queryForObject("SELECT ${backfill.replace("(name)", "(?)")}", String::class.java, name)

        assertThat(backfilled).isEqualTo(addressOf(name))
    }
}
