package net.blueshell.api.jobs.domain

import jakarta.annotation.PreDestroy
import net.blueshell.api.jobs.api.EmailBatchPort
import net.blueshell.api.jobs.api.JobExecutionService
import net.blueshell.api.jobs.api.JobExecutor
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.task.TaskExecutor
import org.springframework.data.domain.PageRequest
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Drains due email jobs on one thread, in one SMTP session per batch. Each row still goes through
 * [JobExecutor], so its handler, retry schedule and status are the same as any other job's.
 */
@Component
class EmailJobWorker(
    private val jobExecutionService: JobExecutionService,
    private val jobExecutor: JobExecutor,
    private val batchSession: EmailBatchPort,
    @param:Qualifier("emailJobExecutor") private val emailJobExecutor: TaskExecutor,
    @param:Value("\${app.email.batch.size:50}") private val batchSize: Int = 50,
    // Tests that drive the executor themselves turn recovery off; the poll would race them.
    @param:Value("\${app.jobs.recovery.enabled:true}") private val pollEnabled: Boolean = true,
) {
    private val pending = AtomicBoolean(false)
    private val stopping = AtomicBoolean(false)

    /** Asks for a drain; repeated nudges while one is pending share the same run. */
    fun nudge() {
        if (stopping.get() || !pending.compareAndSet(false, true)) return
        try {
            emailJobExecutor.execute { runCatching(::drain).onFailure { log.warn("Email batch drain failed", it) } }
        } catch (ex: Exception) {
            pending.set(false)
            log.warn("Email batch drain could not be submitted", ex)
        }
    }

    @Scheduled(fixedDelayString = "\${app.email.batch.poll-interval-ms:15000}")
    fun poll() {
        if (pollEnabled) nudge()
    }

    @PreDestroy
    fun stop() {
        stopping.set(true)
    }

    /** Runs batches until none is due. A row seen once in this drain is left for the next one. */
    fun drain() {
        try {
            val seen = HashSet<Long>()
            while (!stopping.get()) {
                val due =
                    jobExecutionService
                        .findDueEmailJobs(Instant.now(), PageRequest.of(0, batchSize))
                        .filter { seen.add(it.id!!) }
                if (due.isEmpty()) break
                batchSession.open {
                    for (execution in due) {
                        if (stopping.get()) break
                        runCatching { jobExecutor.execute(execution) }
                            .onFailure { log.warn("Email job execution {} failed; continuing the batch", execution.id, it) }
                    }
                }
            }
        } finally {
            pending.set(false)
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(EmailJobWorker::class.java)
    }
}
