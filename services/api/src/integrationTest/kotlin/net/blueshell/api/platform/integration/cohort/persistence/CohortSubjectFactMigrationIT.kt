package net.blueshell.api.platform.integration.cohort.persistence

import net.blueshell.api.platform.integration.cohort.persistence.repository.CohortRepository
import net.blueshell.api.platform.integration.cohort.persistence.repository.CohortSubjectRepository
import net.blueshell.api.platform.integration.sync.port.TargetSystem
import net.blueshell.api.testsupport.UserTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.jdbc.core.JdbcTemplate

/**
 * Real-MariaDB checks for V78: the fact columns round-trip, the
 * `uk_cohort_subject_fact` unique key holds (while NULL pairs coexist),
 * and the backfill statement copies a `cohort_rule` row onto its subject.
 */
@SpringBootTest
class CohortSubjectFactMigrationIT : UserTestSupport() {

    @Autowired private lateinit var subjects: CohortSubjectRepository

    @Autowired private lateinit var cohorts: CohortRepository

    @Autowired private lateinit var jdbc: JdbcTemplate

    @Test
    fun `fact columns round-trip through the entity`() {
        val saved = subjects.save(
            CohortSubject(
                type = CohortSubjectType.COMMITTEE_MEMBERS,
                label = "Web Cmte",
                factKind = CohortFactKind.COMMITTEE,
                factKey = "7",
                enabled = false,
            ),
        )
        val reloaded = subjects.findById(saved.id!!).orElseThrow()
        assertThat(reloaded.factKind).isEqualTo(CohortFactKind.COMMITTEE)
        assertThat(reloaded.factKey).isEqualTo("7")
        assertThat(reloaded.enabled).isFalse()
    }

    @Test
    fun `the fact pair is unique but null pairs coexist`() {
        subjects.saveAndFlush(subjectWithFact("8"))
        assertThatThrownBy { subjects.saveAndFlush(subjectWithFact("8")) }
            .isInstanceOf(DataIntegrityViolationException::class.java)

        // Two CUSTOM subjects with no fact pair must not collide on the unique key.
        subjects.saveAndFlush(CohortSubject(type = CohortSubjectType.CUSTOM, label = "Ad-hoc A"))
        subjects.saveAndFlush(CohortSubject(type = CohortSubjectType.CUSTOM, label = "Ad-hoc B"))
    }

    @Test
    fun `the V78 backfill copies a cohort_rule onto its subject`() {
        val subject = subjects.save(CohortSubject(type = CohortSubjectType.COMMITTEE_MEMBERS, label = "Backfill Cmte"))
        val cohort = cohorts.save(
            Cohort(TargetSystem.BREVO.name, CohortKind.LIST, "Backfill Cmte", subjectId = subject.id),
        )
        jdbc.update(
            "INSERT INTO cohort_rule (fact_kind, fact_key, cohort_id, subject_id, enabled, created_at, updated_at) " +
                "VALUES ('COMMITTEE', '99', ?, ?, 0, NOW(), NOW())",
            cohort.id,
            subject.id,
        )

        // The backfill statement from V78 (re-run against a subject left in the
        // pre-migration shape: fact pair null, a cohort_rule present).
        jdbc.update(
            """
            UPDATE cohort_subject s
            JOIN   cohort      c ON c.subject_id = s.id AND c.deleted_at = '9999-12-31 23:59:59'
            JOIN   cohort_rule r ON r.cohort_id  = c.id
            SET    s.fact_kind = r.fact_kind, s.fact_key = r.fact_key, s.enabled = r.enabled
            WHERE  s.fact_key IS NULL
            """.trimIndent(),
        )

        val reloaded = subjects.findById(subject.id!!).orElseThrow()
        assertThat(reloaded.factKind).isEqualTo(CohortFactKind.COMMITTEE)
        assertThat(reloaded.factKey).isEqualTo("99")
        assertThat(reloaded.enabled).isFalse()
    }

    private fun subjectWithFact(factKey: String) =
        CohortSubject(
            type = CohortSubjectType.COMMITTEE_MEMBERS,
            label = "Cmte $factKey",
            factKind = CohortFactKind.COMMITTEE,
            factKey = factKey,
        )
}
