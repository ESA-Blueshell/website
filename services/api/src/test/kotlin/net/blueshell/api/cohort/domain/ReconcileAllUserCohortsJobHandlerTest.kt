package net.blueshell.api.cohort.domain

import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import tools.jackson.databind.ObjectMapper

/**
 * Thin driving-adapter test: the spawn job still delegates to the inbound
 * port. The paged fan-out itself is covered by
 * [CohortReconciliationServiceTest][net.blueshell.api.cohort.domain.CohortReconciliationServiceTest].
 */
class ReconcileAllUserCohortsJobHandlerTest {

    private val objectMapper = ObjectMapper()
    private val reconciliation: CohortReconciliation = mockk(relaxed = true)
    private val handler = ReconcileAllUserCohortsJobHandler(objectMapper, reconciliation)

    @Test
    fun `delegates to reconcileAllUserCohorts`() {
        handler.handle(objectMapper.writeValueAsString(CohortJobs.ReconcileAllUserCohortsPayload()), executionId = null)

        verify { reconciliation.reconcileAllUserCohorts() }
    }
}
