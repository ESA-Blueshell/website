package net.blueshell.api.jobs.persistence

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jakarta.persistence.criteria.CriteriaBuilder
import jakarta.persistence.criteria.CriteriaQuery
import jakarta.persistence.criteria.Expression
import jakarta.persistence.criteria.Path
import jakarta.persistence.criteria.Root
import net.blueshell.api.jobs.domain.JobExecutionQuery
import net.blueshell.api.shared.enums.JobExecutionCategory
import net.blueshell.api.shared.enums.JobExecutionStatus
import org.junit.jupiter.api.Test

class JobExecutionSpecificationsTest {
    private val jobType: Expression<String> = mockk()
    private val root: Root<JobExecution> = mockk { every { get<String>("jobType") } returns mockk() }
    private val cb: CriteriaBuilder = mockk(relaxed = true) { every { lower(any()) } returns jobType }
    private val query: CriteriaQuery<*> = mockk()

    @Test
    fun `leaves out skipped runs where asked, and only then`() {
        val status: jakarta.persistence.criteria.Path<JobExecutionStatus> = mockk()
        every { root.get<JobExecutionStatus>("status") } returns status

        JobExecutionSpecifications.fromFilter(JobExecutionQuery(hideSkipped = true)).toPredicate(root, query, cb)
        JobExecutionSpecifications.fromFilter(JobExecutionQuery(hideSkipped = false)).toPredicate(root, query, cb)

        verify(exactly = 1) { cb.equal(status, JobExecutionStatus.SKIPPED) }
    }

    @Test
    fun `matches a number against the job's own id as well as the user who started it`() {
        val id: Path<Long> = mockk()
        val userId: Path<Long> = mockk()
        val anyRoot: Root<JobExecution> =
            mockk(relaxed = true) {
                every { get<Long>("id") } returns id
                every { get<Long>("initiatedByUserId") } returns userId
            }

        JobExecutionSpecifications.search("42").toPredicate(anyRoot, mockk(relaxed = true), cb)

        verify { cb.equal(id, 42L) }
        verify { cb.equal(userId, 42L) }
    }

    @Test
    fun `filters a category by its prefix, and other by no category's prefix`() {
        JobExecutionSpecifications.category(JobExecutionCategory.discord).toPredicate(root, query, cb)
        JobExecutionSpecifications.category(JobExecutionCategory.other).toPredicate(root, query, cb)

        verify(exactly = 2) { cb.like(jobType, "discord.%") }
        verify(exactly = 2) { cb.equal(jobType, "discord") }
    }
}
