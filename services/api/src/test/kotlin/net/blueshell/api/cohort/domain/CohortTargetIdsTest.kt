package net.blueshell.api.cohort.domain

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.blueshell.api.cohort.persistence.Cohort
import net.blueshell.api.cohort.persistence.CohortKind
import net.blueshell.api.cohort.persistence.CohortRepository
import net.blueshell.api.shared.job.NonRetryableJobException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.web.server.ResponseStatusException

class CohortTargetIdsTest {

    private val cohorts: CohortRepository = mockk(relaxed = true)
    private val targetIds = CohortTargetIds(cohorts)

    private fun cohort(id: Long, externalId: String? = null) =
        Cohort(system = "BREVO", kind = CohortKind.LIST, label = "Members").apply {
            this.id = id
            this.externalId = externalId
        }

    @Test
    fun `find reads the column`() {
        assertThat(targetIds.find(cohort(5L, externalId = "col-1"))).isEqualTo("col-1")
    }

    @Test
    fun `find returns null when the column is unset`() {
        assertThat(targetIds.find(cohort(5L, externalId = null))).isNull()
        assertThat(targetIds.find(cohort(5L, externalId = "  "))).isNull()
    }

    @Test
    fun `require throws a terminal failure when the cohort is not materialised`() {
        assertThatThrownBy { targetIds.require(cohort(5L, externalId = null)) }
            .isInstanceOf(NonRetryableJobException::class.java)
    }

    @Test
    fun `record writes the column`() {
        val cohort = cohort(5L, externalId = null)
        every { cohorts.findFirstBySystemAndExternalId("BREVO", "list-9") } returns null
        every { cohorts.save(cohort) } returns cohort

        targetIds.record(cohort, "list-9")

        assertThat(cohort.externalId).isEqualTo("list-9")
        verify { cohorts.save(cohort) }
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
