package net.blueshell.api.committee.persistence

import net.blueshell.api.testsupport.UserTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.jdbc.core.JdbcTemplate
import java.time.Instant
import java.time.temporal.ChronoUnit

/** A committee's archived flag and the moment it was archived are kept together, down to the database. */
@SpringBootTest
class CommitteeArchivedAtIT : UserTestSupport() {
    @Autowired private lateinit var jdbc: JdbcTemplate

    @Autowired private lateinit var committees: CommitteeRepository

    @Test
    fun `the database refuses a flag and a date that disagree`() {
        val insert = "INSERT INTO committees (name, description, slug, archived, archived_at) VALUES (?, 'About', ?, ?, ?)"

        assertThatThrownBy { jdbc.update(insert, "Flagged", "flagged", true, null) }
            .isInstanceOf(DataIntegrityViolationException::class.java)
        assertThatThrownBy { jdbc.update(insert, "Dated", "dated", false, "2025-02-26 22:33:42") }
            .isInstanceOf(DataIntegrityViolationException::class.java)
        assertThat(jdbc.update(insert, "Ended", "ended", true, "2025-02-26 22:33:42")).isEqualTo(1)
    }

    @Test
    fun `an archived committee is read back with the moment it stopped, and stores none once brought back`() {
        val at = Instant.parse("2025-02-26T22:33:42Z").truncatedTo(ChronoUnit.SECONDS)
        val saved = committees.saveAndFlush(Committee(name = "Archivecie", description = "About", archived = true, archivedAt = at))

        assertThat(committees.findById(saved.id!!).get().archivedAt).isEqualTo(at)

        val back =
            committees.saveAndFlush(
                committees.findById(saved.id!!).get().apply {
                    archived = false
                    archivedAt = null
                },
            )
        assertThat(listOf(back.archived, back.archivedAt)).containsExactly(false, null)
    }
}
