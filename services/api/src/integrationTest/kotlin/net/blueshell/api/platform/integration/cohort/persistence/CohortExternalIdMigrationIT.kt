package net.blueshell.api.platform.integration.cohort.persistence

import net.blueshell.api.platform.integration.cohort.persistence.repository.CohortRepository
import net.blueshell.api.sync.api.ExternalIdMappingService.Companion.COHORT_AGGREGATE
import net.blueshell.api.sync.persistence.ExternalIdMapping
import net.blueshell.api.sync.persistence.ExternalIdMappingRepository
import net.blueshell.api.shared.enums.TargetSystem
import net.blueshell.api.testsupport.UserTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate

/**
 * Real-MariaDB checks for V77: the `cohort.external_id` column round-trips
 * through the entity, the `(system, external_id, deleted_at)` index exists,
 * and the backfill statement copies the legacy `external_id_mapping` id into
 * the column.
 */
@SpringBootTest
class CohortExternalIdMigrationIT : UserTestSupport() {

    @Autowired private lateinit var cohorts: CohortRepository

    @Autowired private lateinit var externalIds: ExternalIdMappingRepository

    @Autowired private lateinit var jdbc: JdbcTemplate

    @Test
    fun `external_id column round-trips through the entity`() {
        val saved = cohorts.save(
            Cohort(system = TargetSystem.BREVO.name, kind = CohortKind.LIST, label = "Members", externalId = "list-1"),
        )
        assertThat(cohorts.findById(saved.id!!).orElseThrow().externalId).isEqualTo("list-1")
    }

    @Test
    fun `the system + external_id index exists`() {
        val count = jdbc.queryForObject(
            """
            SELECT COUNT(*) FROM information_schema.statistics
            WHERE table_schema = DATABASE() AND table_name = 'cohort'
              AND index_name = 'idx_cohort_external_id'
            """.trimIndent(),
            Int::class.java,
        )
        assertThat(count).isGreaterThan(0)
    }

    @Test
    fun `the V77 backfill copies the legacy mapping id into the column`() {
        val cohort = cohorts.save(
            Cohort(system = TargetSystem.BREVO.name, kind = CohortKind.LIST, label = "Members", externalId = null),
        )
        externalIds.saveAndFlush(
            ExternalIdMapping(COHORT_AGGREGATE, cohort.id!!, TargetSystem.BREVO.name, "legacy-9"),
        )

        // The backfill statement from V77 (re-run here against a row left in the
        // pre-migration shape: column null, legacy mapping present).
        jdbc.update(
            """
            UPDATE cohort c
            JOIN external_id_mapping m
              ON m.aggregate_type = 'COHORT' AND m.aggregate_id = c.id AND m.system = c.system
            SET c.external_id = m.external_id
            WHERE c.external_id IS NULL
            """.trimIndent(),
        )

        assertThat(cohorts.findById(cohort.id!!).orElseThrow().externalId).isEqualTo("legacy-9")
    }
}
