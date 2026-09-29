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
        assertThat(loaded<DriftResolution>().id).isNull()
    }

    @Test
    fun `a drift resolution keeps who it concerned and who made it`() {
        val at = Instant.parse("2026-09-29T20:00:00Z")
        val resolution = DriftResolution(3L, DriftResolutionAction.LINK, 5L, "ext-5", "ada@example.com", 9L, at)

        assertThat(listOf(resolution.cohortId, resolution.action, resolution.userId)).containsExactly(3L, DriftResolutionAction.LINK, 5L)
        assertThat(listOf(resolution.externalUserId, resolution.label)).containsExactly("ext-5", "ada@example.com")
        assertThat(listOf(resolution.resolvedBy, resolution.resolvedAt)).containsExactly(9L, at)
    }

    @Test
    fun `a reconcile run keeps the target it was of and when it started`() {
        val at = Instant.parse("2026-09-29T03:00:00Z")
        val run = TargetReconcileRun(3L, at, JobTrigger.SCHEDULED_RUN, 1, 2, 3)

        assertThat(listOf(run.cohortId, run.startedAt, run.trigger)).containsExactly(3L, at, JobTrigger.SCHEDULED_RUN)
        assertThat(listOf(run.inSync, run.oursOnly, run.theirsOnly)).containsExactly(1, 2, 3)
    }
}
