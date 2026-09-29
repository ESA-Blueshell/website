package net.blueshell.api.cohort.persistence

import net.blueshell.api.shared.job.JobTrigger
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant

class CohortRecordsTest {
    // The constructor the JPA compiler plugin adds, which is the only one Hibernate calls.
    private inline fun <reified T : Any> loaded(): T = T::class.java.getDeclaredConstructor().newInstance()

    @Test
    fun `Hibernate can build each append-only cohort record to load a row into`() {
        assertThat(loaded<TargetDeletion>().id).isNull()
        assertThat(loaded<TargetReconcileRun>().id).isNull()
    }

    @Test
    fun `a reconcile run keeps the target it was of and when it started`() {
        val at = Instant.parse("2026-09-29T03:00:00Z")
        val run = TargetReconcileRun(3L, at, JobTrigger.SCHEDULED_RUN, 1, 2, 3)

        assertThat(listOf(run.cohortId, run.startedAt, run.trigger)).containsExactly(3L, at, JobTrigger.SCHEDULED_RUN)
        assertThat(listOf(run.inSync, run.oursOnly, run.theirsOnly)).containsExactly(1, 2, 3)
    }
}
