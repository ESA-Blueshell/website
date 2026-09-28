package net.blueshell.api.jobs.api

import net.blueshell.api.shared.enums.JobExecutionStatus
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.shared.job.JobTrigger
import net.blueshell.api.shared.tracking.Actor
import net.blueshell.api.testsupport.ServiceTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.TestPropertySource
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

// Nothing here is dispatched; the sweeps stay off so they cannot run the rows either.
@TestPropertySource(properties = ["app.jobs.recovery.enabled=false"])
class JobEnqueueRaceIT : ServiceTestSupport() {
    @Autowired
    private lateinit var executions: JobExecutionService

    private val pool = Executors.newFixedThreadPool(2)

    /** Enqueues `race.test` with [key] in a transaction of its own that stays open [holdMillis] after. */
    private fun enqueue(
        key: String,
        trigger: JobTrigger,
        actor: Actor = Actor.system(),
        holdMillis: Long = 0,
        before: () -> Unit = {},
        after: () -> Unit = {},
    ) = pool.submit<Enqueued?> {
        before()
        transactionTemplate.execute {
            executions
                .createQueued(
                    "race.test",
                    "{}",
                    actor,
                    trigger,
                    dedupKey = key,
                    queuesBehindRunning = true,
                ).also {
                    after()
                    Thread.sleep(holdMillis)
                }
        }
    }

    private fun rowsOf(key: String) = jobExecutions.findByJobTypeAndDedupKey("race.test", key)

    @Test
    fun `an enqueue that meets one not yet committed waits for it, then folds into it`() {
        val key = UUID.randomUUID().toString()
        val inserted = CountDownLatch(1)

        val first = enqueue(key, JobTrigger.EVENT_CREATED, holdMillis = 1500, after = inserted::countDown)
        inserted.await(10, TimeUnit.SECONDS)
        val second = enqueue(key, JobTrigger.EVENT_UPDATED)

        assertThat(first.get(20, TimeUnit.SECONDS)?.dispatch).isTrue()
        assertThat(second.get(20, TimeUnit.SECONDS)?.dispatch).isFalse()
        val rows = rowsOf(key)
        assertThat(rows).hasSize(1)
        assertThat(rows.single().status).isEqualTo(JobExecutionStatus.QUEUED)
        assertThat(rows.single().foldedTriggers.map { it.trigger }).containsExactly(JobTrigger.EVENT_UPDATED)
    }

    @Test
    fun `two first enqueues of one key at once leave one row, neither failing`() {
        val key = UUID.randomUUID().toString()
        val start = CyclicBarrier(2)

        val runs =
            listOf(
                JobTrigger.EVENT_CREATED,
                JobTrigger.EVENT_UPDATED,
            ).map { enqueue(key, it, holdMillis = 500, before = { start.await() }) }

        assertThat(runs.map { it.get(20, TimeUnit.SECONDS)?.dispatch }).containsExactlyInAnyOrder(true, false)
        assertThat(rowsOf(key)).hasSize(1)
    }

    @Test
    fun `an enqueue that fails still gives its lock back`() {
        val key = UUID.randomUUID().toString()

        // No such user, so the insert breaks its foreign key.
        assertThat(
            runCatching {
                enqueue(key, JobTrigger.EVENT_CREATED, actor = Actor.user(Long.MAX_VALUE, Role.BOARD)).get(20, TimeUnit.SECONDS)
            }.isFailure,
        ).isTrue()

        assertThat(enqueue(key, JobTrigger.EVENT_UPDATED).get(20, TimeUnit.SECONDS)?.dispatch).isTrue()
        assertThat(rowsOf(key)).hasSize(1)
    }
}
