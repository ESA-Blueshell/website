package net.blueshell.api.platform.integration.cohort.adapter.job

import io.mockk.mockk
import io.mockk.verify
import net.blueshell.api.platform.integration.cohort.port.`in`.CohortReconciliation
import net.blueshell.api.platform.integration.cohort.application.CohortJobs
import org.junit.jupiter.api.Test
import tools.jackson.databind.ObjectMapper

/**
 * Thin driving-adapter test: the spawn job still delegates to the inbound
 * port. The paged fan-out itself is covered by
 * [CohortReconciliationServiceTest][net.blueshell.api.platform.integration.cohort.application.CohortReconciliationServiceTest].
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
