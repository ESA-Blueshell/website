package net.blueshell.api.platform.integration.cohort.application

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.blueshell.api.domain.user.application.UserService
import net.blueshell.api.platform.integration.cohort.persistence.CohortSubject
import net.blueshell.api.platform.integration.cohort.persistence.CohortSubjectType
import net.blueshell.api.platform.integration.cohort.persistence.repository.CohortMemberRepository
import net.blueshell.api.platform.integration.cohort.persistence.repository.CohortRepository
import net.blueshell.api.platform.integration.cohort.persistence.repository.CohortSubjectRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class CohortSubjectQueryServiceTest {

    private val subjects: CohortSubjectRepository = mockk()
    private val cohorts: CohortRepository = mockk()
    private val cohortMembers: CohortMemberRepository = mockk()
    private val users: UserService = mockk()
    private val targetIds: CohortTargetIds = mockk()
    private val service = CohortSubjectQueryService(subjects, cohorts, cohortMembers, users, targetIds)

    @Test
    fun `summaries returns memberCount and mappingCount from count methods not findAll`() {
        val subject = subject(1L)
        every { subjects.findAll() } returns listOf(subject)
        every { cohortMembers.countBySubjectIdAndUserIdIsNotNull(1L) } returns 4L
        every { cohorts.countBySubjectId(1L) } returns 2L

        val result = service.summaries()

        assertThat(result).hasSize(1)
        assertThat(result[0].memberCount).isEqualTo(4)
        assertThat(result[0].mappingCount).isEqualTo(2)
        verify(exactly = 1) { cohortMembers.countBySubjectIdAndUserIdIsNotNull(1L) }
        verify(exactly = 1) { cohorts.countBySubjectId(1L) }
        verify(exactly = 0) { cohortMembers.findAllBySubjectIdAndUserIdIsNotNull(any()) }
        verify(exactly = 0) { cohorts.findAllBySubjectId(any()) }
    }

    @Test
    fun `summaries excludes stranger rows (userId == null) from memberCount`() {
        val subject = subject(2L)
        every { subjects.findAll() } returns listOf(subject)
        // 1 desired member; stranger rows (null userId) are excluded by the count predicate
        every { cohortMembers.countBySubjectIdAndUserIdIsNotNull(2L) } returns 1L
        every { cohorts.countBySubjectId(2L) } returns 3L

        val result = service.summaries()

        assertThat(result[0].memberCount).isEqualTo(1)
        assertThat(result[0].mappingCount).isEqualTo(3)
    }

    @Test
    fun `summaries returns empty list when no subjects exist`() {
        every { subjects.findAll() } returns emptyList()

        val result = service.summaries()

        assertThat(result).isEmpty()
        verify(exactly = 0) { cohortMembers.countBySubjectIdAndUserIdIsNotNull(any()) }
        verify(exactly = 0) { cohorts.countBySubjectId(any()) }
    }

    @Test
    fun `summaries aggregates counts across multiple subjects`() {
        val subjectA = subject(10L)
        val subjectB = subject(11L)
        every { subjects.findAll() } returns listOf(subjectA, subjectB)
        every { cohortMembers.countBySubjectIdAndUserIdIsNotNull(10L) } returns 7L
        every { cohortMembers.countBySubjectIdAndUserIdIsNotNull(11L) } returns 0L
        every { cohorts.countBySubjectId(10L) } returns 1L
        every { cohorts.countBySubjectId(11L) } returns 2L

        val result = service.summaries()

        assertThat(result).hasSize(2)
        val a = result.first { it.subject.id == 10L }
        val b = result.first { it.subject.id == 11L }
        assertThat(a.memberCount).isEqualTo(7)
        assertThat(a.mappingCount).isEqualTo(1)
        assertThat(b.memberCount).isEqualTo(0)
        assertThat(b.mappingCount).isEqualTo(2)
    }

    private fun subject(id: Long): CohortSubject =
        CohortSubject(CohortSubjectType.CUSTOM, "Test Subject $id").apply { this.id = id }
}
