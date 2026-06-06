package net.blueshell.api.platform.integration.cohort.adapter.job

import io.mockk.mockk
import io.mockk.verify
import net.blueshell.api.platform.integration.cohort.port.`in`.CohortTargeting
import net.blueshell.api.shared.enums.TargetSystem
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
        // fromPersisted rejects an unknown string at this transport boundary.
        val payload = """{"system":"BOGUS_SYSTEM","externalTargetId":"ext-1"}"""

        assertThrows<NonRetryableJobException> {
            handler.handle(payload, executionId = null)
        }
    }

    @Test
    fun `parses a valid system and delegates the delete`() {
        val payload = """{"system":"BREVO","externalTargetId":"ext-1"}"""

        handler.handle(payload, executionId = null)

        verify { targeting.deleteTarget(TargetSystem.BREVO, "ext-1") }
    }
}
