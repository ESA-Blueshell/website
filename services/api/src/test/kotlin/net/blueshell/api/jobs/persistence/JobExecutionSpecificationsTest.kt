package net.blueshell.api.jobs.persistence

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jakarta.persistence.criteria.CriteriaBuilder
import jakarta.persistence.criteria.CriteriaQuery
import jakarta.persistence.criteria.Expression
import jakarta.persistence.criteria.Root
import net.blueshell.api.shared.enums.JobExecutionCategory
import org.junit.jupiter.api.Test

class JobExecutionSpecificationsTest {
    private val jobType: Expression<String> = mockk()
    private val root: Root<JobExecution> = mockk { every { get<String>("jobType") } returns mockk() }
    private val cb: CriteriaBuilder = mockk(relaxed = true) { every { lower(any()) } returns jobType }
    private val query: CriteriaQuery<*> = mockk()

    @Test
    fun `filters a category by its prefix, and other by no category's prefix`() {
        JobExecutionSpecifications.category(JobExecutionCategory.discord).toPredicate(root, query, cb)
        JobExecutionSpecifications.category(JobExecutionCategory.other).toPredicate(root, query, cb)

        verify(exactly = 2) { cb.like(jobType, "discord.%") }
        verify(exactly = 2) { cb.equal(jobType, "discord") }
    }
}
