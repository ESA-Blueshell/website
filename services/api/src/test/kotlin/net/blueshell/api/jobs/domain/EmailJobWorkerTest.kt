package net.blueshell.api.jobs.domain

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.blueshell.api.jobs.api.EmailBatchPort
import net.blueshell.api.jobs.api.JobExecutionService
import net.blueshell.api.jobs.api.JobExecutor
import net.blueshell.api.jobs.persistence.JobExecution
import org.junit.jupiter.api.Test
import org.springframework.core.task.TaskExecutor

class EmailJobWorkerTest {
    private val executions: JobExecutionService = mockk()
    private val executor: JobExecutor = mockk()
    private val batchSession: EmailBatchPort = mockk()
    private val taskExecutor: TaskExecutor = mockk()
    private val worker = EmailJobWorker(executions, executor, batchSession, taskExecutor)

    init {
        every { taskExecutor.execute(any<Runnable>()) } answers { firstArg<Runnable>().run() }
        every { batchSession.open(any<() -> Unit>()) } answers { firstArg<() -> Unit>().invoke() }
    }

    @Test
    fun `one failing job does not stop the rest of the batch`() {
        val first = JobExecution(jobType = "email.signup").apply { id = 1 }
        val second = JobExecution(jobType = "email.signup").apply { id = 2 }
        var queries = 0
        every { executions.findDueEmailJobs(any(), any()) } answers {
            if (queries++ == 0) listOf(first, second) else emptyList()
        }
        every { executor.execute(first) } throws RuntimeException("boom")

        worker.drain()

        verify { executor.execute(first) }
        verify { executor.execute(second) }
    }

    @Test
    fun `drain stops when no email job is due`() {
        every { executions.findDueEmailJobs(any(), any()) } returns emptyList()

        worker.drain()

        verify(exactly = 1) { executions.findDueEmailJobs(any(), any()) }
        verify(exactly = 0) { batchSession.open(any<() -> Unit>()) }
    }
}
