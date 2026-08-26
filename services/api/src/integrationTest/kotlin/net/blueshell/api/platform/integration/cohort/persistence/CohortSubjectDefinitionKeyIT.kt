package net.blueshell.api.platform.integration.cohort.persistence

import net.blueshell.api.platform.integration.cohort.persistence.repository.CohortSubjectRepository
import net.blueshell.api.testsupport.UserTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.jdbc.core.JdbcTemplate

/**
 * Real-MariaDB checks for V89: the definition key round-trips, one cohort cannot be produced
 * by two records, and the fact columns the rule used to live in are gone.
 */
@SpringBootTest
class CohortSubjectDefinitionKeyIT : UserTestSupport() {
    @Autowired
    private lateinit var subjects: CohortSubjectRepository

    @Autowired
    private lateinit var jdbc: JdbcTemplate

    private fun subject(key: String?) = CohortSubject(
        type = CohortSubjectType.NEWSLETTER_SUBSCRIBERS,
        label = "Subject ${System.nanoTime()}",
        definitionKey = key,
    )

    @Test
    fun `the key naming the definition round-trips`() {
        val key = "PERIOD_MEMBERS:${System.nanoTime()}"
        val saved = subjects.save(subject(key))

        val reloaded = subjects.findById(saved.id!!).orElseThrow()

        assertThat(reloaded.definitionKey).isEqualTo(key)
        assertThat(subjects.findByDefinitionKey(key)?.id).isEqualTo(saved.id)
    }

    @Test
    fun `two records cannot claim the same definition`() {
        val key = "PERIOD_MEMBERS:${System.nanoTime()}"
        subjects.save(subject(key))

        assertThatThrownBy { subjects.saveAndFlush(subject(key)) }
            .isInstanceOf(DataIntegrityViolationException::class.java)
    }

    @Test
    fun `records that name no definition do not collide with each other`() {
        // Rows soft-deleted before the key existed carry none, and MariaDB lets a unique
        // index hold any number of nulls.
        subjects.save(subject(null))
        subjects.saveAndFlush(subject(null))

        assertThat(subjects.findAll()).isNotEmpty
    }

    @Test
    fun `the columns the rule used to live in are gone`() {
        val columns = jdbc.queryForList(
            """
            SELECT column_name FROM information_schema.columns
            WHERE table_schema = DATABASE() AND table_name = 'cohort_subject'
            """.trimIndent(),
            String::class.java,
        )

        assertThat(columns).contains("definition_key")
        assertThat(columns).doesNotContain("fact_kind", "fact_key", "enabled")
    }
}
