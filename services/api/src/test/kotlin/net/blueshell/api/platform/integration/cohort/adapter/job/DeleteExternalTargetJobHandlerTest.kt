package net.blueshell.api.platform.integration.cohort.adapter.job

import io.mockk.mockk
import net.blueshell.api.platform.integration.cohort.port.`in`.CohortTargeting
import net.blueshell.api.shared.job.NonRetryableJobException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import tools.jackson.databind.ObjectMapper

class DeleteExternalTargetJobHandlerTest {

    private val objectMapper = ObjectMapper()
    private val targeting: CohortTargeting = mockk(relaxed = true)
    private val handler = DeleteExternalTargetJobHandler(objectMapper, targeting)

    @Test
    fun `throws NonRetryableJobException for an unrecognised system value`() {
        val payload = """{"system":"BOGUS_SYSTEM","externalTargetId":"ext-1"}"""

        assertThrows<NonRetryableJobException> {
            handler.handle(payload, executionId = null)
        }
    }
}
