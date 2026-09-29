package net.blueshell.api.cohort.domain

import net.blueshell.api.shared.job.JobTrigger
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import tools.jackson.databind.json.JsonMapper

class CohortJobPayloadsTest {
    private val json = JsonMapper.builder().findAndAddModules().build()

    @Test
    fun `payloads keep the keys stored jobs were written with`() {
        val written =
            listOf(
                CohortJobs.CreateCohortTargetPayload(4L),
                CohortJobs.SyncCohortMembershipPayload(1L, 4L, SyncCohortMembershipIntent.ADD),
                CohortJobs.RemoveExternalMemberPayload(4L, "ext"),
                CohortJobs.ReconcileListPayload(4L, JobTrigger.BY_HAND),
                CohortJobs.MaterializeCohortTargetPayload(4L),
            ).map(json::writeValueAsString)

        assertThat(written).allSatisfy { assertThat(it).contains("\"cohortId\":4").doesNotContain("targetId") }
        val inbound = CohortJobs.ApplyInboundReconcilePayload(3L, 4L, "BREVO", "7", "PERIOD_PAYERS:1", emptyList())
        assertThat(json.writeValueAsString(inbound)).contains("\"subjectId\":3", "\"cohortId\":4")
    }

    @Test
    fun `a stored payload reads back onto the target it names`() {
        val stored = """{"subjectId":3,"cohortId":4,"system":"BREVO","externalTargetId":"7","definitionKey":"K","selected":[]}"""

        val read = json.readValue(stored, CohortJobs.ApplyInboundReconcilePayload::class.java)

        assertThat(listOf(read.cohortId, read.targetId)).containsExactly(3L, 4L)
        assertThat(json.readValue("""{"cohortId":4}""", CohortJobs.MaterializeCohortTargetPayload::class.java).targetId).isEqualTo(4L)
        assertThat(json.readValue("""{"cohortId":4,"externalUserId":"x"}""", CohortJobs.RemoveExternalMemberPayload::class.java).targetId)
            .isEqualTo(4L)
        val sync = """{"userId":1,"cohortId":4,"intent":"ADD"}"""
        assertThat(json.readValue(sync, CohortJobs.SyncCohortMembershipPayload::class.java).targetId)
            .isEqualTo(4L)
    }

    @Test
    fun `jobs on one target share a dedup key`() {
        assertThat(CohortJobs.ReconcileList.dedupKey(CohortJobs.ReconcileListPayload(4L))).isEqualTo("cohort=4")
        assertThat(CohortJobs.CreateCohortTarget.dedupKey(CohortJobs.CreateCohortTargetPayload(4L))).isEqualTo("cohort=4")
        assertThat(CohortJobs.MaterializeCohortTarget.dedupKey(CohortJobs.MaterializeCohortTargetPayload(4L))).isEqualTo("cohort=4")
    }
}
