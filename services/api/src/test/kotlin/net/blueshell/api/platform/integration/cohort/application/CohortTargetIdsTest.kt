package net.blueshell.api.platform.integration.cohort.application

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.blueshell.api.platform.integration.cohort.persistence.Cohort
import net.blueshell.api.platform.integration.cohort.persistence.CohortKind
import net.blueshell.api.platform.integration.cohort.persistence.repository.CohortRepository
import net.blueshell.api.platform.integration.sync.application.ExternalIdMappingService
import net.blueshell.api.platform.integration.sync.application.ExternalIdMappingService.Companion.COHORT_AGGREGATE
import net.blueshell.api.platform.integration.sync.persistence.ExternalIdMapping
import net.blueshell.api.shared.job.NonRetryableJobException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.web.server.ResponseStatusException

class CohortTargetIdsTest {

    private val externalIds: ExternalIdMappingService = mockk(relaxed = true)
    private val cohorts: CohortRepository = mockk(relaxed = true)
    private val targetIds = CohortTargetIds(externalIds, cohorts)

    private fun cohort(id: Long, externalId: String? = null) =
        Cohort(system = "BREVO", kind = CohortKind.LIST, label = "Members").apply {
            this.id = id
            this.externalId = externalId
        }

    @Test
    fun `find reads the column first and does not touch the mapping`() {
        val cohort = cohort(5L, externalId = "col-1")

        assertThat(targetIds.find(cohort)).isEqualTo("col-1")
        verify(exactly = 0) { externalIds.find(any(), any(), any()) }
    }

    @Test
    fun `find falls back to the legacy mapping when the column is blank, without writing`() {
        val cohort = cohort(5L, externalId = null)
        every { externalIds.find(COHORT_AGGREGATE, 5L, "BREVO") } returns
            ExternalIdMapping(COHORT_AGGREGATE, 5L, "BREVO", "legacy-1")

        assertThat(targetIds.find(cohort)).isEqualTo("legacy-1")
        verify(exactly = 0) { cohorts.save(any()) }
        verify(exactly = 0) { externalIds.upsert(any(), any(), any(), any()) }
    }

    @Test
    fun `require throws a terminal failure when the cohort is not materialised`() {
        val cohort = cohort(5L, externalId = null)
        every { externalIds.find(COHORT_AGGREGATE, 5L, "BREVO") } returns null

        assertThatThrownBy { targetIds.require(cohort) }
            .isInstanceOf(NonRetryableJobException::class.java)
    }

    @Test
    fun `record writes the column and the legacy mapping`() {
        val cohort = cohort(5L, externalId = null)
        every { cohorts.findFirstBySystemAndExternalId("BREVO", "list-9") } returns null
        every { cohorts.save(cohort) } returns cohort

        targetIds.record(cohort, "list-9")

        assertThat(cohort.externalId).isEqualTo("list-9")
        verify { cohorts.save(cohort) }
        verify { externalIds.upsert(COHORT_AGGREGATE, 5L, "BREVO", "list-9") }
    }

    @Test
    fun `record rejects a blank external id`() {
        assertThatThrownBy { targetIds.record(cohort(5L), "  ") }
            .isInstanceOf(IllegalArgumentException::class.java)
        verify(exactly = 0) { cohorts.save(any()) }
    }

    @Test
    fun `record refuses to relink an id already owned by another active cohort`() {
        val cohort = cohort(5L)
        every { cohorts.findFirstBySystemAndExternalId("BREVO", "list-9") } returns cohort(99L, "list-9")

        assertThatThrownBy { targetIds.record(cohort, "list-9") }
            .isInstanceOf(ResponseStatusException::class.java)
        verify(exactly = 0) { cohorts.save(any()) }
    }
}
