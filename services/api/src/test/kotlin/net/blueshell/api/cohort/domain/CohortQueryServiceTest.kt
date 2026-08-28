package net.blueshell.api.cohort.domain

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.blueshell.api.user.api.UserService
import net.blueshell.api.cohort.persistence.Cohort
import net.blueshell.api.cohort.persistence.CohortKind
import net.blueshell.api.cohort.persistence.CohortMemberRepository
import net.blueshell.api.cohort.persistence.CohortRepository
import net.blueshell.api.cohort.persistence.CohortSubjectRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class CohortQueryServiceTest {

    private val cohorts: CohortRepository = mockk()
    private val cohortMembers: CohortMemberRepository = mockk()
    private val subjects: CohortSubjectRepository = mockk()
    private val users: UserService = mockk()
    private val targetIds: CohortTargetIds = mockk()
    private val service = CohortQueryService(cohorts, cohortMembers, subjects, users, targetIds)

    @Test
    fun `summaries returns memberCount from count method not findAll`() {
        val cohort = cohort(1L, subjectId = 10L)
        every { cohorts.findAll() } returns listOf(cohort)
        every { cohortMembers.countByCohortIdAndUserIdIsNotNull(1L) } returns 3L
        every { targetIds.find(cohort) } returns "list-1"

        val result = service.summaries()

        assertThat(result).hasSize(1)
        assertThat(result[0].memberCount).isEqualTo(3)
        assertThat(result[0].externalId).isEqualTo("list-1")
        verify(exactly = 1) { cohortMembers.countByCohortIdAndUserIdIsNotNull(1L) }
        verify(exactly = 0) { cohortMembers.findAllByCohortIdAndUserIdIsNotNull(any()) }
    }

    @Test
    fun `summaries excludes stranger rows (userId == null) from memberCount`() {
        val cohort = cohort(2L, subjectId = 20L)
        every { cohorts.findAll() } returns listOf(cohort)
        // Only 2 desired rows (userId != null); 5 stranger rows excluded by the query predicate
        every { cohortMembers.countByCohortIdAndUserIdIsNotNull(2L) } returns 2L
        every { targetIds.find(cohort) } returns null

        val result = service.summaries()

        assertThat(result[0].memberCount).isEqualTo(2)
        assertThat(result[0].externalId).isNull()
    }

    @Test
    fun `summaries returns empty list when no cohorts exist`() {
        every { cohorts.findAll() } returns emptyList()

        val result = service.summaries()

        assertThat(result).isEmpty()
        verify(exactly = 0) { cohortMembers.countByCohortIdAndUserIdIsNotNull(any()) }
    }

    @Test
    fun `summaries aggregates counts across multiple cohorts`() {
        val cohortA = cohort(10L, subjectId = 100L)
        val cohortB = cohort(11L, subjectId = 101L)
        every { cohorts.findAll() } returns listOf(cohortA, cohortB)
        every { cohortMembers.countByCohortIdAndUserIdIsNotNull(10L) } returns 5L
        every { cohortMembers.countByCohortIdAndUserIdIsNotNull(11L) } returns 0L
        every { targetIds.find(cohortA) } returns "ext-a"
        every { targetIds.find(cohortB) } returns null

        val result = service.summaries()

        assertThat(result).hasSize(2)
        assertThat(result.first { it.cohort.id == 10L }.memberCount).isEqualTo(5)
        assertThat(result.first { it.cohort.id == 11L }.memberCount).isEqualTo(0)
    }

    private fun cohort(id: Long, subjectId: Long): Cohort =
        Cohort(
            system = "BREVO",
            kind = CohortKind.LIST,
            label = "Test Cohort $id",
            subjectId = subjectId,
        ).apply { this.id = id }
}
