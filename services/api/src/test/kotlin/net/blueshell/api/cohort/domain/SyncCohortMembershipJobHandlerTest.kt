package net.blueshell.api.cohort.domain

import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import tools.jackson.databind.ObjectMapper

/**
 * Thin driving-adapter test. Only covers payload deserialization and
 * dispatch to the inbound port — business logic lives in
 * [CohortMembershipSyncServiceTest][net.blueshell.api.cohort.domain.CohortMembershipSyncServiceTest].
 */
class SyncCohortMembershipJobHandlerTest {

    private val objectMapper = ObjectMapper()
    private val sync: CohortMembershipSync = mockk(relaxed = true)
    private val handler = SyncCohortMembershipJobHandler(objectMapper, sync)

    @Test
    fun `deserialises the payload and forwards the triple to the inbound port`() {
        val payload = CohortJobs.SyncCohortMembershipPayload(
            userId = 7L,
            cohortId = 42L,
            intent = SyncCohortMembershipIntent.ADD,
        )

        handler.handle(objectMapper.writeValueAsString(payload), executionId = null)

        verify { sync.sync(userId = 7L, cohortId = 42L, intent = SyncCohortMembershipIntent.ADD) }
    }

    @Test
    fun `forwards REMOVE intent unchanged`() {
        val payload = CohortJobs.SyncCohortMembershipPayload(
            userId = 7L,
            cohortId = 42L,
            intent = SyncCohortMembershipIntent.REMOVE,
        )

        handler.handle(objectMapper.writeValueAsString(payload), executionId = null)

        verify { sync.sync(userId = 7L, cohortId = 42L, intent = SyncCohortMembershipIntent.REMOVE) }
    }
}
