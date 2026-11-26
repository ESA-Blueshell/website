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
import org.springframework.core.task.TaskRejectedException

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

    @Test
    fun `a nudge drains, the poll nudges and a stopped worker claims nothing`() {
        every { executions.findDueEmailJobs(any(), any()) } returns emptyList()

        worker.nudge()
        worker.poll()
        worker.stop()
        worker.nudge()

        verify(exactly = 2) { taskExecutor.execute(any<Runnable>()) }
    }

    @Test
    fun `a disabled poll does not drain`() {
        val quiet = EmailJobWorker(executions, executor, batchSession, taskExecutor, pollEnabled = false)

        quiet.poll()

        verify(exactly = 0) { taskExecutor.execute(any<Runnable>()) }
    }

    @Test
    fun `a failed drain and a refused submit both free the next nudge`() {
        every { executions.findDueEmailJobs(any(), any()) } throws IllegalStateException("database down")
        worker.nudge()

        every { taskExecutor.execute(any<Runnable>()) } throws TaskRejectedException("full")
        worker.nudge()
        worker.nudge()

        verify(exactly = 3) { taskExecutor.execute(any<Runnable>()) }
    }
}
