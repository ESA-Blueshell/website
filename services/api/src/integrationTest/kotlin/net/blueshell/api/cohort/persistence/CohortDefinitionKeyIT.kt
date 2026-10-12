package net.blueshell.api.cohort.persistence

import net.blueshell.api.testsupport.UserTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.jdbc.core.JdbcTemplate

/**
 * Real-MariaDB checks for V89: the definition key round-trips, one cohort cannot be produced by
 * two records, and no fact columns remain on the table.
 */
@SpringBootTest
class CohortDefinitionKeyIT : UserTestSupport() {
    @Autowired
    private lateinit var cohorts: CohortRepository

    @Autowired
    private lateinit var jdbc: JdbcTemplate

    private fun cohort(key: String?) =
        Cohort(
            type = CohortType.NEWSLETTER_SUBSCRIBERS,
            label = "Cohort ${System.nanoTime()}",
            definitionKey = key,
        )

    @Test
    fun `the key naming the definition round-trips`() {
        val key = "PERIOD_MEMBERS:${System.nanoTime()}"
        val saved = cohorts.save(cohort(key))

        val reloaded = cohorts.findById(saved.id!!).orElseThrow()

        assertThat(reloaded.definitionKey).isEqualTo(key)
        assertThat(cohorts.findByDefinitionKey(key)?.id).isEqualTo(saved.id)
    }

    @Test
    fun `two records cannot claim the same definition`() {
        val key = "PERIOD_MEMBERS:${System.nanoTime()}"
        cohorts.save(cohort(key))

        assertThatThrownBy { cohorts.saveAndFlush(cohort(key)) }
            .isInstanceOf(DataIntegrityViolationException::class.java)
    }

    @Test
    fun `records that name no definition do not collide with each other`() {
        // Rows soft-deleted before the key existed carry none, and MariaDB lets a unique
        // index hold any number of nulls.
        cohorts.save(cohort(null))
        cohorts.saveAndFlush(cohort(null))

        assertThat(cohorts.findAll()).isNotEmpty
    }

    @Test
    fun `the columns the rule used to live in are gone`() {
        val columns =
            jdbc.queryForList(
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
